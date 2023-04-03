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
package com.devexperts.qd.test;

import com.devexperts.qd.SerialFieldType;
import org.junit.Test;

import java.lang.reflect.Field;

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SerialFieldTypeTest {

    @Test
    public void testIsDecimal() throws IllegalAccessException {
        for (Field field : SerialFieldType.class.getFields()) {
            int mod = field.getModifiers();
            if (isPublic(mod) && isStatic(mod) && isFinal(mod) &&
                field.getDeclaringClass().equals(SerialFieldType.class))
            {
                String fieldName = field.getName();
                SerialFieldType fieldType = (SerialFieldType) field.get(SerialFieldType.class);
                if (field.getName().equals("DECIMAL")) {
                    assertTrue(fieldName + " should be decimal", fieldType.isDecimal());
                } else {
                    assertFalse(fieldName + " should be non-decimal", fieldType.isDecimal());
                }
            }
        }
    }
}
