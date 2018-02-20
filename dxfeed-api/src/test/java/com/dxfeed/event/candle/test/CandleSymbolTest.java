/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.candle.test;

import java.util.Random;

import com.dxfeed.event.candle.*;
import junit.framework.TestCase;

public class CandleSymbolTest extends TestCase {
    public void testCandleSymbolParsing() {
        Random rnd = new Random(20131202);
        for (int i = 0; i < 10000; i++) {
            String baseSymbol = randomSymbol(rnd);
            CandleAlignment alignment = randomEnum(rnd, CandleAlignment.values());
            CandleExchange exchange = rnd.nextBoolean() ? CandleExchange.DEFAULT : CandleExchange.valueOf(randomChar(rnd));
            CandlePeriod period = CandlePeriod.valueOf((rnd.nextInt(21) - 10) / 10.0, randomEnum(rnd, CandleType.values()));
            CandleSession session = randomEnum(rnd, CandleSession.values());
            CandleSymbol candleSymbol0 = CandleSymbol.valueOf(baseSymbol, alignment, exchange, period, session);
            CandleSymbol candleSymbol1 = CandleSymbol.valueOf(candleSymbol0.toString());
            assertEquals(baseSymbol, candleSymbol1.getBaseSymbol());
            assertEquals(alignment, candleSymbol1.getAlignment());
            assertEquals(exchange, candleSymbol1.getExchange());
            assertEquals(period, candleSymbol1.getPeriod());
            assertEquals(session, candleSymbol1.getSession());
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
