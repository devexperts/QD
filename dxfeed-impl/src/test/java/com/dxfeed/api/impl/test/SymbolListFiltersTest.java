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
package com.dxfeed.api.impl.test;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.kit.FilterSyntaxException;
import com.devexperts.qd.kit.SymbolSetFilter;
import com.devexperts.qd.util.SymbolSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SymbolListFiltersTest {
    private static final DataScheme SCHEME = QDFactory.getDefaultScheme();

    @Test
    public void testSymbolList() {
        assertSymbolList("IBM", "IBM");
        assertNotSymbolList("IBM*");
        assertSymbolList("IBM,MSFT", "IBM", "MSFT");
        assertSymbolList("IBM,MSFT,HABAHABA", "IBM", "MSFT", "HABAHABA");
        assertSymbolList("[ABC]", "A", "B", "C");
        assertSymbolList("(A,B,C,D,E)&(B,D,E)&(A,B,C,D)", "B", "D");
        assertSymbolList("AAPL{=15m,price=bid},GOOG{=1h,price=ask}", "AAPL{=15m,price=bid}", "GOOG{=1h,price=ask}");
        assertSymbolList("optsymbol&(IBM,.IBM)", ".IBM");
        assertSymbolList("!optsymbol&(IBM,.IBM)", "IBM");
        assertSymbolList("opt&(IBM,.IBM,EUR/USD,/CL,/CL9H)", ".IBM");
        assertSymbolList("!opt&(IBM,.IBM,EUR/USD,/CL,/CL9H)", "IBM", "EUR/USD", "/CL", "/CL9H");
        assertSymbolList("fut&(IBM,.IBM,EUR/USD,/CL,/CL9H)", "/CL", "/CL9H");
        assertSymbolList("!fut&(IBM,.IBM,EUR/USD,/CL)", "IBM", ".IBM", "EUR/USD");
        assertSymbolList("prod&(IBM,.IBM,EUR/USD,/CL,/CL9H)", "/CL");
        assertSymbolList("!prod&(IBM,.IBM,EUR/USD,/CL,/CL9H)", "IBM", ".IBM", "EUR/USD", "/CL9H");
        assertSymbolList("fx&(IBM,.IBM,EUR/USD,/CL)", "EUR/USD");
        assertSymbolList("!fx&(IBM,.IBM,EUR/USD,/CL)", "IBM", ".IBM", "/CL");
        assertSymbolList("bs&(IBM,.IBM,EUR/USD,/CL,/CL9H)", "IBM", "EUR/USD");
        assertSymbolList("!bs&(IBM,.IBM,EUR/USD,/CL,/CL9H)", ".IBM", "/CL", "/CL9H");
        assertSymbolList("cs&(IBM,.IBM,EUR/USD,/CL,/CL9H)", "IBM", "EUR/USD", "/CL", "/CL9H");
        assertSymbolList("!cs&(IBM,.IBM,EUR/USD,/CL,/CL9H)", ".IBM");
        assertNotSymbolList("opt");
        assertNotSymbolList("fut");
        assertNotSymbolList("fx");
        assertNotSymbolList("bs");
        assertNotSymbolList("cs");
        // test various quoting approaches (see QD-493)
        assertSymbolList("IBM;fold[ ]i=1[ ]to[ ]2[ ]with[ ]a[ ]do[ ]a;1m", "IBM;fold i=1 to 2 with a do a;1m");
        assertSymbolList("IBM;fold\\ i=1\\ to\\ 2\\ with\\ a\\ do\\ a;1m", "IBM;fold i=1 to 2 with a do a;1m");
        assertSymbolList("\\QIBM;fold i=1 to 2 with a do a;1m\\E", "IBM;fold i=1 to 2 with a do a;1m");
    }

    private void assertSymbolList(String spec, String... symbols) {
        SymbolSetFilter filter = SymbolSetFilter.valueOf(spec, SCHEME);
        assertEquals(QDFilter.Kind.SYMBOL_SET, filter.getKind());
        SymbolSet set = filter.getSymbolSet();
        assertNotNull(set);
        assertEquals(symbols.length, set.size());
        for (String symbol : symbols)
            assertTrue(symbol, set.contains(SCHEME.getCodec().encode(symbol), symbol));
    }

    private void assertNotSymbolList(String spec) {
        assertThrows("Should not be supported: " + spec, FilterSyntaxException.class,
            () -> SymbolSetFilter.valueOf(spec, SCHEME));
    }

    @Test
    public void testToString() {
        checkToString("IBM", "+IBM", "-MSFT", "-GOOG", "-TEST");
        checkToString("IBM,MSFT", "+IBM", "+MSFT", "-GOOG", "-TEST");
        checkToString("IBM,MSFT,GOOG", "+IBM", "+MSFT", "+GOOG", "-TEST");
        checkToString("IBM,MSFT,!GOOG", "+IBM", "+MSFT", "-GOOG", "+TEST");
        checkToString("IBM&MSFT,GOOG", "-IBM", "-MSFT", "+GOOG", "-TEST");
        checkToString("!IBM", "-IBM", "+MSFT", "+GOOG", "+TEST");
        // proper parenthesis around comma-separated symbol lists
        checkToString("!(IBM,MSFT)", "-IBM", "-MSFT", "+GOOG", "+TEST");
        checkToString("!GOOG&(IBM,MSFT,GOOG)", "+IBM", "+MSFT", "-GOOG", "-TEST");
    }

    private void checkToString(String spec, String... testSymbols) {
        QDFilter one = CompositeFilters.valueOf(spec, SCHEME);
        QDFilter two = CompositeFilters.valueOf(one.toString(), SCHEME);
        SymbolCodec code = SCHEME.getCodec();
        DataRecord record = SCHEME.getRecord(0);
        for (String symbolSpec : testSymbols) {
            boolean accepts;
            switch (symbolSpec.charAt(0)) {
                case '+': accepts = true; break;
                case '-': accepts = false; break;
                default: throw new AssertionError();
            }
            String symbol = symbolSpec.substring(1);
            int cipher = code.encode(symbol);
            assertEquals(accepts, one.accept(null, record, cipher, symbol));
            assertEquals(accepts, two.accept(null, record, cipher, symbol));
        }
    }
}
