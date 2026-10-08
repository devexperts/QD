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

public class DownlinkConfigTest {

    @Test
    public void testDownlinkConfig() {
        DownlinkConfig down = new DownlinkConfig();
        down.setName("d1");
        down.setAddress("addr2");
        List<String> downSpaces = Collections.singletonList("s2");
        down.setSpaces(downSpaces);
        List<ChannelConfig> products = Collections.singletonList(ChannelConfig.valueOf("p1"));
        down.setProducts(products);

        assertEquals("d1", down.getName());
        assertEquals("addr2", down.getAddress());
        assertEquals(downSpaces, down.getSpaces());
        assertEquals(products, down.getProducts());

        DownlinkConfig down2 = new DownlinkConfig();
        down2.setName("d1");
        down2.setAddress("addr2");
        down2.setSpaces(downSpaces);
        down2.setProducts(products);

        assertEquals(down, down2);
        assertEquals(down.hashCode(), down2.hashCode());

        down2.setName("d2");
        assertNotEquals(down, down2);
    }

    @Test
    public void testDefaultState() {
        DownlinkConfig config = new DownlinkConfig();
        config.setAddress("localhost:1234");
        assertEquals("localhost:1234", config.getAddress());
        assertEquals(QDFilter.ANYTHING.toString(), config.getFilter());
        assertNull(config.getName());
        assertTrue(config.getSpaces().isEmpty());
        assertTrue(config.getProducts().isEmpty());
    }
}
