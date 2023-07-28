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
package com.devexperts.util.test;

import com.devexperts.util.TimeUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TimeUtilTest {

    @Test
    public void testSimpleZones() {
        assertEquals("GMT", TimeUtil.getTimeZoneGmt().getID());
        assertEquals("UTC", TimeUtil.getTimeZone("UTC").getID());
        assertEquals("PST", TimeUtil.getTimeZone("PST").getID());
        assertEquals("EST", TimeUtil.getTimeZone("EST").getID());

        assertEquals("America/Chicago", TimeUtil.getTimeZone("America/Chicago").getID());
        assertEquals("America/New_York", TimeUtil.getTimeZone("America/New_York").getID());
    }

    @Test
    public void testOffsetZones() {
        assertEquals("GMT+00:00", TimeUtil.getTimeZone("GMT+00:00").getID());
        assertEquals("GMT-00:00", TimeUtil.getTimeZone("GMT-00:00").getID());
        assertEquals("GMT+06:00", TimeUtil.getTimeZone("GMT+06:00").getID());
        assertEquals("GMT-06:00", TimeUtil.getTimeZone("GMT-06:00").getID());
    }

    @Test
    public void testInvalidZones() {
        assertThrows(IllegalArgumentException.class, () -> TimeUtil.getTimeZone("Atlantis/Underwater_Town"));
        assertThrows(IllegalArgumentException.class, () -> TimeUtil.getTimeZone("GMT+6:00"));
        assertThrows(IllegalArgumentException.class, () -> TimeUtil.getTimeZone("Z"));
    }
}
