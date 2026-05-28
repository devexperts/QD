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
package com.devexperts.qd.qtp;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.util.TimePeriod;
import org.junit.Test;

import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * Verifies that the copy-constructor transfers every property declared on the source shaper.
 * Every new property added to {@link ChannelShaper} extends the copy-constructor
 * — no migration-site sweep required.
 */
public class ChannelShaperCopyTest {

    private static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE);
    private static final Executor EXECUTOR = Runnable::run;

    @Test
    public void testEveryPropertyTransfersToCopy() {
        ChannelShaper source = new ChannelShaper(QDContract.TICKER, EXECUTOR, true);
        source.setAggregationPeriod(2500);
        source.setDefaultAggregationPeriod(TimePeriod.valueOf("3s"));
        source.setMinAggregationPeriod(TimePeriod.valueOf("1s"));
        source.setMaxAggregationPeriod(TimePeriod.valueOf("10s"));
        source.setWeight(42);
        source.setSubscriptionFilter(QDFilter.ANYTHING);

        ChannelShaper copy = new ChannelShaper(source);

        assertEquals(source.getContract(), copy.getContract());
        assertEquals(source.isKeepRejected(), copy.isKeepRejected());
        assertSame(source.getSubscriptionExecutor(), copy.getSubscriptionExecutor());
        assertEquals(source.getAggregationPeriod(), copy.getAggregationPeriod());
        assertEquals(source.getDefaultAggregationPeriod(), copy.getDefaultAggregationPeriod());
        assertEquals(source.getMinAggregationPeriod(), copy.getMinAggregationPeriod());
        assertEquals(source.getMaxAggregationPeriod(), copy.getMaxAggregationPeriod());
        assertEquals(source.getWeight(), copy.getWeight());
        assertSame(source.getSubscriptionFilter(), copy.getSubscriptionFilter());
    }

    @Test
    public void testUnsetBoundsRemainUnsetOnCopy() {
        ChannelShaper source = new ChannelShaper(QDContract.STREAM, EXECUTOR);
        ChannelShaper copy = new ChannelShaper(source);

        assertNull(copy.getDefaultAggregationPeriod());
        assertEquals(source.getMinAggregationPeriod(), copy.getMinAggregationPeriod());
        assertEquals(source.getMaxAggregationPeriod(), copy.getMaxAggregationPeriod());
        assertEquals(source.getAggregationPeriod(), copy.getAggregationPeriod());
    }

    @Test
    public void testDynamicChannelShaperCopyPreservesFilter() {
        QDFilter filter = new QDFilter(SCHEME) {
            @Override
            public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
                return false;
            }
        };
        DynamicChannelShaper source = new DynamicChannelShaper(QDContract.TICKER, EXECUTOR, filter);
        source.setMinAggregationPeriod(TimePeriod.valueOf("2s"));
        source.setMaxAggregationPeriod(TimePeriod.valueOf("8s"));

        DynamicChannelShaper copy = new DynamicChannelShaper(source);

        assertEquals(QDContract.TICKER, copy.getContract());
        assertSame(filter, copy.getSubscriptionFilter());
        assertEquals(TimePeriod.valueOf("2s"), copy.getMinAggregationPeriod());
        assertEquals(TimePeriod.valueOf("8s"), copy.getMaxAggregationPeriod());
    }
}
