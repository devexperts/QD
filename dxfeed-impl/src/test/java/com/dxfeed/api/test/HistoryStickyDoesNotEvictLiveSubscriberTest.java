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
package com.dxfeed.api.test;

import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.util.TimePeriod;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedTimeSeriesSubscription;
import com.dxfeed.event.market.TimeAndSale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class HistoryStickyDoesNotEvictLiveSubscriberTest {

    private static final long STICKY_PERIOD_MS = 100;

    private DXEndpoint endpoint;

    @Before
    public void setUp() {
        endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.LOCAL_HUB)
            .withProperty(QDEndpoint.DXFEED_STICKY_SUBSCRIPTION_PROPERTY,
                TimePeriod.valueOf(STICKY_PERIOD_MS).toString())
            .build();
    }

    @After
    public void tearDown() {
        endpoint.close();
    }

    @Test
    public void stickyCleanupDoesNotCorruptChainWithMaxTimeSubscriber() throws InterruptedException {
        // Step 1: subscribe with a normal fromTime, then unsubscribe → sticky activates for "A".
        DXFeedTimeSeriesSubscription<TimeAndSale> subA =
            endpoint.getFeed().createTimeSeriesSubscription(TimeAndSale.class);
        subA.setFromTime(0);
        subA.addSymbols("A");
        subA.setSymbols(); // remove all → last subscriber gone, sticky period begins
        subA.close();

        // Step 2: subscribe WITHOUT setFromTime → fromTime stays at Long.MAX_VALUE.
        DXFeedTimeSeriesSubscription<TimeAndSale> subB =
            endpoint.getFeed().createTimeSeriesSubscription(TimeAndSale.class);
        subB.addSymbols("A"); // QD agent enters the History chain with TIME_SUB = Long.MAX_VALUE

        // Step 3: wait for the sticky period to expire.
        Thread.sleep(STICKY_PERIOD_MS * 5);

        // Step 4: trigger the sticky cleanup callback
        DXFeedTimeSeriesSubscription<TimeAndSale> kick =
            endpoint.getFeed().createTimeSeriesSubscription(TimeAndSale.class);
        kick.close();

        // Step 5: force a total-sub rehash by subscribing to many new symbols.
        DXFeedTimeSeriesSubscription<TimeAndSale> grower =
            endpoint.getFeed().createTimeSeriesSubscription(TimeAndSale.class);
        grower.setFromTime(0);
        Set<String> symbols = IntStream.range(0, 64)
            .mapToObj(i -> "A" + i)
            .collect(Collectors.toSet());
        grower.addSymbols(symbols);

        // Step 6: close B — must complete without throwing IllegalStateException.
        subB.close();
    }
}
