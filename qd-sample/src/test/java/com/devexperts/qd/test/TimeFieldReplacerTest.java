/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;

import com.devexperts.test.TraceRunnerWithParametersFactory;
import com.devexperts.timetest.TestTimeProvider;
import com.devexperts.util.TimePeriod;
import com.dxfeed.api.*;
import com.dxfeed.event.EventType;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.market.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;

@Ignore
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TraceRunnerWithParametersFactory.class)
public class TimeFieldReplacerTest {
    private static final Path FILE_WRITE_TO = Paths.get("TimeFieldReplacerTest.qds.tmp");

    private static final long INITIAL_TIME_MILLIS = 123_000;
    private static final long STRATEGY_TIME_MILLIS = 234_000;
    private static final long CURRENT_TIME_MILLIS = 345_000;

    private final boolean replace;
    private final StrategyType strategyType;
    private final String fileFormat;

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
        TestTimeProvider.reset();
        FILE_WRITE_TO.toFile().delete();
    }

    private <T extends EventType<?>> void testEvent(Class<T> clazz, T initialEvent, Consumer<T> checker,
        String recordFilterSpec) throws InterruptedException
    {
        // 1. Create endpoint with PUBLISHER role and connect to tape file
        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.PUBLISHER)
            .withProperty(DXEndpoint.DXFEED_WILDCARD_ENABLE_PROPERTY, "true")
            .build();
        DXPublisher publisher = endpoint.getPublisher();

        endpoint.connect("tape:" + FILE_WRITE_TO + "[format=" + fileFormat + "]");
        // 2. Publish event and close endpoint
        publisher.publishEvents(Collections.singletonList(initialEvent));
        endpoint.awaitProcessed();
        endpoint.closeAndAwaitTermination();
        // 3. Read published events
        endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.STREAM_FEED)
            .build();
        DXFeedSubscription<T> sub = endpoint.getFeed().createSubscription(clazz);
        List<T> events = new ArrayList<>();
        sub.addEventListener(events::addAll);
        sub.addSymbols(initialEvent.getEventSymbol());
        String fieldReplacerConfig;
        if (replace) {
            if (strategyType == StrategyType.CURRENT)
                TestTimeProvider.start(CURRENT_TIME_MILLIS);
            fieldReplacerConfig = "[fieldReplacer=time:" + recordFilterSpec + ":"
                + strategyType.createConfiguration(TimePeriod.valueOf(STRATEGY_TIME_MILLIS).toString()) + "]";
        } else {
            fieldReplacerConfig = "";
        }
        endpoint.connect("file:" + FILE_WRITE_TO + fieldReplacerConfig).awaitNotConnected();
        endpoint.closeAndAwaitTermination();
        // 4. Check that events are changed as required
        assertEquals(1, events.size());
        T modifiedEvent = events.get(0);
        checker.accept(modifiedEvent);
    }

    @Test
    public void testTimeAndSale() throws Exception {
        TimeAndSale initialEvent = new TimeAndSale("IBM");
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        testEvent(TimeAndSale.class, initialEvent, resultEvent -> {
            assertExpectedTime(resultEvent.getTime());
        }, "*");
    }

    @Test
    public void testTrade() throws Exception {
        Trade initialEvent = new Trade("IBM");
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        testEvent(Trade.class, initialEvent, resultEvent -> {
            assertExpectedTime(resultEvent.getTime());
        }, "*");
    }

    @Test
    public void testTradeETH() throws Exception {
        TradeETH initialEvent = new TradeETH("IBM");
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        testEvent(TradeETH.class, initialEvent, resultEvent -> {
            assertExpectedTime(resultEvent.getTime());
        }, "*");
    }

    @Test
    public void testOrder() throws Exception {
        Order initialEvent = new Order("IBM");
        initialEvent.setOrderSide(Side.BUY);
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        testEvent(Order.class, initialEvent, resultEvent -> {
            assertExpectedTime(resultEvent.getTime());
        }, "*");
    }

    @Test
    public void testSpreadOrder() throws Exception {
        SpreadOrder initialEvent = new SpreadOrder("IBM");
        initialEvent.setOrderSide(Side.BUY);
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        testEvent(SpreadOrder.class, initialEvent, resultEvent -> {
            assertExpectedTime(resultEvent.getTime());
        }, "*");
    }

    @Test
    public void testCandle() throws Exception {
        Candle initialEvent = new Candle(CandleSymbol.valueOf("IBM"));
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        testEvent(Candle.class, initialEvent, resultEvent -> {
            assertExpectedTime(resultEvent.getTime());
        }, "*");
    }

    @Test
    public void testQuote() throws Exception {
        Quote initialEvent = new Quote("IBM");
        initialEvent.setAskTime(INITIAL_TIME_MILLIS);
        initialEvent.setBidTime(INITIAL_TIME_MILLIS);
        testEvent(Quote.class, initialEvent, resultEvent -> {
            assertExpectedTime(resultEvent.getAskTime());
            assertExpectedTime(resultEvent.getBidTime());
        }, "*");
    }

    public void testProfile() throws Exception {
        Profile initialEvent = new Profile("IBM");
        initialEvent.setHaltStartTime(INITIAL_TIME_MILLIS);
        initialEvent.setHaltEndTime(INITIAL_TIME_MILLIS);
        testEvent(Profile.class, initialEvent, resultEvent -> {
            assertExpectedTime(resultEvent.getHaltStartTime());
            assertExpectedTime(resultEvent.getHaltEndTime());
        }, "*");
    }

    private void assertExpectedTime(long timeMillis) {
        assertEquals(getExpectedTime(), timeMillis);
    }

    private long getExpectedTime() {
        if (replace) {
            switch (strategyType) {
            case CURRENT:
                return CURRENT_TIME_MILLIS;
            case INCREASE:
                return INITIAL_TIME_MILLIS + STRATEGY_TIME_MILLIS;
            case DECREASE:
                return INITIAL_TIME_MILLIS - STRATEGY_TIME_MILLIS;
            case SPECIFIED:
                return STRATEGY_TIME_MILLIS;
            default:
                throw new AssertionError();
            }
        }
        return INITIAL_TIME_MILLIS;
    }

    private enum StrategyType {
        CURRENT {
            @Override
            String createConfiguration(String time) {
                return "current";
            }
        },
        INCREASE {
            @Override
            String createConfiguration(String time) {
                return "+" + time;
            }
        },
        DECREASE {
            @Override
            String createConfiguration(String time) {
                return "-" + time;
            }
        },
        SPECIFIED {
            @Override
            String createConfiguration(String time) {
                return time;
            }
        };

        abstract String createConfiguration(String time);
    }

}
