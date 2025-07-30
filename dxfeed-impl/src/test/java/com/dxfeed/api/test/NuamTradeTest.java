/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */

package com.dxfeed.api.test;

import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.custom.NuamTrade;
import com.dxfeed.event.market.Direction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class NuamTradeTest {
    private static final String SYMBOL = "TEST";
    private static final long TIME = 1753453151300L;
    private static final int DAYS_SINCE_EPOCH = 20294;

    private DXEndpoint endpoint;
    private DXPublisher publisher;

    private final BlockingQueue<NuamTrade> receivedEvents = new ArrayBlockingQueue<>(10);

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        DXFeed feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        DXFeedSubscription<NuamTrade> sub = feed.createSubscription(NuamTrade.class);
        sub.addEventListener(receivedEvents::addAll);
        sub.addSymbols(SYMBOL);
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @Test
    public void testNuamTrade() throws InterruptedException {
        NuamTrade trade = createNuamTrade();
        publisher.publishEvents(Collections.singleton(trade));
        assertEvent(trade, receivedEvents.take());
    }

    private static NuamTrade createNuamTrade() {
        NuamTrade trade = new NuamTrade(SYMBOL);
        trade.setTime(TIME);
        trade.setSequence(2);
        trade.setExchangeCode('A');
        trade.setPrice(3.0);
        trade.setSizeAsDouble(4.0);
        trade.setDayId(DAYS_SINCE_EPOCH);
        trade.setDayVolumeAsDouble(5.0);
        trade.setDayTurnover(6.0);
        trade.setTickDirection(Direction.UP);
        trade.setChange(7.0);

        trade.setTradeStatTime(TIME);
        trade.setLastSignificantPrice(101.0);
        trade.setLastPriceForAll(102.0);
        trade.setNumberOfTrades(103);
        trade.setVWAP(104.0);
        return trade;
    }

    private void assertEvent(NuamTrade expected, NuamTrade actual) {
        assertNotNull(actual);
        assertEquals(expected.getEventSymbol(), actual.getEventSymbol());
        assertEquals(TIME, actual.getTime());
        assertEquals(expected.getSequence(), actual.getSequence());
        assertEquals(expected.getExchangeCode(), actual.getExchangeCode());
        assertEquals(expected.getPrice(), actual.getPrice(), 0.0);
        assertEquals(expected.getSizeAsDouble(), actual.getSizeAsDouble(), 0.0);
        assertEquals(expected.getDayId(), actual.getDayId());
        assertEquals(expected.getDayVolumeAsDouble(), actual.getDayVolumeAsDouble(), 0.0);
        assertEquals(expected.getDayTurnover(), actual.getDayTurnover(), 0.0);
        assertEquals(expected.getTickDirection(), actual.getTickDirection());
        assertEquals(expected.getChange(), actual.getChange(), 0.0);

        assertEquals(expected.getTradeStatTime(), actual.getTradeStatTime());
        assertEquals(expected.getLastSignificantPrice(), actual.getLastSignificantPrice(), 0.0);
        assertEquals(expected.getLastPriceForAll(), actual.getLastPriceForAll(), 0.0);
        assertEquals(expected.getNumberOfTrades(), actual.getNumberOfTrades());
        assertEquals(expected.getVWAP(), actual.getVWAP(), 0.0);
    }

    @Test
    public void testEmptyNuamTrade() {
        NuamTrade trade = new NuamTrade();
        assertNull(trade.getEventSymbol());
        assertEquals(0, trade.getTime());
        assertEquals(0, trade.getSequence());
        assertEquals(0, trade.getExchangeCode());
        assertEquals(Double.NaN, trade.getPrice(), 0.0);
        assertEquals(Double.NaN, trade.getSizeAsDouble(), 0.0);
        assertEquals(0, trade.getDayId());
        assertEquals(Double.NaN, trade.getDayVolumeAsDouble(), 0.0);
        assertEquals(Double.NaN, trade.getDayTurnover(), 0.0);
        assertEquals(Direction.UNDEFINED, trade.getTickDirection());
        assertEquals(Double.NaN, trade.getChange(), 0.0);

        assertEquals(0, trade.getTradeStatTime());
        assertEquals(Double.NaN, trade.getLastSignificantPrice(), 0.0);
        assertEquals(Double.NaN, trade.getLastPriceForAll(), 0.0);
        assertEquals(0, trade.getNumberOfTrades());
        assertEquals(Double.NaN, trade.getVWAP(), 0.0);
    }
}
