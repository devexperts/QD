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

import com.devexperts.util.InvalidFormatException;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class UniverseConfigTest {

    @Test
    public void testUniverseConfigOverrides() {
        UniverseConfig universe = new UniverseConfig();
        
        SpaceConfig s1 = new SpaceConfig();
        s1.setName("s1");
        s1.setActive(false);
        s1.setEnableWildcards(false);
        s1.setEventTimeSequence(false);
        s1.setStoreEverything(false);

        SpaceConfig s2 = new SpaceConfig();
        s2.setName("s2");
        s2.setActive(false);
        s2.setEnableWildcards(false);
        s2.setEventTimeSequence(false);
        s2.setStoreEverything(false);

        Map<String, SpaceConfig> spaces = new HashMap<>();
        spaces.put("s1", s1);
        spaces.put("s2", s2);
        universe.setSpaces(spaces);
        
        universe.setActiveSpaces(Collections.singletonList("s1"));
        universe.setEnableWildcardsSpaces(Collections.singletonList("*"));
        universe.setEventTimeSequenceSpaces(Collections.singletonList("s2"));
        universe.setStoreEverythingSpaces(Collections.singletonList("s1"));
        
        universe.applyOverrides();
        
        assertTrue(s1.isActive());
        assertTrue(s1.isEnableWildcards());
        assertFalse(s1.isEventTimeSequence());
        assertTrue(s1.isStoreEverything());

        assertFalse(s2.isActive());
        assertTrue(s2.isEnableWildcards());
        assertTrue(s2.isEventTimeSequence());
        assertFalse(s2.isStoreEverything());
    }

    @Test
    public void testUniverseConfigNameResolution() {
        UniverseConfig universe = new UniverseConfig();
        
        SpaceConfig s1 = new SpaceConfig();
        // name not set
        SpaceConfig s2 = new SpaceConfig();
        s2.setName("space2");

        Map<String, SpaceConfig> spaces = new HashMap<>();
        spaces.put("space1", s1);
        spaces.put("space2", s2);
        
        universe.setSpaces(spaces);
        
        assertEquals("space1", s1.getName());
        assertEquals("space2", s2.getName());
        assertSame(s1, universe.getSpaces().get("space1"));
        assertSame(s2, universe.getSpaces().get("space2"));
    }

    @Test
    public void testUniverseConfig() {
        UniverseConfig universe = new UniverseConfig();
        
        Map<String, SpaceConfig> spaces = Collections.singletonMap("s1", new SpaceConfig());
        universe.setSpaces(spaces);
        
        Map<String, List<ChannelConfig>> products = Collections.singletonMap("p1", Collections.singletonList(ChannelConfig.valueOf("b1")));
        universe.setProducts(products);
        
        List<String> activeSpaces = Collections.singletonList("s1");
        universe.setActiveSpaces(activeSpaces);
        
        List<String> enableWildcardsSpaces = Collections.singletonList("s2");
        universe.setEnableWildcardsSpaces(enableWildcardsSpaces);
        
        List<String> eventTimeSequenceSpaces = Collections.singletonList("s3");
        universe.setEventTimeSequenceSpaces(eventTimeSequenceSpaces);
        
        List<String> storeEverythingSpaces = Collections.singletonList("s4");
        universe.setStoreEverythingSpaces(storeEverythingSpaces);
        
        assertEquals(spaces, universe.getSpaces());
        assertEquals(products, universe.getProducts());
        assertEquals(activeSpaces, universe.getActiveSpaces());
        assertEquals(enableWildcardsSpaces, universe.getEnableWildcardsSpaces());
        assertEquals(eventTimeSequenceSpaces, universe.getEventTimeSequenceSpaces());
        assertEquals(storeEverythingSpaces, universe.getStoreEverythingSpaces());

        UniverseConfig universe2 = new UniverseConfig();
        universe2.setSpaces(spaces);
        universe2.setProducts(products);
        universe2.setActiveSpaces(activeSpaces);
        universe2.setEnableWildcardsSpaces(enableWildcardsSpaces);
        universe2.setEventTimeSequenceSpaces(eventTimeSequenceSpaces);
        universe2.setStoreEverythingSpaces(storeEverythingSpaces);

        assertEquals(universe, universe2);
        assertEquals(universe.hashCode(), universe2.hashCode());

        universe2.setActiveSpaces(Collections.singletonList("s2"));
        assertNotEquals(universe, universe2);
    }

    @Test(expected = InvalidFormatException.class)
    public void testUniverseConfigNameResolutionMismatch() {
        UniverseConfig universe = new UniverseConfig();
        SpaceConfig s1 = new SpaceConfig();
        s1.setName("wrongName");
        universe.setSpaces(Collections.singletonMap("space1", s1));
    }

    @Test
    public void testDefaultState() {
        UniverseConfig config = new UniverseConfig();
        assertTrue(config.getSpaces().isEmpty());
        assertTrue(config.getProducts().isEmpty());
        assertTrue(config.getActiveSpaces().isEmpty());
        assertTrue(config.getEnableWildcardsSpaces().isEmpty());
        assertTrue(config.getEventTimeSequenceSpaces().isEmpty());
        assertTrue(config.getStoreEverythingSpaces().isEmpty());
    }
}
