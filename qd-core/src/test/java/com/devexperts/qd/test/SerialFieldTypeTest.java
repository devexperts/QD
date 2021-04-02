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
package com.devexperts.qd.test;

import com.devexperts.qd.SerialFieldType;
import junit.framework.TestCase;

import java.lang.reflect.Field;

import static java.lang.reflect.Modifier.isFinal;
import static java.lang.reflect.Modifier.isPublic;
import static java.lang.reflect.Modifier.isStatic;

public class SerialFieldTypeTest extends TestCase {

    public void testIsDecimal() throws IllegalAccessException {
        for (Field field : SerialFieldType.class.getFields()) {
            int mod = field.getModifiers();
            if (isPublic(mod) && isStatic(mod) && isFinal(mod) && field.getDeclaringClass().equals(SerialFieldType.class)) {
                String fieldName = field.getName();
                SerialFieldType fieldType = (SerialFieldType) field.get(SerialFieldType.class);
                switch (field.getName()) {
                case "DECIMAL":
                    assertTrue(fieldName + " should be decimal", fieldType.isDecimal());
                    break;
                default:
                    assertFalse(fieldName + " should be non-decimal", fieldType.isDecimal());
                }
            }
        }
    }

}
