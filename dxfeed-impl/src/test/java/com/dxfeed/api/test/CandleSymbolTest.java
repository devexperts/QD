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

import com.dxfeed.event.candle.CandleExchange;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandlePrice;
import com.dxfeed.event.candle.CandleSession;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.candle.CandleType;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CandleSymbolTest {

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
    }
}
