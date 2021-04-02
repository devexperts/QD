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
package com.dxfeed.ipf.test;

import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileField;
import junit.framework.TestCase;

/**
 * Unit test for {@link InstrumentProfileField} class.
 */
public class InstrumentProfileFieldTest extends TestCase {

    public InstrumentProfileFieldTest(String s) {
        super(s);
    }

    public void testFields() {
        for (InstrumentProfileField field : InstrumentProfileField.values()) {
            assertTrue(InstrumentProfileField.find(field.name()) == field);

            InstrumentProfile ip = new InstrumentProfile();
            assertEquals(field.getField(ip), "");
            field.setField(ip, "");
            assertEquals(field.getField(ip), "");

            if (field.isNumericField()) {
                ip = new InstrumentProfile();
                assertTrue(field.getNumericField(ip) == 0);
                field.setField(ip, "");
                assertTrue(field.getNumericField(ip) == 0);
                field.setNumericField(ip, 0);
                assertEquals(field.getField(ip), "");
                assertTrue(field.getNumericField(ip) == 0);
            } else {
                try {
                    field.getNumericField(new InstrumentProfile());
                    fail();
                } catch (Throwable ignored) {}
                try {
                    field.setNumericField(new InstrumentProfile(), 0);
                    fail();
                } catch (Throwable ignored) {}
            }

            try {
                // Check unformatted text storage
                ip = new InstrumentProfile();
                field.setField(ip, "Hello");
                try {
                    assertEquals(field.getField(ip), "Hello");
                } catch (Throwable t) {
                    fail();
                }
            } catch (Throwable ignored) {}

            try {
                // Check plain number storage
                ip = new InstrumentProfile();
                field.setField(ip, "123");
                try {
                    assertEquals(field.getField(ip), "123");
                } catch (Throwable t) {
                    fail();
                }
            } catch (Throwable ignored) {}

            try {
                // Check date storage
                ip = new InstrumentProfile();
                field.setField(ip, "2020-03-08");
                try {
                    assertEquals(field.getField(ip), "2020-03-08");
                } catch (Throwable t) {
                    fail();
                }
            } catch (Throwable ignored) {}

            if (field.isNumericField())
                try {
                    // Check plain number storage
                    ip = new InstrumentProfile();
                    field.setNumericField(ip, 123);
                    try {
                        assertTrue(field.getNumericField(ip) == 123);
                    } catch (Throwable t) {
                        fail();
                    }
                } catch (Throwable ignored) {}
        }
    }
}
