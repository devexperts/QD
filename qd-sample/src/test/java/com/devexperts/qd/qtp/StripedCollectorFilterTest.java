/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.kit.CompositeFilters;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StripedCollectorFilterTest {

    private static final DataScheme SCHEME = QDFactory.getDefaultScheme();
    public static final DataRecord QUOTE = SCHEME.findRecordByName("Quote");
    public static final DataRecord TRADE = SCHEME.findRecordByName("Trade");

    @Test
    public void testFactoryWithNoStriping() {
        QDTicker ticker = (QDTicker) QDFactory.getDefaultFactory().collectorBuilder(QDContract.TICKER).build();

        QDFilter bsFilter = CompositeFilters.valueOf("bs", SCHEME);
        AgentAdapter.Factory agentFactory = new AgentAdapter.Factory(ticker, null, null, bsFilter);
        agentFactory.setFilter(":Quote");

        QDFilter filter = agentFactory.getFilter();
        assertTrue(filter.accept(QDContract.TICKER, QUOTE, 0, "IBM"));
        assertFalse("Factory filter", filter.accept(QDContract.TICKER, TRADE, 0, "IBM"));
        assertFalse("Initial filter", filter.accept(QDContract.TICKER, QUOTE, 0, "/ESH7"));
    }

    @Test
    public void testFactoryStriping() {
        QDTicker ticker = (QDTicker) QDFactory.getDefaultFactory().collectorBuilder(QDContract.TICKER).build();

        QDFilter bsFilter = CompositeFilters.valueOf("bs", SCHEME);
        AgentAdapter.Factory agentFactory = new AgentAdapter.Factory(ticker, null, null, bsFilter);

        // Multiple setters should not affect result filters
        agentFactory.setFilter(":Trade");
        agentFactory.setStripeFilter("hash0of128");
        agentFactory.setConfiguration(MessageConnectors.FILTER_CONFIGURATION_KEY, "!*");
        agentFactory.setConfiguration(MessageConnectors.STRIPE_FILTER_CONFIGURATION_KEY, "hash1of128");

        // Final settings should be used
        agentFactory.setFilter(":Quote");
        agentFactory.setStripeFilter("range-A-M-");

        assertEquals("range-A-M-", agentFactory.getStripeFilter());

        QDFilter filter = agentFactory.getFilter();
        assertTrue(filter.accept(QDContract.TICKER, QUOTE, 0, "IBM"));
        assertFalse("Factory filter", filter.accept(QDContract.TICKER, TRADE, 0, "IBM"));
        assertFalse("Stripe filter", filter.accept(QDContract.TICKER, QUOTE, 0, "ZZZ"));
        assertFalse("Initial filter", filter.accept(QDContract.TICKER, QUOTE, 0, "/ESH7"));
    }
}
