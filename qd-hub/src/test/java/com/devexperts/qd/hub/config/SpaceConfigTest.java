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

import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SpaceConfigTest {

    @Test
    public void testSpaceConfig() {
        SpaceConfig s1 = new SpaceConfig();
        s1.setName("n1");
        s1.setFilter("f1");
        s1.setActive(true);
        s1.setEnableWildcards(true);
        s1.setEventTimeSequence(true);
        s1.setStoreEverything(true);
        s1.setStoreEverythingFilter("sf1");
        s1.setContracts(EnumSet.of(QDContract.HISTORY));

        SpaceConfig s2 = new SpaceConfig();
        s2.setName("n1");
        s2.setFilter("f1");
        s2.setActive(true);
        s2.setEnableWildcards(true);
        s2.setEventTimeSequence(true);
        s2.setStoreEverything(true);
        s2.setStoreEverythingFilter("sf1");
        s2.setContracts(EnumSet.of(QDContract.HISTORY));

        assertEquals(s1, s2);
        assertEquals(s1.hashCode(), s2.hashCode());

        s2.setName("n2");
        assertNotEquals(s1, s2);
    }

    @Test
    public void testDefaultState() {
        SpaceConfig config = new SpaceConfig();
        assertNull(config.getName());
        assertEquals(QDFilter.ANYTHING.toString(), config.getFilter());
        assertEquals(EnumSet.allOf(QDContract.class), config.getContracts());
        assertTrue(config.isActive());
        assertTrue(config.isEnableWildcards());
        assertFalse(config.isEventTimeSequence());
        assertFalse(config.isStoreEverything());
        assertEquals(QDFilter.ANYTHING.toString(), config.getStoreEverythingFilter());
    }
}
