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
package com.dxfeed.api.codegen;

import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.services.Services;
import com.devexperts.test.TraceRunner;
import com.dxfeed.api.codegen.event.BetterCandle;
import com.dxfeed.api.codegen.event.BetterOrder;
import com.dxfeed.api.codegen.event.BetterQuote;
import com.dxfeed.api.codegen.event.CustomEvent;
import com.dxfeed.api.codegen.event.CustomMarketEvent;
import com.dxfeed.api.codegen.event.WrappedInt;
import com.dxfeed.api.impl.DXFeedScheme;
import com.dxfeed.api.impl.EventDelegateFactory;
import com.dxfeed.api.impl.SchemeBuilder;
import com.dxfeed.api.impl.SchemeProperties;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.event.IndexedEventSource;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandlePrice;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.candle.CandleType;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Scope;
import com.dxfeed.event.market.Side;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.time.Year;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(TraceRunner.class)
public class CustomSchemeCompatibilityTest {

    private static final String QUOTE_SYMBOL = "Quote";
    private static final CandleSymbol CANDLE_SYMBOL =
        CandleSymbol.valueOf("Candle", CandlePeriod.valueOf(1, CandleType.MINUTE), CandlePrice.BID);
    private static final String CUSTOM_SYMBOL = "Custom";
    private static final IndexedEventSubscriptionSymbol<String> ORDER_SYMBOL =
        new IndexedEventSubscriptionSymbol<>("Order", IndexedEventSource.DEFAULT);

    private static final String EVENT_FACTORY_IMPL = "com.dxfeed.api.codegen.event.EventFactoryImpl";
    private static final long TIME0 = System.currentTimeMillis() / 1000 * 1000;

    @Before
    public void setUp() {
        // prevent Candle to be sent as Trade records
        System.setProperty("com.dxfeed.event.candle.impl.Trade.suffixes", "");
    }

    @Test
    public void testCustomThroughDefault() throws Exception {
        try (TestEndpoint publisherEndpoint = new TestEndpoint(DXFeedScheme.getInstance()).bindAuto();
             TestEndpoint mux =
                 new TestEndpoint(buildDefaultScheme()).bindAuto().connect(publisherEndpoint.getServerPort());
             TestEndpoint feedEndpoint = new TestEndpoint(DXFeedScheme.getInstance()).connect(mux.getServerPort()))
        {
            SchemeCompatibilityChecker checker =
                new SchemeCompatibilityChecker(publisherEndpoint.getPublisher(), feedEndpoint.getFeed());

            checker.checkEventPublishing(BetterQuote.class, BetterQuote.class, QUOTE_SYMBOL,
                i -> fillBetterQuoteFields(new BetterQuote(QUOTE_SYMBOL), i),
                (pub, sub) -> {
                    checkCompatible(pub, sub, Quote.class);
                    checkDefaultValues(sub);
                });

            checker.checkEventPublishing(BetterCandle.class, BetterCandle.class, CANDLE_SYMBOL,
                i -> fillBetterCandleFields(new BetterCandle(CANDLE_SYMBOL), i),
                (pub, sub) -> {
                    checkCompatible(pub, sub, Candle.class);
                    checkDefaultValues(sub);
                });

            checker.checkEventPublishing(BetterOrder.class, BetterOrder.class, ORDER_SYMBOL,
                i -> fillBetterOrderFields(new BetterOrder(ORDER_SYMBOL.getEventSymbol()), i),
                (pub, sub) -> {
                    checkCompatible(pub, sub, Order.class);
                    checkDefaultValues(sub);
                });
        }
    }


    @Test
    public void testCustomToDefault() throws Exception {
        try (TestEndpoint publisherEndpoint = new TestEndpoint(DXFeedScheme.getInstance()).bindAuto();
             TestEndpoint feedEndpoint =
                 new TestEndpoint(buildDefaultScheme()).connect(publisherEndpoint.getServerPort()))
        {
            SchemeCompatibilityChecker checker
                = new SchemeCompatibilityChecker(publisherEndpoint.getPublisher(), feedEndpoint.getFeed());

            checker.checkEventPublishing(Quote.class, BetterQuote.class, QUOTE_SYMBOL,
                i -> fillBetterQuoteFields(new BetterQuote(QUOTE_SYMBOL), i),
                (pub, sub) -> checkCompatible(pub, sub, Quote.class));

            checker.checkEventPublishing(Candle.class, BetterCandle.class, CANDLE_SYMBOL,
                i -> fillBetterCandleFields(new BetterCandle(CANDLE_SYMBOL), i),
                (pub, sub) -> checkCompatible(pub, sub, Candle.class));

            checker.checkEventPublishing(Order.class, BetterOrder.class, ORDER_SYMBOL,
                i -> fillBetterOrderFields(new BetterOrder(ORDER_SYMBOL.getEventSymbol()), i),
                (pub, sub) -> checkCompatible(pub, sub, Order.class));
        }
    }

    @Test
    public void testDefaultToCustom() throws Exception {
        try (TestEndpoint publisherEndpoint = new TestEndpoint(buildDefaultScheme()).bindAuto();
             TestEndpoint feedEndpoint =
                 new TestEndpoint(DXFeedScheme.getInstance()).connect(publisherEndpoint.getServerPort()))
        {
            SchemeCompatibilityChecker checker
                = new SchemeCompatibilityChecker(publisherEndpoint.getPublisher(), feedEndpoint.getFeed());

            checker.checkEventPublishing(BetterQuote.class, Quote.class, QUOTE_SYMBOL,
                i -> fillQuoteFields(new Quote(QUOTE_SYMBOL), i),
                (pub, sub) -> {
                    checkCompatible(pub, sub, Quote.class);
                    checkDefaultValues(sub);
                });

            checker.checkEventPublishing(BetterCandle.class, Candle.class, CANDLE_SYMBOL,
                i -> fillCandleFields(new Candle(CANDLE_SYMBOL), i),
                (pub, sub) -> {
                    checkCompatible(pub, sub, Candle.class);
                    checkDefaultValues(sub);
                });

            checker.checkEventPublishing(BetterOrder.class, Order.class, ORDER_SYMBOL,
                i -> fillOrderFields(new Order(ORDER_SYMBOL.getEventSymbol()), i),
                (pub, sub) -> {
                    checkCompatible(pub, sub, Order.class);
                    checkDefaultValues(sub);
                });
        }
    }

    @Test
    public void testCustomToCustom() throws Exception {
        try (TestEndpoint publisherEndpoint = new TestEndpoint(DXFeedScheme.getInstance()).bindAuto();
             TestEndpoint feedEndpoint
                 = new TestEndpoint(DXFeedScheme.getInstance()).connect(publisherEndpoint.getServerPort()))
        {
            SchemeCompatibilityChecker checker
                = new SchemeCompatibilityChecker(publisherEndpoint.getPublisher(), feedEndpoint.getFeed());
            checkFullCompatibility(checker);
        }
    }

    @Test
    public void testLocalHub() throws Exception {
        try (TestEndpoint endpoint = new TestEndpoint(DXFeedScheme.getInstance())) {
            SchemeCompatibilityChecker checker
                = new SchemeCompatibilityChecker(endpoint.getPublisher(), endpoint.getFeed());
            checkFullCompatibility(checker);
        }
    }

    private void checkFullCompatibility(SchemeCompatibilityChecker checker) throws Exception {
        checker.checkEventPublishing(BetterQuote.class, BetterQuote.class, QUOTE_SYMBOL,
            i -> fillBetterQuoteFields(new BetterQuote(QUOTE_SYMBOL), i),
            (pub, sub) -> checkCompatible(pub, sub, BetterQuote.class));

        checker.checkEventPublishing(BetterCandle.class, BetterCandle.class, CANDLE_SYMBOL,
            i -> fillBetterCandleFields(new BetterCandle(CANDLE_SYMBOL), i),
            (pub, sub) -> checkCompatible(pub, sub, BetterCandle.class));

        checker.checkEventPublishing(BetterOrder.class, BetterOrder.class, ORDER_SYMBOL,
            i -> fillBetterOrderFields(new BetterOrder(ORDER_SYMBOL.getEventSymbol()), i),
            (pub, sub) -> checkCompatible(pub, sub, BetterOrder.class));

        checker.checkEventPublishing(CustomMarketEvent.class, CustomMarketEvent.class, CUSTOM_SYMBOL,
            i -> fillCustomMarketEventFields(new CustomMarketEvent(CUSTOM_SYMBOL), i),
            (pub, sub) -> checkCompatible(pub, sub, CustomMarketEvent.class));

        checker.checkEventPublishing(CustomEvent.class, CustomEvent.class, CUSTOM_SYMBOL,
            i -> fillCustomEventFields(new CustomEvent(CUSTOM_SYMBOL), i),
            (pub, sub) -> checkCompatible(pub, sub, CustomEvent.class));
    }

    private <T extends Quote> T fillQuoteFields(T quote, int i) {
        // base fields
        quote.setBidSize(100 + i);
        quote.setBidPrice(200.5 + i);
        quote.setBidTime(TIME0 + i * 1000);
        quote.setBidExchangeCode((char) (48 + i));
        quote.setAskSize(300 + i);
        quote.setAskPrice(400.5 + i);
        quote.setAskTime(TIME0 + 5000 + i * 1000);
        quote.setAskExchangeCode((char) (52 + i));
        return quote;
    }

    private BetterQuote fillBetterQuoteFields(BetterQuote quote, int i) {
        // custom fields
        quote.setBetterChar((char) (60 + i));
        quote.setBetterInt(500 + i);
        quote.setBetterLong(600 + i);
        quote.setBetterDouble(700.5 + i);
        quote.setBetterString(Integer.toString(i));
        quote.setBetterBool(i % 3 != 0 || i % 7 != 0);
        quote.setBetterByte((byte) i);
        quote.setBetterShort((short) (1000 + i));
        quote.setBetterFloat(123.5f + i);
        return fillQuoteFields(quote, i);
    }

    private void checkDefaultValues(BetterQuote quote) {
        assertEquals(0, quote.getBetterChar());
        assertEquals(0, quote.getBetterInt());
        assertEquals(0, quote.getBetterLong());
        assertEquals(Double.NaN, quote.getBetterDouble(), 0);
        assertEquals(null, quote.getBetterString());
    }

    private <T extends Candle> T fillCandleFields(T candle, int i) {
        // base fields
        candle.setTime(TIME0 + i * 1000);
        candle.setSequence(91737 + i);
        candle.setCount(100 + i);
        candle.setOpen(200.5 + i);
        candle.setHigh(300.75 + i);
        candle.setLow(400.6 + i);
        candle.setClose(500.1 + i);
        candle.setVolume(600 + i);
        candle.setVWAP(700.7 + i);
        candle.setBidVolume(8000 + i);
        candle.setAskVolume(90000 + i);
        return candle;
    }

    private BetterCandle fillBetterCandleFields(BetterCandle candle, int i) {
        // custom fields
        candle.setBetterInt(10000 + i);
        candle.setDate(Year.of(2016).atDay(i + 1).atStartOfDay());
        candle.setBetterDecimalDouble(800.2 + i);
        candle.setBetterDecimalLong(900 + i);
        candle.setShortString(Integer.toString(i));
        candle.setBetterBool(i % 5 != 0 || i % 11 != 0);
        candle.setBetterByte((byte) i);
        candle.setBetterShort((short) (1000 + i));
        candle.setBetterFloat(123.5f + i);
        return fillCandleFields(candle, i);
    }

    private void checkDefaultValues(BetterCandle candle) {
        assertEquals(0, candle.getBetterInt());
        assertEquals(0, candle.getBetterDate());
        assertEquals(0, candle.getOptTime());
        assertEquals(Double.NaN, candle.getBetterDecimalDouble(), 0);
        assertEquals(0, candle.getBetterDecimalLong());
        assertEquals(null, candle.getShortString());
    }

    private <T extends Order> T fillOrderFields(T order, int i) {
        order.setIndex(1256123 + i);
        order.setTime(TIME0 + i * 1000);
        order.setSequence(100 + i);
        order.setPrice(200.5 + i);
        order.setSize(300 + i);
        order.setCount(400 + i);
        order.setExchangeCode((char) i);
        order.setOrderSide(Side.valueOf(1 + i % 2));
        order.setScope(Scope.valueOf(i % 4));
        return order;
    }

    private BetterOrder fillBetterOrderFields(BetterOrder order, int i) {
        order.setType(i % 2 == 0 ? BetterOrder.OrderType.TYPE_A : BetterOrder.OrderType.TYPE_B);
        return fillOrderFields(order, i);
    }

    private void checkDefaultValues(BetterOrder order) {
        assertEquals(BetterOrder.OrderType.NO_TYPE, order.getType());
    }

    private CustomMarketEvent fillCustomMarketEventFields(CustomMarketEvent event, int i) {
        event.setCustomInt(100 + i);
        event.setCustomLong(200 + i);
        event.setCustomShortString(Integer.toString(i));
        return event;
    }

    private CustomEvent fillCustomEventFields(CustomEvent event, int i) {
        event.setBigValue(BigInteger.valueOf(100 + i));
        event.setWrappedValue(new WrappedInt(200 + i));
        event.setAttachment(IntStream.range(0, i).boxed().collect(Collectors.toList()));
        return event;
    }

    /**
     * Checks that all methods of passed class return same values for expected and actual objects.
     * Ignores {@link Object#getClass getClass} method.
     */
    private <T> void checkCompatible(T expected, T actual, Class<? super T> clazz) {
        assertNotNull(expected);
        assertNotNull(actual);
        for (Method method : clazz.getMethods()) {
            String methodName = method.getName();
            if (!methodName.equals("getClass") && methodName.matches("(get|is).*") &&
                method.getParameterTypes().length == 0)
            {
                try {
                    Object actualValue = method.invoke(actual);
                    Object expectedValue = method.invoke(expected);
                    assertEquals(methodName, expectedValue, actualValue);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    private DefaultScheme buildDefaultScheme() {
        SchemeBuilder builder = new SchemeBuilder(new SchemeProperties(new Properties()));
        for (EventDelegateFactory factory : Services.createServices(EventDelegateFactory.class, null)) {
            if (!factory.getClass().getName().equals(EVENT_FACTORY_IMPL))
                factory.buildScheme(builder);
        }
        return new DefaultScheme(PentaCodec.INSTANCE, builder.buildRecords());
    }
}
