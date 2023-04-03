/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.io.test;

import com.devexperts.io.Marshalled;
import com.devexperts.io.Marshaller;
import org.junit.Test;

import java.io.IOException;

public class CompactPrimitiveTest {

    @Test
    public void testCompactPrimitive() throws IOException {
        checkReadWrite(null, void.class);
        checkReadWrite(true, boolean.class);
        checkReadWrite((byte) 123, byte.class);
        checkReadWrite('%', char.class);
        checkReadWrite((short) 12345, short.class);
        checkReadWrite(1234567890, int.class);
        checkReadWrite(1234567890123456789L, long.class);
        checkReadWrite(1.2345F, float.class);
        checkReadWrite(1.2345, double.class);
        checkReadWrite("Abc!", String.class);
        checkReadWrite("", String.class);
        checkReadWrite(null, String.class);

        checkReadWrite(null, byte[].class);
        String[][][][][] sss = new String[1][2][3][4][5];
        sss[0][1][2][3][4] = "hi!";
        sss[0][0][1][2][3] = "=)";
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 5; j++)
                sss[0][1][0][i][j] = i + "" + j;
        checkReadWrite(sss, String[][][][][].class);
    }

    private static void checkReadWrite(Object value, Class<?> clazz) throws IOException {
        byte[] bytes = Marshalled.forObject(new Object[] {value}, Marshaller.forClasses(clazz)).getBytes();
        Object result = (Marshalled.forBytes(bytes, Marshaller.forClasses(clazz)).getObject())[0];
        ObjectUtilTest.assertDeepEquals(value, result);
    }
}
