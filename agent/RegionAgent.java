/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 HMCL-skip contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version. See the LICENSE file for details.
 *
 * RegionAgent —— 运行期把 HMCL 的「中国大陆」判定改成 false 的 Java agent。
 *
 * 不修改 jar，因此 HMCL 的自签名校验照常通过（微软登录、自更新不受影响）。
 *
 * 用法：
 *   java -javaagent:region-agent.jar -jar HMCL.jar
 *   java -javaagent:region-agent.jar=machine -jar HMCL.jar
 *   java -javaagent:region-agent.jar=callsites=^org/jackhuang/hmcl/download/HMCLDownloadProvider$ -jar HMCL.jar
 *
 * 参数（用 ';' 分隔）：
 *   machine                 连 isIsChinaMainlandMachine() 一起改（默认只改 network）
 *   callsites               改为「调用点级」：把 invokestatic 换成 iconst_0，方法体不动
 *   callsites=<regex>       同上，但只处理类名匹配的类（internal name，如 org/jackhuang/...）
 *   dump=<dir>              把改写后的 class 落到目录，便于 javap 核对
 */

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class RegionAgent {

    private static final String OWNER = "org/jackhuang/hmcl/util/RegionUtils";
    private static final String NETWORK = "isIsChinaMainlandNetwork";
    private static final String MACHINE = "isIsChinaMainlandMachine";
    private static final String DESC = "()Z";

    private RegionAgent() {
    }

    public static void premain(String args, Instrumentation inst) {
        Options options = Options.parse(args);

        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if (className == null) {
                    return null;
                }

                byte[] patched;
                if (options.callSites) {
                    // 调用点级：RegionUtils 本身保持原样，只改调用方
                    if (OWNER.equals(className) || !options.matches(className)) {
                        return null;
                    }
                    patched = patchCallSites(classfileBuffer, options.machine);
                } else {
                    if (!OWNER.equals(className)) {
                        return null;
                    }
                    patched = patchMethods(classfileBuffer, options.machine);
                }

                if (patched != null) {
                    System.err.println("[region-agent] transformed " + className);
                    if (options.dump != null) {
                        dump(options.dump, className, patched);
                    }
                }
                return patched;
            }
        });
    }

    // ------------------------------------------------------------ 方法级

    private static byte[] patchMethods(byte[] bytes, boolean patchMachine) {
        ClassNode cn = parse(bytes);
        boolean changed = false;

        for (MethodNode mn : cn.methods) {
            if (!DESC.equals(mn.desc)) {
                continue;
            }
            if (NETWORK.equals(mn.name) || (patchMachine && MACHINE.equals(mn.name))) {
                mn.instructions.clear();
                mn.tryCatchBlocks.clear();
                mn.localVariables = null;
                mn.visibleLocalVariableAnnotations = null;
                mn.invisibleLocalVariableAnnotations = null;
                mn.instructions.add(new InsnNode(Opcodes.ICONST_0));
                mn.instructions.add(new InsnNode(Opcodes.IRETURN));
                mn.maxStack = 1;
                mn.maxLocals = 0;
                changed = true;
            }
        }

        return changed ? write(cn) : null;
    }

    // ------------------------------------------------------------ 调用点级

    private static byte[] patchCallSites(byte[] bytes, boolean patchMachine) {
        ClassNode cn = parse(bytes);
        boolean changed = false;

        for (MethodNode mn : cn.methods) {
            // 先取 next 再替换：InsnList.set() 会把旧节点的链接清空
            for (AbstractInsnNode p = mn.instructions.getFirst(); p != null; ) {
                AbstractInsnNode next = p.getNext();
                if (p instanceof MethodInsnNode mi
                        && p.getOpcode() == Opcodes.INVOKESTATIC
                        && OWNER.equals(mi.owner)
                        && DESC.equals(mi.desc)
                        && (NETWORK.equals(mi.name) || (patchMachine && MACHINE.equals(mi.name)))) {
                    // invokestatic 与 iconst_0 的栈效果一致：弹 0 个、压 1 个 int
                    mn.instructions.set(p, new InsnNode(Opcodes.ICONST_0));
                    changed = true;
                }
                p = next;
            }
        }

        return changed ? write(cn) : null;
    }

    // ------------------------------------------------------------ 工具

    private static ClassNode parse(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        return cn;
    }

    private static byte[] write(ClassNode cn) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    private static void dump(Path dir, String className, byte[] bytes) {
        try {
            Path file = dir.resolve(className + ".class");
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            System.err.println("[region-agent] failed to dump " + className + ": " + e);
        }
    }

    private static final class Options {
        boolean machine;
        boolean callSites;
        Path dump;
        final List<Pattern> filters = new ArrayList<>();

        static Options parse(String args) {
            Options o = new Options();
            if (args == null || args.isBlank()) {
                return o;
            }
            for (String token : args.split(";")) {
                token = token.trim();
                if (token.isEmpty()) {
                    continue;
                }
                if ("machine".equals(token)) {
                    o.machine = true;
                } else if ("callsites".equals(token)) {
                    o.callSites = true;
                } else if (token.startsWith("callsites=")) {
                    o.callSites = true;
                    o.filters.add(Pattern.compile(token.substring("callsites=".length())));
                } else if (token.startsWith("dump=")) {
                    o.dump = Path.of(token.substring("dump=".length()));
                } else {
                    System.err.println("[region-agent] unknown option: " + token);
                }
            }
            return o;
        }

        boolean matches(String className) {
            if (filters.isEmpty()) {
                return true;
            }
            for (Pattern p : filters) {
                if (p.matcher(className).find()) {
                    return true;
                }
            }
            return false;
        }
    }
}
