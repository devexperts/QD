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
package com.devexperts.qd.util.test;

import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.util.SymbolSet;
import org.junit.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SymbolSetTest {
    private final SymbolSet set = SymbolSet.createInstance();

    private static final String[] STRINGS = { "A", "B", "C", "ZZZ", "UNBREAKABLE", "NOT HERE" };

    @Test
    public void testSymbolSet() {
        assertSet();
        assertTrue(add("A"));
        assertSet("A");
        assertFalse(add("A"));
        assertSet("A");
        assertTrue(add("B"));
        assertSet("A", "B");
        assertTrue(remove("A"));
        assertSet("B");
        assertFalse(remove("A"));
        assertSet("B");
        assertTrue(remove("B"));
        assertSet();
        assertTrue(add("UNBREAKABLE"));
        assertSet("UNBREAKABLE");
        assertTrue(add("C"));
        assertSet("UNBREAKABLE", "C");
        assertTrue(remove("UNBREAKABLE"));
        assertSet("C");
    }

    @Test
    public void testSymbolSetStress() {
        Random rnd = new Random(20140915);
        int n = 1000;
        // add
        Set<String> symbols = new HashSet<>();
        for (int i = 0; i < n; i++) {
            String symbol;
            do {
                symbol = Integer.toString(rnd.nextInt(10 * n));
            } while (!symbols.add(symbol));
            assertTrue(add(symbol));
        }
        assertSet(symbols.toArray(new String[0]));
        // remove random half
        for (Iterator<String> it = symbols.iterator(); it.hasNext(); ) {
            String symbol = it.next();
            if (rnd.nextBoolean()) {
                assertTrue(remove(symbol));
                it.remove();
            }
        }
        assertSet(symbols.toArray(new String[0]));
        // add more
        for (int i = 0; i < n; i++) {
            String symbol;
            do {
                symbol = Integer.toString(rnd.nextInt(10 * n));
            } while (!symbols.add(symbol));
            assertTrue(add(symbol));
        }
        assertSet(symbols.toArray(new String[symbols.size()]));
        // remove all
        for (String symbol : symbols) {
            assertTrue(remove(symbol));
        }
        assertSet();
    }

    private boolean add(String symbol) {
        return set.add(PentaCodec.INSTANCE.encode(symbol), symbol);
    }

    private boolean remove(String symbol) {
        return set.remove(PentaCodec.INSTANCE.encode(symbol), symbol);
    }

    private void assertSet(String... symbols) {
        checkSet(set, symbols);
        checkSet(set.unmodifiable(), symbols);
    }

    private void checkSet(SymbolSet set, String[] symbols) {
        final Set<String> examined = new HashSet<>();
        set.examine((cipher, symbol) -> assertTrue(examined.add(PentaCodec.INSTANCE.decode(cipher, symbol))));
        assertEquals(symbols.length, set.size());
        assertEquals(symbols.length, examined.size());
        for (String symbol : symbols) {
            assertTrue(examined.contains(symbol));
            int cipher = PentaCodec.INSTANCE.encode(symbol);
            assertTrue(set.contains(cipher, symbol));
            assertTrue(set.contains(cipher, symbol.toCharArray(), 0, symbol.length()));
            if (cipher == 0)
                assertEquals(symbol, set.getSymbol(symbol.toCharArray(), 0, symbol.length()));
        }
        // these symbols should not appear
        for (String symbol : STRINGS) {
            if (examined.contains(symbol))
                continue;
            int cipher = PentaCodec.INSTANCE.encode(symbol);
            assertFalse(set.contains(cipher, symbol));
            assertFalse(set.contains(cipher, symbol.toCharArray(), 0, symbol.length()));
        }
    }
}
