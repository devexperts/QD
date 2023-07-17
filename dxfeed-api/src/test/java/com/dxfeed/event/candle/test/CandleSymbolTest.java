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
package com.dxfeed.event.candle.test;

import com.dxfeed.event.candle.CandleAlignment;
import com.dxfeed.event.candle.CandleExchange;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandlePriceLevel;
import com.dxfeed.event.candle.CandleSession;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.candle.CandleType;
import com.dxfeed.event.market.MarketEventSymbols;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class CandleSymbolTest {

    @Test
    public void testCandleSymbolParsing() {
        Random rnd = new Random(20131202);
        for (int i = 0; i < 10000; i++) {
            String baseSymbol = randomSymbol(rnd);
            CandleAlignment alignment = randomEnum(rnd, CandleAlignment.values());
            CandleExchange exchange = rnd.nextBoolean() ?
                CandleExchange.DEFAULT : CandleExchange.valueOf(randomExchange(rnd));
            CandlePeriod period =
                CandlePeriod.valueOf((rnd.nextInt(21) - 10) / 10.0, randomEnum(rnd, CandleType.values()));
            CandleSession session = randomEnum(rnd, CandleSession.values());
            CandlePriceLevel priceLevel = CandlePriceLevel.valueOf((rnd.nextInt(21)) / 10.0);
            CandleSymbol symbol0 = CandleSymbol.valueOf(baseSymbol, alignment, exchange, period, session, priceLevel);
            CandleSymbol symbol1 = CandleSymbol.valueOf(symbol0.toString());
            assertEquals(baseSymbol, symbol1.getBaseSymbol());
            assertEquals(alignment, symbol1.getAlignment());
            assertEquals(exchange, symbol1.getExchange());
            assertEquals(period, symbol1.getPeriod());
            assertEquals(session, symbol1.getSession());
            assertEquals(priceLevel, symbol1.getPriceLevel());
            assertEquals(symbol0, symbol1);
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

    private char randomExchange(Random rnd) {
        return MarketEventSymbols.SUPPORTED_EXCHANGES.charAt(
            rnd.nextInt(MarketEventSymbols.SUPPORTED_EXCHANGES.length()));
    }
}
