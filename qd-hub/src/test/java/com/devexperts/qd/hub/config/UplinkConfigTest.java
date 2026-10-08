/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.hub.config;

import com.devexperts.qd.QDFilter;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UplinkConfigTest {

    @Test
    public void testUplinkConfig() {
        UplinkConfig up = new UplinkConfig();
        up.setName("u1");
        up.setAddress("addr1");
        up.setFilter("f1");
        List<String> upSpaces = Collections.singletonList("s1");
        up.setSpaces(upSpaces);

        assertEquals("u1", up.getName());
        assertEquals("addr1", up.getAddress());
        assertEquals("f1", up.getFilter());
        assertEquals(upSpaces, up.getSpaces());

        UplinkConfig up2 = new UplinkConfig();
        up2.setName("u1");
        up2.setAddress("addr1");
        up2.setFilter("f1");
        up2.setSpaces(upSpaces);

        assertEquals(up, up2);
        assertEquals(up.hashCode(), up2.hashCode());

        up2.setName("u2");
        assertNotEquals(up, up2);
    }

    @Test
    public void testDefaultState() {
        UplinkConfig config = new UplinkConfig();
        config.setAddress("localhost:1234");
        assertEquals("localhost:1234", config.getAddress());
        assertEquals(QDFilter.ANYTHING.toString(), config.getFilter());
        assertNull(config.getName());
        assertTrue(config.getSpaces().isEmpty());
    }
}
