/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.candle.test;

import com.dxfeed.event.candle.CandleAlignment;
import com.dxfeed.event.candle.CandleDataType;
import com.dxfeed.event.candle.CandleExchange;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandlePrice;
import com.dxfeed.event.candle.CandlePriceLevel;
import com.dxfeed.event.candle.CandleSession;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.candle.CandleType;
import com.dxfeed.event.market.MarketEventSymbols;
import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class CandleSymbolTest {

    private static final CandleDataType[] DATA_TYPES = {
        CandleDataType.UNDEFINED,
        CandleDataType.SOURCE,
        CandleDataType.ALL,
        CandleDataType.RTH,
        CandleDataType.ETH,
        CandleDataType.THO
    };

    @Test
    public void testCandlePeriods() {
        assertEquals(CandlePeriod.DAY, CandlePeriod.parse("DAY"));
        assertEquals(CandlePeriod.DAY, CandlePeriod.parse("1DAY"));
        assertEquals(CandlePeriod.DAY, CandlePeriod.parse("1day"));
        assertEquals(CandlePeriod.DAY, CandlePeriod.parse("1Day"));
        assertEquals(CandlePeriod.DAY, CandlePeriod.parse("Day"));
        assertEquals(CandlePeriod.DAY, CandlePeriod.parse("Days"));
        assertEquals(CandlePeriod.DAY, CandlePeriod.parse("d"));
        assertEquals(CandlePeriod.DAY, CandlePeriod.parse("1d"));

        assertEquals(CandlePeriod.TICK, CandlePeriod.parse("TICK"));
        assertEquals(CandlePeriod.TICK, CandlePeriod.parse("tick"));
        assertEquals(CandlePeriod.TICK, CandlePeriod.parse("Ticks"));
        assertEquals(CandlePeriod.TICK, CandlePeriod.parse("1tick"));
        assertEquals(CandlePeriod.TICK, CandlePeriod.parse("1T"));

        assertEquals(CandlePeriod.valueOf(5, CandleType.MINUTE), CandlePeriod.parse("5MINUTE"));
        assertEquals(CandlePeriod.valueOf(5, CandleType.MINUTE), CandlePeriod.parse("5min"));
        assertEquals(CandlePeriod.valueOf(5, CandleType.MINUTE), CandlePeriod.parse("5Min"));
        assertEquals(CandlePeriod.valueOf(5, CandleType.MINUTE), CandlePeriod.parse("5m"));
        assertEquals(CandlePeriod.valueOf(5, CandleType.MINUTE), CandlePeriod.parse("5M"));

        Set<CandlePeriod> set = new HashSet<CandlePeriod>();
        for (double val = 0; val <= 5; val += 0.25) {
            for (CandleType type : CandleType.values()) {
                CandlePeriod p = CandlePeriod.valueOf(val, type);
                assertTrue("All should be different", set.add(p));
                String s1 = p.toString();
                assertEquals(p, CandlePeriod.parse(s1));
                String s2 = val + type.name();
                assertEquals(p, CandlePeriod.parse(s2));
            }
        }
    }

    @Test
    public void testCandleDataTypes() {
        assertEquals(CandleDataType.SOURCE, CandleDataType.valueOf("source"));
        assertEquals(CandleDataType.SOURCE, CandleDataType.parse("source"));
        assertNotEquals(CandleDataType.SOURCE, CandleDataType.parse("SOURCE"));

        assertEquals(CandleDataType.THO, CandleDataType.valueOf("tho"));
        assertEquals(CandleDataType.THO, CandleDataType.parse("tho"));
        assertNotEquals(CandleDataType.THO, CandleDataType.parse("THO"));

        assertEquals(CandleDataType.ALL, CandleDataType.valueOf("all"));
        assertEquals(CandleDataType.ALL, CandleDataType.parse("all"));
        assertNotEquals(CandleDataType.ALL, CandleDataType.parse("ALL"));

        assertEquals(CandleDataType.RTH, CandleDataType.valueOf("rth"));
        assertEquals(CandleDataType.RTH, CandleDataType.parse("rth"));
        assertNotEquals(CandleDataType.RTH, CandleDataType.parse("RTH"));

        assertEquals(CandleDataType.ETH, CandleDataType.valueOf("eth"));
        assertEquals(CandleDataType.ETH, CandleDataType.parse("eth"));
        assertNotEquals(CandleDataType.ETH, CandleDataType.parse("ETH"));

        assertEquals(CandleDataType.DEFAULT, CandleDataType.valueOf(""));
        assertEquals(CandleDataType.DEFAULT, CandleDataType.parse(""));
        assertNotEquals(CandleDataType.DEFAULT, CandleDataType.parse(" "));

        for (CandleDataType type : DATA_TYPES) {
            assertEquals(type.getValue(), type.toString());
            assertEquals(type, CandleDataType.valueOf(type.toString()));
            assertEquals(type, CandleDataType.parse(type.toString()));
        }
    }

    @Test
    public void testNormalization() {
        assertEquals("IBM&E", CandleSymbol.valueOf("IBM", CandleExchange.valueOf('E')).toString());
        assertEquals("IBM", CandleSymbol.valueOf("IBM", CandleExchange.COMPOSITE).toString());

        assertEquals("IBM{=d}", CandleSymbol.valueOf("IBM", CandlePeriod.DAY).toString());
        assertEquals("IBM", CandleSymbol.valueOf("IBM", CandlePeriod.TICK).toString());

        assertEquals("IBM{price=ask}", CandleSymbol.valueOf("IBM", CandlePrice.ASK).toString());
        assertEquals("IBM", CandleSymbol.valueOf("IBM", CandlePrice.LAST).toString());

        assertEquals("IBM{tho=true}", CandleSymbol.valueOf("IBM", CandleSession.REGULAR).toString());
        assertEquals("IBM", CandleSymbol.valueOf("IBM", CandleSession.ANY).toString());

        assertEquals("EUR/USD{=2h,price=bid,source=bank}", CandleSymbol.valueOf(
            "EUR/USD{source=bank}", CandlePrice.BID, CandlePeriod.valueOf(2, CandleType.HOUR)).toString());
        assertEquals("IBM{=15m,aa=zz,price=bid}", CandleSymbol.valueOf(
            "IBM{aa=zz,price=b}", CandlePeriod.valueOf(15, CandleType.MINUTE)).toString());

        assertEquals("IBM{dt=source}", CandleSymbol.valueOf("IBM", CandleDataType.SOURCE).toString());
        assertEquals("IBM{dt=tho}", CandleSymbol.valueOf("IBM", CandleDataType.THO).toString());
        assertEquals("IBM{dt=all}", CandleSymbol.valueOf("IBM", CandleDataType.ALL).toString());
        assertEquals("IBM{dt=rth}", CandleSymbol.valueOf("IBM", CandleDataType.RTH).toString());
        assertEquals("IBM{dt=eth}", CandleSymbol.valueOf("IBM", CandleDataType.ETH).toString());
        assertEquals("IBM", CandleSymbol.valueOf("IBM", CandleDataType.UNDEFINED).toString());

        assertEquals("IBM{=15m,price=bid}", CandleSymbol.valueOf("IBM{=15m,dt=,price=bid}").toString());
        assertEquals("IBM{=15m,price=bid}",
            CandleSymbol.valueOf("IBM{=15m,dt=all,price=bid}", CandleDataType.UNDEFINED).toString());
        assertEquals("IBM{=15m,dt=source,price=bid}",
            CandleSymbol.valueOf("IBM{=15m,dt=all,price=bid}", CandleDataType.SOURCE).toString());
    }

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
            CandlePrice price = randomEnum(rnd, CandlePrice.values());
            CandleDataType dataType = CandleDataType.parse(randomDataType(rnd));
            CandleSymbol symbol0 = CandleSymbol.valueOf(baseSymbol, alignment, exchange, period, session, priceLevel,
                price, dataType);
            CandleSymbol symbol1 = CandleSymbol.valueOf(symbol0.toString());
            assertEquals(baseSymbol, symbol1.getBaseSymbol());
            assertEquals(alignment, symbol1.getAlignment());
            assertEquals(exchange, symbol1.getExchange());
            assertEquals(period, symbol1.getPeriod());
            assertEquals(session, symbol1.getSession());
            assertEquals(priceLevel, symbol1.getPriceLevel());
            assertEquals(price, symbol1.getPrice());
            assertEquals(dataType, symbol1.getDataType());
            assertEquals(symbol0, symbol1);
        }
    }

    private <T extends Enum<T>> T randomEnum(Random rnd, T[] values) {
        return values[rnd.nextInt(values.length)];
    }

    private String randomSymbol(Random rnd) {
        int len = rnd.nextInt(5) + 1;
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(randomChar(rnd));
        }
        return sb.toString();
    }

    private char randomChar(Random rnd) {
        return (char) ('A' + rnd.nextInt('Z' - 'A' + 1));
    }

    private char randomExchange(Random rnd) {
        return MarketEventSymbols.SUPPORTED_EXCHANGES.charAt(
            rnd.nextInt(MarketEventSymbols.SUPPORTED_EXCHANGES.length()));
    }

    private String randomDataType(Random rnd) {
        int i = rnd.nextInt(DATA_TYPES.length + 1);
        return i == DATA_TYPES.length ? randomSymbol(rnd) : DATA_TYPES[i].toString();
    }
}
