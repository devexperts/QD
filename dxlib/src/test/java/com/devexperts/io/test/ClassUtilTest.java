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
package com.devexperts.io.test;

import com.devexperts.io.ClassUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ClassUtilTest {
    @Test
    public void testTypeClasses() throws ClassNotFoundException {
        assertEquals(void.class, ClassUtil.getTypeClass("void", null));
        assertEquals(int.class, ClassUtil.getTypeClass("int", null));
        assertEquals(String.class, ClassUtil.getTypeClass("java.lang.String", null));
        assertEquals(Object.class, ClassUtil.getTypeClass("java.lang.Object", null));

        assertEquals(int[].class, ClassUtil.getTypeClass("int[]", null));
        assertEquals(String[].class, ClassUtil.getTypeClass("java.lang.String[]", null));
        assertEquals(Object[].class, ClassUtil.getTypeClass("java.lang.Object[]", null));

        assertEquals(int[][].class, ClassUtil.getTypeClass("int[][]", null));
        assertEquals(String[][].class, ClassUtil.getTypeClass("java.lang.String[][]", null));
        assertEquals(Object[][].class, ClassUtil.getTypeClass("java.lang.Object[][]", null));

        assertEquals(int[].class, ClassUtil.getTypeClass("[I", null));
        assertEquals(String[].class, ClassUtil.getTypeClass("[Ljava.lang.String;", null));
        assertEquals(Object[].class, ClassUtil.getTypeClass("[Ljava.lang.Object;", null));

        assertEquals(int[][].class, ClassUtil.getTypeClass("[[I", null));
        assertEquals(String[][].class, ClassUtil.getTypeClass("[[Ljava.lang.String;", null));
        assertEquals(Object[][].class, ClassUtil.getTypeClass("[[Ljava.lang.Object;", null));
    }
}
