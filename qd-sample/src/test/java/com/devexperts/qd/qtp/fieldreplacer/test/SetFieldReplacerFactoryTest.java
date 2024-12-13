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
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.candle.CandleType;
import com.dxfeed.event.market.AnalyticOrder;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OtcMarketsOrder;
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Side;
import com.dxfeed.event.market.SpreadOrder;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.event.market.Trade;
import com.dxfeed.event.market.TradeETH;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@SuppressWarnings("unchecked")
@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TraceRunnerWithParametersFactory.class)
public class SetFieldReplacerFactoryTest extends AbstractFieldReplacerTest {

    private enum FieldPairs {
        BID_ASK("BidPrice:AskPrice", "Quote,TimeAndSale"),
        PRICE_SIZE("Price:Size", "TimeAndSale,Trade,TradeETH,Order,AnalyticOrder,OtcMarketsOrder,SpreadOrder"),
        HIGH_LOW("High:Low", "Candle,Trade.*"),
        HALT_START_END_TIME("HaltStartTime:HaltEndTime", "Profile");

        final String config;
        final String records;

        FieldPairs(String config, String records) {
            this.config = config;
            this.records = records;
        }
    }

    private final FieldPairs fields;

    public SetFieldReplacerFactoryTest(Object fields) {
        this.fields = (FieldPairs) fields;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> parameters() {
        return parameters(FieldPairs.values());
    }

    @Override
    protected String getReplacer() {
        StringBuilder result = new StringBuilder();
        for (String record : fields.records.split(",")) {
            result.append("(set:").append(record).append(":").append(fields.config).append(")");
        }
        return result.toString();
    }

    @Test
    public void testTimeAndSale() {
        TimeAndSale initialEvent = new TimeAndSale("IBM");
        initialEvent.setTime(123_000);
        initialEvent.setPrice(1.0);
        initialEvent.setSize(100);
        initialEvent.setAskPrice(1.1);
        initialEvent.setBidPrice(0.9);

        testEvent(initialEvent, event -> {
            switch (fields) {
                case BID_ASK:
                    return event.getBidPrice() == event.getAskPrice();
                case PRICE_SIZE:
                    return event.getPrice() == event.getSizeAsDouble();
            }
            return true;
        });
    }

    @Test
    public void testTrade() {
        Trade initialEvent = new Trade("IBM");
        initialEvent.setTime(123_000);
        initialEvent.setPrice(1.0);
        initialEvent.setSize(100);

        testEvent(initialEvent, event -> {
            if (fields == FieldPairs.PRICE_SIZE) {
                return event.getPrice() == event.getSizeAsDouble();
            }
            return true;
        });
    }

    @Test
    public void testTradeETH() {
        TradeETH initialEvent = new TradeETH("IBM");
        initialEvent.setTime(123_000);
        initialEvent.setPrice(1.0);
        initialEvent.setSize(100);

        testEvent(initialEvent, event -> {
            if (fields == FieldPairs.PRICE_SIZE) {
                return event.getPrice() == event.getSizeAsDouble();
            }
            return true;
        });
    }

    @Test
    public void testOrder() {
        Order initialEvent = new Order("IBM");
        initialEvent.setTime(123_000);
        initialEvent.setPrice(1.0);
        initialEvent.setSize(100);
        initialEvent.setOrderSide(Side.BUY);

        testEvent(initialEvent, event -> {
            if (fields == FieldPairs.PRICE_SIZE) {
                return event.getPrice() == event.getSizeAsDouble();
            }
            return true;
        });
    }

    @Test
    public void testAnalyticOrder() {
        AnalyticOrder initialEvent = new AnalyticOrder("IBM");
        initialEvent.setTime(123_000);
        initialEvent.setPrice(1.0);
        initialEvent.setSize(100);
        initialEvent.setOrderSide(Side.BUY);

        testEvent(initialEvent, event -> {
            if (fields == FieldPairs.PRICE_SIZE) {
                return event.getPrice() == event.getSizeAsDouble();
            }
            return true;
        });
    }

    @Test
    public void testOtcMarketsOrder() {
        OtcMarketsOrder initialEvent = new OtcMarketsOrder("IBM");
        initialEvent.setTime(123_000);
        initialEvent.setPrice(1.0);
        initialEvent.setSize(100);
        initialEvent.setOrderSide(Side.BUY);

        testEvent(initialEvent, event -> {
            if (fields == FieldPairs.PRICE_SIZE) {
                return event.getPrice() == event.getSizeAsDouble();
            }
            return true;
        });
    }

    @Test
    public void testSpreadOrder() {
        SpreadOrder initialEvent = new SpreadOrder("IBM");
        initialEvent.setTime(123_000);
        initialEvent.setPrice(1.0);
        initialEvent.setSize(100);
        initialEvent.setOrderSide(Side.BUY);

        testEvent(initialEvent, event -> {
            if (fields == FieldPairs.PRICE_SIZE) {
                return event.getPrice() == event.getSizeAsDouble();
            }
            return true;
        });
    }

    @Test
    public void testCandle() {
        // Bare symbol doesn't turn around via tape well.
        Candle initialEvent = new Candle(CandleSymbol.valueOf("IBM", CandlePeriod.valueOf(1.0, CandleType.WEEK)));
        initialEvent.setTime(123_000);
        initialEvent.setOpen(10.0);
        initialEvent.setHigh(12.0);
        initialEvent.setLow(8.0);
        initialEvent.setClose(9.0);

        testEvent(initialEvent, event -> {
            if (fields == FieldPairs.HIGH_LOW) {
                return event.getHigh() == event.getLow();
            }
            return true;
        });
    }

    @Test
    public void testQuote() {
        Quote initialEvent = new Quote("IBM");
        initialEvent.setBidTime(123_000);
        initialEvent.setBidPrice(10.0);
        initialEvent.setAskTime(234_000);
        initialEvent.setAskPrice(11.0);

        testEvent(initialEvent, event -> {
            if (fields == FieldPairs.BID_ASK) {
                return event.getBidPrice() == event.getAskPrice();
            }
            return true;
        });
    }

    @Test
    public void testProfile() {
        Profile initialEvent = new Profile("IBM");
        initialEvent.setHaltStartTime(123_000);
        initialEvent.setHaltEndTime(234_000);

        testEvent(initialEvent, event -> {
            if (fields == FieldPairs.HALT_START_END_TIME) {
                return event.getHaltStartTime() == event.getHaltEndTime();
            }
            return true;
        });
    }
}
