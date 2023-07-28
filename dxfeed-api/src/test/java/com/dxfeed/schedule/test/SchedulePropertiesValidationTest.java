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
package com.dxfeed.schedule.test;

import com.devexperts.io.ByteArrayOutput;
import com.dxfeed.schedule.Schedule;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertNotNull;

/**
 * Test that validates default "schedule.properties".
 */
public class SchedulePropertiesValidationTest {

    /**
     * Errors in properties are ignored by Schedule class for program stability.
     * This test sets the "schedule.properties" as defaults again now throwing exception on errors.
     */
    @Test
    public void testDefaults() throws Exception {
        try (InputStream in = Schedule.class.getResourceAsStream("schedule.properties");
             ByteArrayOutput out = new ByteArrayOutput())
        {
            assertNotNull(in);
            for (int n; (n = in.read(out.getBuffer(), out.getPosition(), out.getLimit() - out.getPosition())) >= 0; ) {
                out.setPosition(out.getPosition() + n);
                out.ensureCapacity(out.getPosition() + 1000);
            }
            Schedule.setDefaults(out.toByteArray());
        }
    }
}
