/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.api.test;

import java.util.*;

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.api.osub.WildcardSymbol;
import com.dxfeed.event.candle.*;
import com.dxfeed.event.market.*;
import com.dxfeed.event.option.Series;
import org.junit.Assert;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;

public class DXPublisherTest extends AbstractDXPublisherTest {
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
                q.setBidTime(i++ * 1000);
                q.setAskPrice(i++);
                q.setAskSize(i++);
                q.setAskTime(i++ * 1000);
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
            t.setTimeNanos(i++ * 1000_000 + i++);
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
            Assert.assertEquals(publishedEvent.getDayId(), receivedEvent.getDayId());
            Assert.assertEquals(publishedEvent.getEventTime(), receivedEvent.getEventTime());
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
            p.setHaltStartTime(i++ * 1000);
            p.setHaltEndTime(i++ * 1000);
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
            ts.setTimeNanos(i++ * 1000_000 + i++);
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
            o.setTimeNanos(i++ * 1000_000 + i++);
            o.setSequence(i++);
            o.setMarketMaker(Integer.toHexString(i++));
            return o;
        };
        Object subSymbol = new IndexedEventSubscriptionSymbol<>(symbol, OrderSource.DEFAULT);
        testEventPublishing(Order.class, subSymbol, eventCreator);
        testEventPublishing(Order.class, WildcardSymbol.ALL, eventCreator);
//      testHistory(Order.class, eventCreator);
    }

    @Test
    public void testCandle() throws InterruptedException {
        // Create candle symbols
        Set<CandleSymbol> symbols = new LinkedHashSet<>();
        Random r = new Random(0);
        for (int i = 0; i < 100; i++) {
            CandleSymbol symbol = CandleSymbol.valueOf("Candle",
                CandlePeriod.valueOf(r.nextInt(3) + 1, CandleType.values()[r.nextInt(CandleType.values().length)]));
            if (r.nextBoolean())
                symbol = CandleSymbol.valueOf(symbol.toString(), CandlePrice.values()[r.nextInt(CandlePrice.values().length)]);
            if (r.nextBoolean())
                symbol = CandleSymbol.valueOf(symbol.toString(), CandleSession.values()[r.nextInt(CandleSession.values().length)]);
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
                assertEquals(publishedEvent.getClose(), receivedEvent.getClose());
                assertEquals(publishedEvent.getVolume(), receivedEvent.getVolume());
                if (symbol.getPeriod() != CandlePeriod.TICK) {
                    assertEquals(publishedEvent.getCount(), receivedEvent.getCount());
                    assertEquals(publishedEvent.getOpen(), receivedEvent.getOpen());
                    assertEquals(publishedEvent.getHigh(), receivedEvent.getHigh());
                    assertEquals(publishedEvent.getLow(), receivedEvent.getLow());
                    assertEquals(publishedEvent.getVWAP(), receivedEvent.getVWAP());
                } else {
                    assertEquals(1, receivedEvent.getCount());
                    assertEquals(publishedEvent.getClose(), receivedEvent.getOpen());
                    assertEquals(publishedEvent.getClose(), receivedEvent.getHigh());
                    assertEquals(publishedEvent.getClose(), receivedEvent.getLow());
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
                Assert.assertEquals(publishedEvent.getTime(), receivedEvent.getTime());
                Assert.assertEquals(publishedEvent.getEventTime(), receivedEvent.getEventTime());
            });
        }
    }
}