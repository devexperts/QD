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
package com.devexperts.qd.test;

import com.devexperts.qd.util.SymbolObjectMap;
import com.devexperts.qd.util.SymbolObjectVisitor;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SymbolObjectMapTest {

    @Test
    public void testSimple() {
        // note: it does not test rehashing
        SymbolObjectMap map = SymbolObjectMap.createInstance();

        assertNull(map.getSymbol(new char[] {'h', 'a', 'b', 'a'}, 0, 4));
        assertNull(map.getSymbol(new char[] {'q'}, 0, 1));

        assertFalse(map.contains(0, "haba"));
        assertFalse(map.contains(-1, null));
        assertFalse(map.contains(-2, null));
        assertFalse(map.contains(-3, null));

        assertNull(map.put(0, "haba", new Integer(0)));
        assertNull(map.put(-1, null, new Integer(1)));
        assertNull(map.put(-2, null, new Integer(2)));

        assertEquals(map.getSymbol(new char[] {'h', 'a', 'b', 'a'}, 0, 4), "haba");
        assertNull(map.getSymbol(new char[] {'q'}, 0, 1));

        assertTrue(map.contains(0, "haba"));
        assertTrue(map.contains(-1, null));
        assertTrue(map.contains(-2, null));
        assertFalse(map.contains(-3, null));

        assertEquals(map.size(), 3);

        assertEquals(map.get(0, "haba"), new Integer(0));
        assertEquals(map.get(-1, null), new Integer(1));
        assertEquals(map.get(-2, null), new Integer(2));
        assertNull(map.get(-3, null));

        assertEquals(map.put(0, "haba", new Integer(10)), new Integer(0));
        assertEquals(map.put(-1, null, new Integer(11)), new Integer(1));
        assertEquals(map.put(-2, null, new Integer(12)), new Integer(2));

        assertEquals(map.size(), 3);

        assertEquals(map.get(0, "haba"), new Integer(10));
        assertEquals(map.get(-1, null), new Integer(11));
        assertEquals(map.get(-2, null), new Integer(12));
        assertNull(map.get(-3, null));

        assertEquals(map.remove(0, "haba"), new Integer(10));
        assertEquals(map.remove(-2, null), new Integer(12));

        assertEquals(map.size(), 1);

        assertNull(map.get(0, "haba"));
        assertEquals(map.get(-1, null), new Integer(11));
        assertNull(map.get(-2, null));
        assertNull(map.get(-3, null));

        assertNull(map.remove(-3, null));
        assertEquals(map.remove(-1, null), new Integer(11));

        assertEquals(map.size(), 0);

        assertNull(map.get(0, "haba"));
        assertNull(map.get(-1, null));
        assertNull(map.get(-2, null));
        assertNull(map.get(-3, null));
    }

    @Test
    public void testChars() {
        // note: it does not test rehashing
        SymbolObjectMap map = SymbolObjectMap.createInstance();
        char[] chars = {0, 0, 'h', 'e', 'l', 'l', 'o', 0, 0};
        int offset = 2;
        int length = 5;
        String symbol = new String(chars, offset, length);

        assertNull(map.getSymbol(chars, offset, length));
        assertNull(map.getSymbol(new char[] {'q'}, 0, 1));

        assertFalse(map.contains(0, symbol));
        assertFalse(map.contains(0, chars, offset, length));
        assertFalse(map.contains(0, "haba"));
        assertFalse(map.contains(-3, null));

        assertNull(map.put(0, chars, offset, length, new Integer(0)));

        assertEquals(map.getSymbol(chars, offset, length), symbol);
        assertNull(map.getSymbol(new char[] {'q'}, 0, 1));

        assertTrue(map.contains(0, symbol));
        assertTrue(map.contains(0, chars, offset, length));
        assertFalse(map.contains(0, "haba"));
        assertFalse(map.contains(-3, null));

        assertEquals(map.size(), 1);

        assertEquals(map.get(0, symbol), new Integer(0));
        assertEquals(map.get(0, chars, offset, length), new Integer(0));
        assertNull(map.get(0, "haba"));
        assertNull(map.get(-3, null));

        assertEquals(map.put(0, chars, offset, length, new Integer(10)), new Integer(0));

        assertEquals(map.size(), 1);

        assertEquals(map.get(0, symbol), new Integer(10));
        assertEquals(map.get(0, chars, offset, length), new Integer(10));
        assertNull(map.get(0, "haba"));
        assertNull(map.get(-3, null));

        assertEquals(map.remove(0, chars, offset, length), new Integer(10));

        assertEquals(map.size(), 0);

        assertNull(map.get(0, symbol));
        assertNull(map.get(0, chars, offset, length));
        assertNull(map.get(0, "haba"));
        assertNull(map.get(-3, null));

        assertNull(map.remove(-3, null));

        assertEquals(map.size(), 0);

        assertNull(map.get(0, symbol));
        assertNull(map.get(0, chars, offset, length));
        assertNull(map.get(0, "haba"));
        assertNull(map.get(-3, null));
    }

    private static int CNT = 1000;

    @Test
    public void testComplex() {
        SymbolObjectMap map = SymbolObjectMap.createInstance();

        for (int i = 1; i <= CNT; i++) {
            assertNull(map.put(-i, null, new Integer(i)));
            assertEquals(map.size(), i);
        }

        final HashMap<Integer, Object> ciphersToValue = new HashMap<Integer, Object>();

        assertFalse(map.examineEntries(new SymbolObjectVisitor() {
            public boolean hasCapacity() {
                return true;
            }

            public void visitEntry(int cipher, String symbol, Object value) {
                assertNull(ciphersToValue.put(new Integer(cipher), value));
                assertNull(null, symbol);
            }
        }));

        assertEquals(ciphersToValue.size(), CNT);
        for (int i = 1; i <= CNT; i++) {
            assertEquals(new Integer(i), ciphersToValue.get(new Integer(-i)));
        }

        for (int i = 1; i <= CNT; i++)
            assertEquals(map.get(-i, null), new Integer(i));

        for (int i = 1; i <= CNT; i++) {
            assertEquals(map.remove(-i, null), new Integer(i));
            assertEquals(map.size(), CNT - i);
        }
    }

    @Test
    public void testComplexMapped() {
        SymbolObjectMap map = SymbolObjectMap.createInstance();

        for (int i = 1; i <= CNT; i++) {
            assertNull(map.put(0, Integer.toString(i), new Integer(i)));
            assertEquals(map.size(), i);
        }

        final HashMap<String, Object> symbolsToValue = new HashMap<>();

        assertFalse(map.examineEntries(new SymbolObjectVisitor() {
            public boolean hasCapacity() {
                return true;
            }

            public void visitEntry(int cipher, String symbol, Object value) {
                assertEquals(0, cipher);
                assertNull(symbolsToValue.put(symbol, value));
            }
        }));

        assertEquals(symbolsToValue.size(), CNT);
        for (int i = 1; i <= CNT; i++) {
            assertEquals(new Integer(i), symbolsToValue.get(Integer.toString(i)));
        }

        for (int i = 1; i <= CNT; i++)
            assertEquals(map.get(0, Integer.toString(i)), new Integer(i));

        for (int i = 1; i <= CNT; i++) {
            assertEquals(map.remove(0, Integer.toString(i)), new Integer(i));
            assertEquals(map.size(), CNT - i);
        }
    }

    @Test
    public void testConcurrentMappedVisiting() {
        final SymbolObjectMap map = SymbolObjectMap.createInstance();
        HashSet<String> expect = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            String s = "L" + i;
            map.put(0, s, null);
            expect.add(s);
        }
        final HashSet<String> actual = new HashSet<String>();
        map.examineEntries(new SymbolObjectVisitor() {
            public boolean hasCapacity() {
                return true;
            }

            public void visitEntry(int cipher, String symbol, Object value) {
                if (actual.size() % 10 == 0) {
                    // concurrent modification
                    for (int i = 0; i < 100; i++)
                        map.put(0, "L" + actual.size() + "-" + i, null);
                }
                assertEquals(0, cipher);
                assertNotNull(symbol);
                assertTrue(actual.add(symbol));
            }
        });
        assertTrue("all visited", actual.containsAll(expect));
    }
}
