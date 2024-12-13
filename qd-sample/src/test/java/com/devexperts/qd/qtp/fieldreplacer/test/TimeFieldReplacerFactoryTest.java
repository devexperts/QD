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
package com.devexperts.qd.qtp.fieldreplacer.test;

import com.devexperts.test.TraceRunnerWithParametersFactory;
import com.devexperts.util.TimePeriod;
import com.dxfeed.event.EventType;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Side;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.event.market.Trade;
import com.dxfeed.event.market.TradeETH;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

@SuppressWarnings("unchecked")
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TraceRunnerWithParametersFactory.class)
public class TimeFieldReplacerFactoryTest extends AbstractFieldReplacerTest {

    private static final long INITIAL_TIME_MILLIS = 123_000;
    private static final long STRATEGY_TIME_MILLIS = 234_000;

    private long testStartTime;
    private final StrategyType strategyType;

    private enum StrategyType {
        CURRENT("current"), INCREASE("+%s"), DECREASE("-%s"), SPECIFIED("%s");

        final String format;

        StrategyType(String format) {
            this.format = format;
        }
    }

    @Parameterized.Parameters(name = "strategy={0}")
    public static Iterable<Object[]> parameters() {
        return parameters(StrategyType.values());
    }

    public TimeFieldReplacerFactoryTest(Object strategyType) {
        this.strategyType = (StrategyType) strategyType;
    }

    @Override
    protected String getReplacer() {
        return "time:*:" + String.format(strategyType.format, TimePeriod.valueOf(STRATEGY_TIME_MILLIS).toString());
    }

    private <T extends EventType<?>> void testEvent(T initialEvent, ToLongFunction<T>... timeExtractors) {
        testStartTime = System.currentTimeMillis() / 1000 * 1000;

        @SuppressWarnings("unchecked")
        Predicate<T>[] predicates = (Predicate<T>[]) Arrays.stream(timeExtractors)
            .map(timeExtractor ->
                (Predicate<T>) (T event) -> checkTime(timeExtractor.applyAsLong(event)))
            .toArray(Predicate[]::new);
        testEvent(initialEvent, predicates);
    }

    @Test
    public void testTimeAndSale() {
        TimeAndSale initialEvent = new TimeAndSale("IBM");
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, TimeAndSale::getTime);
    }

    @Test
    public void testTrade() {
        Trade initialEvent = new Trade("IBM");
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, Trade::getTime);
    }

    @Test
    public void testTradeETH() {
        TradeETH initialEvent = new TradeETH("IBM");
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, TradeETH::getTime);
    }

    @Test
    public void testOrder() {
        Order initialEvent = new Order("IBM");
        initialEvent.setOrderSide(Side.BUY);
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        initialEvent.setActionTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, Order::getTime, Order::getActionTime);
    }

    @Test
    public void testCandle() {
        Candle initialEvent = new Candle(CandleSymbol.valueOf("IBM"));
        initialEvent.setTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, Candle::getTime);
    }

    @Test
    public void testQuote() {
        Quote initialEvent = new Quote("IBM");
        initialEvent.setBidTime(INITIAL_TIME_MILLIS);
        initialEvent.setAskTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, Quote::getBidTime, Quote::getAskTime);
    }

    @Test
    public void testProfile() {
        Profile initialEvent = new Profile("IBM");
        initialEvent.setHaltStartTime(INITIAL_TIME_MILLIS);
        initialEvent.setHaltEndTime(INITIAL_TIME_MILLIS);
        testEvent(initialEvent, Profile::getHaltStartTime, Profile::getHaltEndTime);
    }

    private boolean checkTime(long time) {
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
}
