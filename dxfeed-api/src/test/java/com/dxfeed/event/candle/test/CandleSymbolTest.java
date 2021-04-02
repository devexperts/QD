/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.candle.test;

import com.dxfeed.event.candle.CandleAlignment;
import com.dxfeed.event.candle.CandleExchange;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandlePriceLevel;
import com.dxfeed.event.candle.CandleSession;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.candle.CandleType;
import junit.framework.TestCase;

import java.util.Random;

public class CandleSymbolTest extends TestCase {
    public void testCandleSymbolParsing() {
        Random rnd = new Random(20131202);
        for (int i = 0; i < 10000; i++) {
            String baseSymbol = randomSymbol(rnd);
            CandleAlignment alignment = randomEnum(rnd, CandleAlignment.values());
            CandleExchange exchange = rnd.nextBoolean() ? CandleExchange.DEFAULT : CandleExchange.valueOf(randomChar(rnd));
            CandlePeriod period = CandlePeriod.valueOf((rnd.nextInt(21) - 10) / 10.0, randomEnum(rnd, CandleType.values()));
            CandleSession session = randomEnum(rnd, CandleSession.values());
            CandlePriceLevel priceLevel = CandlePriceLevel.valueOf((rnd.nextInt(21)) / 10.0);
            CandleSymbol candleSymbol0 = CandleSymbol.valueOf(baseSymbol, alignment, exchange, period, session, priceLevel);
            CandleSymbol candleSymbol1 = CandleSymbol.valueOf(candleSymbol0.toString());
            assertEquals(baseSymbol, candleSymbol1.getBaseSymbol());
            assertEquals(alignment, candleSymbol1.getAlignment());
            assertEquals(exchange, candleSymbol1.getExchange());
            assertEquals(period, candleSymbol1.getPeriod());
            assertEquals(session, candleSymbol1.getSession());
            assertEquals(priceLevel, candleSymbol1.getPriceLevel());
            assertEquals(candleSymbol0, candleSymbol1);
        }
    }

    private <T extends Enum<T>> T randomEnum(Random rnd, T[] values) {
        return values[rnd.nextInt(values.length)];
    }

    private String randomSymbol(Random rnd) {
        int len = rnd.nextInt(5) + 1;
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++)
            sb.append(randomChar(rnd));
        return sb.toString();
    }

    private char randomChar(Random rnd) {
        return (char) ('A' + rnd.nextInt('Z' - 'A' + 1));
    }
}
