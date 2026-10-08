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

import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.util.InvalidFormatException;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HubConfigTest {

    @Test
    public void testHubConfigNameResolution() {
        HubConfig hub = new HubConfig();
        
        UplinkConfig u1 = new UplinkConfig();
        UplinkConfig u2 = new UplinkConfig();
        u2.setName("up2");
        Map<String, UplinkConfig> uplinks = new HashMap<>();
        uplinks.put("up1", u1);
        uplinks.put("up2", u2);
        hub.setUplinks(uplinks);
        assertEquals("up1", u1.getName());
        assertEquals("up2", u2.getName());

        DownlinkConfig d1 = new DownlinkConfig();
        DownlinkConfig d2 = new DownlinkConfig();
        d2.setName("down2");
        Map<String, DownlinkConfig> downlinks = new HashMap<>();
        downlinks.put("down1", d1);
        downlinks.put("down2", d2);
        hub.setDownlinks(downlinks);
        assertEquals("down1", d1.getName());
        assertEquals("down2", d2.getName());
    }

    @Test(expected = InvalidFormatException.class)
    public void testHubConfigUplinkNameResolutionMismatch() {
        HubConfig hub = new HubConfig();
        UplinkConfig u1 = new UplinkConfig();
        u1.setName("wrongUp");
        hub.setUplinks(Collections.singletonMap("up1", u1));
    }

    @Test(expected = InvalidFormatException.class)
    public void testHubConfigDownlinkNameResolutionMismatch() {
        HubConfig hub = new HubConfig();
        DownlinkConfig d1 = new DownlinkConfig();
        d1.setName("wrongDown");
        hub.setDownlinks(Collections.singletonMap("down1", d1));
    }

    @Test
    public void testHubConfig() {
        HubConfig hub = new HubConfig();
        hub.setName("hub1");
        hub.setDefaultUplinkBindAddr("1.2.3.4");
        hub.setDefaultDownlinkBindAddr("5.6.7.8");
        UniverseConfig universe = new UniverseConfig();
        hub.setUniverseConfig(universe);

        Map<String, UplinkConfig> uplinks = Collections.singletonMap("up1", new UplinkConfig());
        hub.setUplinks(uplinks);

        Map<String, DownlinkConfig> downlinks = Collections.singletonMap("down1", new DownlinkConfig());
        hub.setDownlinks(downlinks);

        assertEquals("hub", hub.getType());
        assertEquals("hub1", hub.getName());
        assertEquals("1.2.3.4", hub.getDefaultUplinkBindAddr());
        assertEquals("5.6.7.8", hub.getDefaultDownlinkBindAddr());
        assertSame(universe, hub.getUniverseConfig());
        assertEquals(uplinks, hub.getUplinks());
        assertEquals(downlinks, hub.getDownlinks());

        HubConfig hub2 = new HubConfig();
        hub2.setName("hub1");
        hub2.setDefaultUplinkBindAddr("1.2.3.4");
        hub2.setDefaultDownlinkBindAddr("5.6.7.8");
        hub2.setUniverseConfig(universe);
        hub2.setUplinks(uplinks);
        hub2.setDownlinks(downlinks);

        assertEquals(hub, hub2);
        assertEquals(hub.hashCode(), hub2.hashCode());

        hub2.setName("hub2");
        assertNotEquals(hub, hub2);

        hub.setType("hub"); // should be ok
        try {
            hub.setType("other");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Test
    public void testDefaultState() {
        HubConfig config = new HubConfig();
        config.setName("hub1");
        UniverseConfig universe = new UniverseConfig();
        config.setUniverseConfig(universe);
        config.setUplinks(Collections.emptyMap());
        config.setDownlinks(Collections.emptyMap());

        assertEquals("hub1", config.getName());
        assertEquals("hub", config.getType());
        assertEquals(MessageConnector.Bindable.ANY_BIND_ADDRESS, config.getDefaultUplinkBindAddr());
        assertEquals(MessageConnector.Bindable.ANY_BIND_ADDRESS, config.getDefaultDownlinkBindAddr());
        assertSame(universe, config.getUniverseConfig());
        assertTrue(config.getUplinks().isEmpty());
        assertTrue(config.getDownlinks().isEmpty());
    }
}
