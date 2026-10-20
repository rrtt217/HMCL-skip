/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 HMCL-skip contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version. See the LICENSE file for details.
 */
/// 在给定 -cp（含 HMCL jar 与 openjfx jars）下，强制链接+校验若干类。
public class LoadClasses {
    public static void main(String[] args) throws Exception {
        for (String n : args) {
            Class<?> c = Class.forName(n, false, ClassLoader.getSystemClassLoader());
            c.getDeclaredMethods();
            System.out.println("ok   " + n);
        }
    }
}
