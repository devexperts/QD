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
import com.devexperts.util.TimePeriod;
import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

public class ChannelConfigTest {

    @Test
    public void testChannelConfig() {
        ChannelConfig c1 = new ChannelConfig();
        c1.setBase("base1");
        c1.setFilter("filter1");
        c1.setAllowWildcards(true);
        c1.setAggregationPeriod(TimePeriod.valueOf("1s"));
        c1.setContracts(EnumSet.of(QDContract.TICKER));

        ChannelConfig c2 = new ChannelConfig();
        c2.setBase("base1");
        c2.setFilter("filter1");
        c2.setAllowWildcards(true);
        c2.setAggregationPeriod(TimePeriod.valueOf("1s"));
        c2.setContracts(EnumSet.of(QDContract.TICKER));

        assertEquals(c1, c2);
        assertEquals(c1.hashCode(), c2.hashCode());
        
        c2.setBase("base2");
        assertNotEquals(c1, c2);

        ChannelConfig c3 = ChannelConfig.valueOf("base3");
        assertEquals("base3", c3.getBase());
    }

    @Test
    public void testDefaultState() {
        ChannelConfig config = new ChannelConfig();
        config.setBase("base1");
        assertEquals("base1", config.getBase());
        assertEquals(QDFilter.ANYTHING.toString(), config.getFilter());
        assertEquals(EnumSet.allOf(QDContract.class), config.getContracts());
        assertEquals(TimePeriod.ZERO, config.getAggregationPeriod());
        assertFalse(config.isAllowWildcards());
    }
}
