/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import com.devexperts.test.TraceRunnerWithParametersFactory;
import com.devexperts.util.TimePeriod;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.EventType;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.market.AnalyticOrder;
import com.dxfeed.event.market.OtcMarketsOrder;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Side;
import com.dxfeed.event.market.SpreadOrder;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.event.market.Trade;
import com.dxfeed.event.market.TradeETH;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToLongFunction;

import static org.junit.Assert.assertTrue;

@SuppressWarnings("unchecked")
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TraceRunnerWithParametersFactory.class)
public class TimeFieldReplacerTest {
    private static final Path FILE_WRITE_TO = Paths.get("TimeFieldReplacerTest.qds.tmp");

    private static final long INITIAL_TIME_MILLIS = 123_000;
    private static final long STRATEGY_TIME_MILLIS = 234_000;

    private final boolean replace;
    private final StrategyType strategyType;
    private final String fileFormat;

    private long testStartTime;

    public TimeFieldReplacerTest(boolean replace, StrategyType strategyType, String fileFormat) {
        this.replace = replace;
        this.strategyType = strategyType;
        this.fileFormat = fileFormat;
    }

    @Parameterized.Parameters(name = "replace={0}, strategy={1}, fileFormat={2}")
    public static Iterable<Object[]> parameters() {
        ArrayList<Object[]> parameters = new ArrayList<>();
        boolean[] replaceParameters = {true, false};
        String[] fileFormatParameters = {"text", "binary"};
        for (boolean replace : replaceParameters) {
            for (StrategyType strategyType : StrategyType.values()) {
                for (String fileFormat : fileFormatParameters) {
                    parameters.add(new Object[] {replace, strategyType, fileFormat});
                }
            }
        }
        return parameters;
    }

    @After
    public void tearDown() {
        //noinspection ResultOfMethodCallIgnored
        FILE_WRITE_TO.toFile().delete();
    }

    private <T extends EventType<?>> void testEvent(T initialEvent, ToLongFunction<T>... timeExtractors)
        throws InterruptedException
    {
        // 0. Remember when test was started, truncated to whole seconds
        testStartTime = System.currentTimeMillis() / 1000 * 1000;

        // 1. Create endpoint with PUBLISHER role and connect to tape file
        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.PUBLISHER)
            .withProperty(DXEndpoint.DXFEED_WILDCARD_ENABLE_PROPERTY, "true")
            .withProperty("dxscheme.enabled.ActionTime", "*")
            .build();
        endpoint.connect("tape:" + FILE_WRITE_TO + "[format=" + fileFormat + "]");

        // 2. Publish event and close endpoint
        endpoint.getPublisher().publishEvents(Collections.singletonList(initialEvent));
        endpoint.awaitProcessed();
        endpoint.closeAndAwaitTermination();

        // 3. Read published events
        endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.STREAM_FEED)
            .withProperty("dxscheme.enabled.ActionTime", "*")
            .build();
        DXFeedSubscription<T> sub = endpoint.getFeed().createSubscription((Class<T>) initialEvent.getClass());
        List<T> events = new ArrayList<>();
        sub.addEventListener(events::addAll);
        sub.addSymbols(initialEvent.getEventSymbol());
        String fieldReplacerConfig = replace ? "[fieldReplacer=time:*:"
            + String.format(strategyType.config, TimePeriod.valueOf(STRATEGY_TIME_MILLIS).toString()) + "]" : "";
        endpoint.connect("file:" + FILE_WRITE_TO + fieldReplacerConfig);
        endpoint.awaitNotConnected();
        endpoint.closeAndAwaitTermination();

        // 4. Check that events are changed as required
        for (T modifiedEvent : events) {
            for (ToLongFunction<T> timeExtractor : timeExtractors) {
                assertTrue(initialEvent + " --> " + modifiedEvent, checkTime(timeExtractor.applyAsLong(modifiedEvent)));
            }
        }
    }

    @Test
    public void testTimeAndSale() throws Exception {
        TimeAndSale initialEvent = new TimeAndSale("IBM");
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, TimeAndSale::getTime);
    }

    @Test
    public void testTrade() throws Exception {
        Trade initialEvent = new Trade("IBM");
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, Trade::getTime);
    }

    @Test
    public void testTradeETH() throws Exception {
        TradeETH initialEvent = new TradeETH("IBM");
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, TradeETH::getTime);
    }

    @Test
    public void testOrder() throws Exception {
        Order initialEvent = new Order("IBM");
        initialEvent.setOrderSide(Side.BUY);
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        initialEvent.setActionTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, Order::getTime, Order::getActionTime);
    }

    @Test
    public void testAnalyticOrder() throws Exception {
        AnalyticOrder initialEvent = new AnalyticOrder("IBM");
        initialEvent.setOrderSide(Side.BUY);
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        initialEvent.setActionTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, AnalyticOrder::getTime, AnalyticOrder::getActionTime);
    }

    @Test
    public void testOtcMarketsOrder() throws Exception {
        OtcMarketsOrder initialEvent = new OtcMarketsOrder("IBM");
        initialEvent.setOrderSide(Side.BUY);
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        initialEvent.setActionTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, OtcMarketsOrder::getTime, OtcMarketsOrder::getActionTime);
    }

    @Test
    public void testSpreadOrder() throws Exception {
        SpreadOrder initialEvent = new SpreadOrder("IBM");
        initialEvent.setOrderSide(Side.BUY);
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        initialEvent.setActionTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, SpreadOrder::getTime, SpreadOrder::getActionTime);
    }

    @Test
    public void testCandle() throws Exception {
        Candle initialEvent = new Candle(CandleSymbol.valueOf("IBM"));
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, Candle::getTime);
    }

    @Test
    public void testQuote() throws Exception {
        Quote initialEvent = new Quote("IBM");
        initialEvent.setBidTime(INITIAL_TIME_MILLIS);
        initialEvent.setAskTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, Quote::getBidTime, Quote::getAskTime);
    }

    @Test
    public void testProfile() throws Exception {
        Profile initialEvent = new Profile("IBM");
        initialEvent.setHaltStartTime(INITIAL_TIME_MILLIS);
        initialEvent.setHaltEndTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, Profile::getHaltStartTime, Profile::getHaltEndTime);
    }

    private boolean checkTime(long time) {
        if (replace) {
            switch (strategyType) {
            case CURRENT:
                return time >= testStartTime && time <= System.currentTimeMillis();
            case INCREASE:
                return time == INITIAL_TIME_MILLIS + STRATEGY_TIME_MILLIS;
            case DECREASE:
                return time == INITIAL_TIME_MILLIS - STRATEGY_TIME_MILLIS;
            case SPECIFIED:
                return time == STRATEGY_TIME_MILLIS;
            default:
                throw new AssertionError();
            }
        }
        return time == INITIAL_TIME_MILLIS;
    }

    private enum StrategyType {
        CURRENT("current"), INCREASE("+%s"), DECREASE("-%s"), SPECIFIED("%s");

        final String config;

        StrategyType(String config) {
            this.config = config;
        }
    }
}
