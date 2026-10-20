/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 HMCL-skip contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version. See the LICENSE file for details.
 */
import java.util.regex.Pattern;

public class RegexCheck {
    public static void main(String[] args) {
        Pattern p = Pattern.compile(args[0]);
        for (int i = 1; i < args.length; i++) {
            System.out.println((p.matcher(args[i]).find() ? "match   " : "no-match") + " " + args[i]);
        }
    }
}
