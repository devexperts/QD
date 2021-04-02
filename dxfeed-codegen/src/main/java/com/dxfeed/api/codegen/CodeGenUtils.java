/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.codegen;

class CodeGenUtils {
    static String emptyToDefault(String value, String defaultValue) {
        return value == null || value.isEmpty() ? defaultValue : value;
    }

    static boolean isPrimitiveAssignable(CodeGenType from, CodeGenType to) {
        assert from.isPrimitive() && to.isPrimitive();
        if (from.equals(to))
            return true;
        int fromScope = getDecimalScope(from);
        int toScope = getDecimalScope(to);
        if (fromScope == 0 || toScope == 0)
            return false;
        return fromScope <= toScope; // widening conversion
    }

    private static int getDecimalScope(CodeGenType type) {
        if (type.isSameType(byte.class))
            return 1;
        if (type.isSameType(short.class))
            return 2;
        if (type.isSameType(int.class))
            return 3;
        if (type.isSameType(long.class))
            return 4;
        if (type.isSameType(float.class))
            return 5;
        if (type.isSameType(double.class))
            return 6;
        return 0;
    }
}
