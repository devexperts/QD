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

import com.dxfeed.event.market.MarketEventSymbols;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;


public class MarketEventSymbolsTest {

    @Test
    public void testNull() {
        assertNull(MarketEventSymbols.getBaseSymbol(null));
        assertEquals("SPX", MarketEventSymbols.changeBaseSymbol(null, "SPX"));
        assertEquals(false, MarketEventSymbols.hasExchangeCode(null));
        assertEquals('\0', MarketEventSymbols.getExchangeCode(null));
        assertNull(MarketEventSymbols.changeExchangeCode(null, '\0'));
        assertEquals("&D", MarketEventSymbols.changeExchangeCode(null, 'D'));

        assertEquals(null, MarketEventSymbols.getAttributeStringByKey(null, "p"));
        assertEquals(null, MarketEventSymbols.removeAttributeStringByKey(null, "p"));
        assertEquals(null, MarketEventSymbols.changeAttributeStringByKey(null, "p", null));
        assertEquals("{p=DAY}", MarketEventSymbols.changeAttributeStringByKey(null, "p", "DAY"));

        assertEquals(null, MarketEventSymbols.getAttributeStringByKey(null, ""));
        assertEquals(null, MarketEventSymbols.removeAttributeStringByKey(null, ""));
        assertEquals(null, MarketEventSymbols.changeAttributeStringByKey(null, "", null));
        assertEquals("{=}", MarketEventSymbols.changeAttributeStringByKey(null, "", ""));
    }

    @Test
    public void testEmpty() {
        checkNonAttributedSymbol("");
    }

    @Test
    public void testRegular() {
        checkNonAttributedSymbol("IBM");
    }

    @Test
    public void testBrokenSymbol() {
        checkNonAttributedSymbol("{");
        checkNonAttributedSymbol("}");
        checkNonAttributedSymbol("{}");
        checkNonAttributedSymbol("A{");
        checkNonAttributedSymbol("A}");
        checkNonAttributedSymbol("A{}");
        checkNonAttributedSymbol("{B");
        checkNonAttributedSymbol("}B");
        checkNonAttributedSymbol("{}B");
    }

    private void checkNonAttributedSymbol(String s) {
        assertEquals(s, MarketEventSymbols.getBaseSymbol(s));
        assertEquals("MSFT", MarketEventSymbols.changeBaseSymbol(s, "MSFT"));
        assertEquals(false, MarketEventSymbols.hasExchangeCode(s));
        assertEquals('\0', MarketEventSymbols.getExchangeCode(s));
        assertEquals(s, MarketEventSymbols.changeExchangeCode(s, '\0'));
        assertEquals(s + "&C", MarketEventSymbols.changeExchangeCode(s, 'C'));

        assertEquals(null, MarketEventSymbols.getAttributeStringByKey(s, "key"));
        assertEquals(s, MarketEventSymbols.removeAttributeStringByKey(s, "key"));
        assertEquals(s, MarketEventSymbols.changeAttributeStringByKey(s, "key", null));
        assertEquals(s + "{key=val}", MarketEventSymbols.changeAttributeStringByKey(s, "key", "val"));

        assertEquals(null, MarketEventSymbols.getAttributeStringByKey(s, ""));
        assertEquals(s, MarketEventSymbols.removeAttributeStringByKey(s, ""));
        assertEquals(s, MarketEventSymbols.changeAttributeStringByKey(s, "", null));
        assertEquals(s + "{=val}", MarketEventSymbols.changeAttributeStringByKey(s, "", "val"));
        assertEquals(s + "{=}", MarketEventSymbols.changeAttributeStringByKey(s, "", ""));
    }

    @Test
    public void testRegional() {
        String s = "GE&N";
        assertEquals("GE", MarketEventSymbols.getBaseSymbol(s));
        assertEquals("F&N", MarketEventSymbols.changeBaseSymbol(s, "F"));
        assertEquals(true, MarketEventSymbols.hasExchangeCode(s));
        assertEquals('N', MarketEventSymbols.getExchangeCode(s));
        assertEquals("GE", MarketEventSymbols.changeExchangeCode(s, '\0'));
        assertEquals("GE&Q", MarketEventSymbols.changeExchangeCode(s, 'Q'));

        assertEquals(null, MarketEventSymbols.getAttributeStringByKey(s, "tho"));
        assertEquals(s, MarketEventSymbols.removeAttributeStringByKey(s, "tho"));
        assertEquals(s, MarketEventSymbols.changeAttributeStringByKey(s, "tho", null));
        assertEquals("GE&N{tho=true}", MarketEventSymbols.changeAttributeStringByKey(s, "tho", "true"));

        assertEquals(null, MarketEventSymbols.getAttributeStringByKey(s, ""));
        assertEquals(s, MarketEventSymbols.removeAttributeStringByKey(s, ""));
        assertEquals(s, MarketEventSymbols.changeAttributeStringByKey(s, "", null));
        assertEquals("GE&N{=yes}", MarketEventSymbols.changeAttributeStringByKey(s, "", "yes"));
        assertEquals("GE&N{=}", MarketEventSymbols.changeAttributeStringByKey(s, "", ""));
    }

    @Test
    public void testOneAttr() {
        String s = "/ES{tho=true}";
        assertEquals("/ES", MarketEventSymbols.getBaseSymbol(s));
        assertEquals("/NQ{tho=true}", MarketEventSymbols.changeBaseSymbol(s, "/NQ"));
        assertEquals(false, MarketEventSymbols.hasExchangeCode(s));
        assertEquals('\0', MarketEventSymbols.getExchangeCode(s));
        assertEquals(s, MarketEventSymbols.changeExchangeCode(s, '\0'));
        assertEquals("/ES&G{tho=true}", MarketEventSymbols.changeExchangeCode(s, 'G'));

        assertEquals("true", MarketEventSymbols.getAttributeStringByKey(s, "tho"));
        assertEquals("/ES", MarketEventSymbols.removeAttributeStringByKey(s, "tho"));
        assertEquals("/ES", MarketEventSymbols.changeAttributeStringByKey(s, "tho", null));
        assertEquals("/ES{tho=false}", MarketEventSymbols.changeAttributeStringByKey(s, "tho", "false"));

        assertEquals(null, MarketEventSymbols.getAttributeStringByKey(s, "t"));
        assertEquals(s, MarketEventSymbols.removeAttributeStringByKey(s, "t"));
        assertEquals(s, MarketEventSymbols.changeAttributeStringByKey(s, "t", null));
        assertEquals("/ES{t=MIN,tho=true}", MarketEventSymbols.changeAttributeStringByKey(s, "t", "MIN"));

        assertEquals(null, MarketEventSymbols.getAttributeStringByKey(s, "zap"));
        assertEquals(s, MarketEventSymbols.removeAttributeStringByKey(s, "zap"));
        assertEquals(s, MarketEventSymbols.changeAttributeStringByKey(s, "zap", null));
        assertEquals("/ES{tho=true,zap=15}", MarketEventSymbols.changeAttributeStringByKey(s, "zap", "15"));
        assertEquals("/ES{tho=true,zap=}", MarketEventSymbols.changeAttributeStringByKey(s, "zap", ""));

        assertEquals(null, MarketEventSymbols.getAttributeStringByKey(s, ""));
        assertEquals(s, MarketEventSymbols.removeAttributeStringByKey(s, ""));
        assertEquals(s, MarketEventSymbols.changeAttributeStringByKey(s, "", null));
        assertEquals("/ES{=code,tho=true}", MarketEventSymbols.changeAttributeStringByKey(s, "", "code"));
        assertEquals("/ES{=,tho=true}", MarketEventSymbols.changeAttributeStringByKey(s, "", ""));
    }

    @Test
    public void testTwoAttrs() {
        String s = "A{c=1,e=3}";
        assertEquals("A", MarketEventSymbols.getBaseSymbol(s));
        assertEquals("B{c=1,e=3}", MarketEventSymbols.changeBaseSymbol(s, "B"));
        assertEquals(false, MarketEventSymbols.hasExchangeCode(s));
        assertEquals('\0', MarketEventSymbols.getExchangeCode(s));
        assertEquals(s, MarketEventSymbols.changeExchangeCode(s, '\0'));
        assertEquals("A&D{c=1,e=3}", MarketEventSymbols.changeExchangeCode(s, 'D'));

        assertEquals(null, MarketEventSymbols.getAttributeStringByKey(s, "b"));
        assertEquals(s, MarketEventSymbols.removeAttributeStringByKey(s, "b"));
        assertEquals(s, MarketEventSymbols.changeAttributeStringByKey(s, "b", null));
        assertEquals("A{b=2,c=1,e=3}", MarketEventSymbols.changeAttributeStringByKey(s, "b", "2"));
        assertEquals("A{b=,c=1,e=3}", MarketEventSymbols.changeAttributeStringByKey(s, "b", ""));

        assertEquals("1", MarketEventSymbols.getAttributeStringByKey(s, "c"));
        assertEquals("A{e=3}", MarketEventSymbols.removeAttributeStringByKey(s, "c"));
        assertEquals("A{e=3}", MarketEventSymbols.changeAttributeStringByKey(s, "c", null));
        assertEquals("A{c=2,e=3}", MarketEventSymbols.changeAttributeStringByKey(s, "c", "2"));
        assertEquals("A{c=,e=3}", MarketEventSymbols.changeAttributeStringByKey(s, "c", ""));

        assertEquals(null, MarketEventSymbols.getAttributeStringByKey(s, "d"));
        assertEquals(s, MarketEventSymbols.removeAttributeStringByKey(s, "d"));
        assertEquals(s, MarketEventSymbols.changeAttributeStringByKey(s, "d", null));
        assertEquals("A{c=1,d=4,e=3}", MarketEventSymbols.changeAttributeStringByKey(s, "d", "4"));
        assertEquals("A{c=1,d=,e=3}", MarketEventSymbols.changeAttributeStringByKey(s, "d", ""));

        assertEquals("3", MarketEventSymbols.getAttributeStringByKey(s, "e"));
        assertEquals("A{c=1}", MarketEventSymbols.removeAttributeStringByKey(s, "e"));
        assertEquals("A{c=1}", MarketEventSymbols.changeAttributeStringByKey(s, "e", null));
        assertEquals("A{c=1,e=0}", MarketEventSymbols.changeAttributeStringByKey(s, "e", "0"));
        assertEquals("A{c=1,e=}", MarketEventSymbols.changeAttributeStringByKey(s, "e", ""));

        assertEquals(null, MarketEventSymbols.getAttributeStringByKey(s, "t"));
        assertEquals(s, MarketEventSymbols.removeAttributeStringByKey(s, "t"));
        assertEquals(s, MarketEventSymbols.changeAttributeStringByKey(s, "t", null));
        assertEquals("A{c=1,e=3,t=5}", MarketEventSymbols.changeAttributeStringByKey(s, "t", "5"));
        assertEquals("A{c=1,e=3,t=}", MarketEventSymbols.changeAttributeStringByKey(s, "t", ""));

        assertEquals(null, MarketEventSymbols.getAttributeStringByKey(s, ""));
        assertEquals(s, MarketEventSymbols.removeAttributeStringByKey(s, ""));
        assertEquals(s, MarketEventSymbols.changeAttributeStringByKey(s, "", null));
        assertEquals("A{=-1,c=1,e=3}", MarketEventSymbols.changeAttributeStringByKey(s, "", "-1"));
        assertEquals("A{=,c=1,e=3}", MarketEventSymbols.changeAttributeStringByKey(s, "", ""));
    }

    @Test
    public void testSpreadSymbol() {
        assertEquals("", MarketEventSymbols.buildSpreadSymbol(Collections.<String, Double>emptyMap()));
        assertEquals("", MarketEventSymbols.buildSpreadSymbol(spread("A:0")));
        assertEquals("A", MarketEventSymbols.buildSpreadSymbol(spread("A:1")));
        assertEquals("=-A", MarketEventSymbols.buildSpreadSymbol(spread("A:-1")));
        assertEquals("=2*A", MarketEventSymbols.buildSpreadSymbol(spread("A:2")));
        assertEquals("=-2*A", MarketEventSymbols.buildSpreadSymbol(spread("A:-2")));
        assertEquals("=0.5*A", MarketEventSymbols.buildSpreadSymbol(spread("A:0.5")));
        assertEquals("=-0.5*A", MarketEventSymbols.buildSpreadSymbol(spread("A:-0.5")));

        assertEquals("=A+B", MarketEventSymbols.buildSpreadSymbol(spread("A:1,B:1")));
        assertEquals("=A-B", MarketEventSymbols.buildSpreadSymbol(spread("A:1,B:-1")));
        assertEquals("=B-A", MarketEventSymbols.buildSpreadSymbol(spread("A:-1,B:1")));
        assertEquals("=-B-A", MarketEventSymbols.buildSpreadSymbol(spread("A:-1,B:-1")));
        assertEquals("=2*A+B", MarketEventSymbols.buildSpreadSymbol(spread("A:2,B:1")));
        assertEquals("=2*B+A", MarketEventSymbols.buildSpreadSymbol(spread("A:1,B:2")));
        assertEquals("=A-2*B", MarketEventSymbols.buildSpreadSymbol(spread("A:1,B:-2,C:0")));
        assertEquals("=4*D+4*H+3*C+3*G+2*B+2*F+A+E+MarketEventSymbolsTest+0.5*P+0.5*Q-0.5*L-0.5*K-1.5*J-1.5*I",
            MarketEventSymbols.buildSpreadSymbol(spread("A:1,B:2,C:3,D:4,E:1,F:2,G:3,H:4,I:-1.5,J:-1.5,K:-0.5,L:-0.5,M:0,N:0,P:0.5,Q:0.5,MarketEventSymbolsTest:1")));
    }

    private Map<String, Double> spread(String s) {
        Map<String, Double> m = new HashMap<String, Double>();
        for (String leg : s.split(","))
            m.put(leg.split(":")[0], Double.parseDouble(leg.split(":")[1]));
        return m;
    }
}
