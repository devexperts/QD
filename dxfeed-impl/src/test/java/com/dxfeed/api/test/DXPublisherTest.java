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
package com.dxfeed.api.test;

import com.devexperts.logging.Logging;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.api.osub.WildcardSymbol;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandlePrice;
import com.dxfeed.event.candle.CandleSession;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.candle.CandleType;
import com.dxfeed.event.market.MarketEventSymbols;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderSource;
import com.dxfeed.event.market.PriceType;
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Scope;
import com.dxfeed.event.market.Side;
import com.dxfeed.event.market.Summary;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.event.market.TimeAndSaleType;
import com.dxfeed.event.market.Trade;
import com.dxfeed.event.option.Series;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class DXPublisherTest extends AbstractDXPublisherTest {
    private static final Logging log = Logging.getLogging(DXPublisherTest.class);

    // Note that '9' and '~' are not a supported "exchange codes" and subscription to
    // "SYM&9" and "SYM&~" should go via composite record
    private static final char[] EXCHANGE_CODES = "A~Z\0X9B".toCharArray();

    public DXPublisherTest(DXEndpoint.Role role) {
        super(role);
    }

    @Override
    protected DXEndpoint.Builder endpointBuilder() {
        return super.endpointBuilder()
            .withProperty(DXEndpoint.DXSCHEME_NANO_TIME_PROPERTY, "true");
    }

    @Test
    public void testSubscriptionNotificationLoss() throws InterruptedException {
        setUp("testSubscriptionNotificationLoss");
        Set<Object> symbols = new HashSet<>(Arrays.asList("AAPL,GOOG,IBM,GE".split(",")));
        Object firstSymbol = symbols.iterator().next();
        Set<Object> added = new HashSet<>();
        Set<Object> removed = new HashSet<>();
        ObservableSubscriptionChangeListener observableSubChangeListener = new ObservableSubscriptionChangeListener() {
            @Override
            public void symbolsAdded(Set<?> symbols) {
                log.info("symbolsAdded " + symbols);
                added.addAll(symbols);
                assertFalse(symbols.isEmpty());
                throw new RuntimeException();
            }

            @Override
            public void symbolsRemoved(Set<?> symbols) {
                log.info("symbolsRemoved " + symbols);
                removed.addAll(symbols);
                assertFalse(symbols.isEmpty());
                throw new RuntimeException();
            }
        };
        Class<Quote> eventType = Quote.class;
        publisher.getSubscription(eventType).addChangeListener(observableSubChangeListener);
        DXFeedSubscription<Quote> sub = feed.createSubscription(eventType);
        log.info("Adding symbol " + firstSymbol);
        sub.addSymbols(firstSymbol);
        checkpoint();
        log.info("Adding rest of symbols " + symbols);
        sub.addSymbols(symbols.toArray());
        checkpoint();
        assertEquals(symbols, added);

        log.info("Removing symbol " + firstSymbol);
        sub.removeSymbols(firstSymbol);
        checkpoint();
        log.info("Closing subscription with rest of symbols " + symbols);
        sub.close();
        checkpoint();
        assertEquals(symbols, added);
        assertEquals(symbols, removed);

        publisher.getSubscription(eventType).removeChangeListener(observableSubChangeListener);
        tearDown();
    }

    @Test
    public void testQuote() throws InterruptedException {
        for (final char exchangeCode : EXCHANGE_CODES) {
            final String symbol = MarketEventSymbols.changeExchangeCode("Quote", exchangeCode);
            EventCreator<Quote> eventCreator = i -> {
                Quote q = new Quote(symbol);
                if (i < 0)
                    return q;
                q.setEventTime(i++);
                q.setBidPrice(i++);
                q.setBidSize(i++);
                q.setBidExchangeCode(exchangeCode == 0 ? 'X' : exchangeCode);
                q.setBidTime(i++ * 1000L);
                q.setAskPrice(i++);
                q.setAskSize(i++);
                q.setAskTime(i++ * 1000L);
                q.setTimeNanoPart(i++);
                q.setAskExchangeCode(exchangeCode == 0 ? 'Y' : exchangeCode);
                return q;
            };
            testEventPublishing(Quote.class, symbol, eventCreator);
            testEventPublishing(Quote.class, WildcardSymbol.ALL, eventCreator);
        }
        // Test getLastEvent
        Quote quote = new Quote("Quote");
        quote.setAskSize(1000);
        quote.setEventTime(100);
        testGetLastEvent(Quote.class, quote, new Quote(), (publishedEvent, receivedEvent) -> {
            assertEquals(publishedEvent.getAskSize(), receivedEvent.getAskSize());
            assertEquals(publishedEvent.getEventTime(), receivedEvent.getEventTime());
        });
    }

    @Test
    public void testTrade() throws InterruptedException {
        final String symbol = "Trade";
        testEventPublishing(Trade.class, symbol, i -> {
            Trade t = new Trade(symbol);
            if (i < 0)
                return t;
            t.setEventTime(i++);
            t.setTime(i++);
            t.setSequence(i++);
            t.setPrice(i++);
            t.setSize(i++);
            char exchangeCode = EXCHANGE_CODES[i % EXCHANGE_CODES.length];
            t.setExchangeCode(exchangeCode == 0 ? 'C' : exchangeCode);
            t.setTimeNanos(i++ * 1000_000L + i++);
            t.setDayVolume(i++);
            return t;
        });
        // Test getLastEvent
        Trade trade = new Trade("Trade");
        trade.setSize(1000);
        trade.setEventTime(100);
        testGetLastEvent(Trade.class, trade, new Trade(), (publishedEvent, receivedEvent) -> {
            assertEquals(publishedEvent.getSize(), receivedEvent.getSize());
            assertEquals(publishedEvent.getEventTime(), receivedEvent.getEventTime());
        });
    }

    @Test
    public void testSummary() throws InterruptedException {
        final String symbol = "Summary";
        testEventPublishing(Summary.class, symbol, i -> {
            Summary s = new Summary(symbol);
            if (i < 0)
                return s;
            s.setEventTime(i++);
            s.setDayId(i++);
            s.setDayOpenPrice(i++);
            s.setDayClosePrice(i++);
            s.setDayHighPrice(i++);
            s.setDayLowPrice(i++);
            s.setPrevDayId(i++);
            s.setPrevDayClosePrice(i++);
            s.setOpenInterest(i++);
            s.setDayClosePriceType(PriceType.values()[i++ % PriceType.values().length]);
            s.setPrevDayClosePriceType(PriceType.values()[i++ % PriceType.values().length]);
            return s;
        });
        // Test getLastEvent
        Summary summary = new Summary(symbol);
        summary.setDayId(100);
        summary.setEventTime(100);
        testGetLastEvent(Summary.class, summary, new Summary(), (publishedEvent, receivedEvent) -> {
            assertEquals(publishedEvent.getDayId(), receivedEvent.getDayId());
            assertEquals(publishedEvent.getEventTime(), receivedEvent.getEventTime());
        });
    }

    @Test
    public void testProfile() throws InterruptedException {
        final String symbol = "Profile";
        testEventPublishing(Profile.class, symbol, i -> {
            Profile p = new Profile(symbol);
            if (i < 0)
                return p;
            p.setEventTime(i++);
            p.setHaltStartTime(i++ * 1000L);
            p.setHaltEndTime(i++ * 1000L);
            p.setDescription("" + i++);
            p.setStatusReason("" + i++);
            return p;
        });
        // Test getLastEvent
        Profile profile = new Profile(symbol);
        profile.setHighLimitPrice(100);
        profile.setEventTime(100);
        testGetLastEvent(Profile.class, profile, new Profile(), (publishedEvent, receivedEvent) -> {
            assertEquals(publishedEvent.getHighLimitPrice(), receivedEvent.getHighLimitPrice(), 0);
            assertEquals(publishedEvent.getEventTime(), receivedEvent.getEventTime());
        });
    }

    @Test
    public void testSeries() throws InterruptedException {
        final String symbol = "Series";
        testEventPublishing(Series.class, symbol, i -> {
            Series s = new Series(symbol);
            if (i < 0)
                return s;
            s.setEventTime(i++);
            s.setIndex(i++);
            s.setExpiration(i++);
            s.setVolatility(i++);
            s.setPutCallRatio(i++);
            s.setForwardPrice(i++);
            s.setInterest(i++);
            s.setDividend(i++);
            return s;
        });
    }

    @Test
    public void testTimeAndSale() throws InterruptedException {
        final String symbol = "TimeAndSale";
        EventCreator<TimeAndSale> eventCreator = i -> {
            TimeAndSale ts = new TimeAndSale(symbol);
            if (i < 0)
                return ts;
            ts.setEventTime(i++);
            ts.setTimeNanos(i++ * 1000_000L + i++);
            ts.setSequence(i++);
            ts.setPrice(i++);
            ts.setSize(i++);
            ts.setExchangeCode((char) i++);
            ts.setBidPrice(i++);
            ts.setAskPrice(i++);
            ts.setExchangeSaleConditions(i++ == 0 ? null : Integer.toBinaryString(i++ % 16));
            ts.setValidTick(i++ % 2 == 0);
            ts.setType(TimeAndSaleType.values()[i++ % TimeAndSaleType.values().length]);
            return ts;
        };
        testEventPublishing(TimeAndSale.class, symbol, eventCreator);
        testEventPublishing(TimeAndSale.class, WildcardSymbol.ALL, eventCreator);
        if (role == DXEndpoint.Role.LOCAL_HUB)
            testTimeSeriesEventPublishing(TimeAndSale.class, symbol, eventCreator);
    }

    @Test
    public void testOrder() throws InterruptedException {
        final String symbol = "Order";
        EventCreator<Order> eventCreator = i -> {
            Order o = new Order(symbol);
            if (i < 0)
                return o;
            o.setEventTime(i++);
            o.setIndex(i++);
            o.setScope(Scope.values()[i++ % Scope.values().length]);
            o.setOrderSide(i++ % 2 == 0 ? Side.BUY : Side.SELL);
            o.setPrice(i++);
            o.setSize(i++);
            o.setExchangeCode((char) i);
            o.setTimeNanos(i++ * 1000_000L + i++);
            o.setSequence(i++);
            o.setMarketMaker(Integer.toHexString(i++));
            return o;
        };
        Object subSymbol = new IndexedEventSubscriptionSymbol<>(symbol, OrderSource.DEFAULT);
        testEventPublishing(Order.class, subSymbol, eventCreator);
        testEventPublishing(Order.class, WildcardSymbol.ALL, eventCreator);
        //testHistory(Order.class, eventCreator);
    }

    @Test
    public void testCandle() throws InterruptedException {
        // Create candle symbols
        Set<CandleSymbol> symbols = new LinkedHashSet<>();
        Random r = new Random(0);
        for (int i = 0; i < 100; i++) {
            CandleSymbol symbol = CandleSymbol.valueOf("Candle",
                CandlePeriod.valueOf(r.nextInt(3) + 1, CandleType.values()[r.nextInt(CandleType.values().length)]));
            if (r.nextBoolean()) {
                CandlePrice priceAttr = CandlePrice.values()[r.nextInt(CandlePrice.values().length)];
                symbol = CandleSymbol.valueOf(symbol.toString(), priceAttr);
            }
            if (r.nextBoolean()) {
                CandleSession sessionAttr = CandleSession.values()[r.nextInt(CandleSession.values().length)];
                symbol = CandleSymbol.valueOf(symbol.toString(), sessionAttr);
            }
            symbols.add(symbol);
        }
        for (CandlePrice price : CandlePrice.values()) {
            // explicitly test special 1 minute records for different price types
            CandleSymbol symbol = CandleSymbol.valueOf("Candle", price, CandlePeriod.valueOf(1, CandleType.MINUTE));
            symbols.add(symbol);
        }
        // Test
        for (final CandleSymbol symbol : symbols) {
            EventCreator<Candle> eventCreator = i -> {
                Candle c = new Candle(symbol);
                if (i < 0)
                    return c;
                c.setEventTime(i++);
                c.setTime(i++);
                c.setSequence(i++);
                c.setClose(i++);
                c.setVolume(i++);
                if (symbol.getPeriod() != CandlePeriod.TICK) {
                    c.setCount(i++);
                    c.setOpen(i++);
                    c.setHigh(i++);
                    c.setLow(i++);
                    c.setVWAP(i++);
                }
                return c;
            };
            EventChecker<Candle> eventChecker = (publishedEvent, receivedEvent) -> {
                assertEquals(publishedEvent.getEventTime(), receivedEvent.getEventTime());
                assertEquals(publishedEvent.getIndex(), receivedEvent.getIndex());
                assertEquals(publishedEvent.getTime(), receivedEvent.getTime());
                assertEquals(publishedEvent.getClose(), receivedEvent.getClose(), 0);
                assertEquals(publishedEvent.getVolume(), receivedEvent.getVolume());
                if (symbol.getPeriod() != CandlePeriod.TICK) {
                    assertEquals(publishedEvent.getCount(), receivedEvent.getCount());
                    assertEquals(publishedEvent.getOpen(), receivedEvent.getOpen(), 0);
                    assertEquals(publishedEvent.getHigh(), receivedEvent.getHigh(), 0);
                    assertEquals(publishedEvent.getLow(), receivedEvent.getLow(), 0);
                    assertEquals(publishedEvent.getVWAP(), receivedEvent.getVWAP(), 0);
                } else {
                    assertEquals(1, receivedEvent.getCount());
                    assertEquals(publishedEvent.getClose(), receivedEvent.getOpen(), 0);
                    assertEquals(publishedEvent.getClose(), receivedEvent.getHigh(), 0);
                    assertEquals(publishedEvent.getClose(), receivedEvent.getLow(), 0);
                }
            };
            testEventPublishing(Candle.class, symbol, eventCreator, eventChecker);
            testEventPublishing(Candle.class, WildcardSymbol.ALL, eventCreator, eventChecker);
            if (role == DXEndpoint.Role.LOCAL_HUB)
                testTimeSeriesEventPublishing(Candle.class, symbol, eventCreator, eventChecker);
            // Test getLastEvent
            Candle candle = new Candle(symbol);
            candle.setTime(100);
            candle.setEventTime(100);
            testGetLastEvent(Candle.class, candle, new Candle(), (publishedEvent, receivedEvent) -> {
                assertEquals(publishedEvent.getTime(), receivedEvent.getTime());
                assertEquals(publishedEvent.getEventTime(), receivedEvent.getEventTime());
            });
        }
    }
}
