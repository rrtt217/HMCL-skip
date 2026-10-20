/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 HMCL-skip contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version. See the LICENSE file for details.
 */
public class RegionProbe {
    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("org.jackhuang.hmcl.util.RegionUtils");
        long t0 = System.nanoTime();
        Object machine = c.getMethod("isIsChinaMainlandMachine").invoke(null);
        Object network = c.getMethod("isIsChinaMainlandNetwork").invoke(null);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.println("machine=" + machine + " network=" + network + " elapsedMs=" + ms);
    }
}
