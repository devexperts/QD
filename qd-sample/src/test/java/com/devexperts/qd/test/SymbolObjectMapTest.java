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
package com.devexperts.qd.test;

import com.devexperts.qd.util.SymbolObjectMap;
import com.devexperts.qd.util.SymbolObjectVisitor;
import junit.framework.TestCase;

import java.util.HashMap;
import java.util.HashSet;

public class SymbolObjectMapTest extends TestCase {
    public void testSimple() {
        // note: it does not test rehashing
        SymbolObjectMap map = SymbolObjectMap.createInstance();

        assertEquals(map.getSymbol(new char[] {'h', 'a', 'b', 'a'}, 0, 4), null);
        assertEquals(map.getSymbol(new char[] {'q'}, 0, 1), null);

        assertFalse(map.contains(0, "haba"));
        assertFalse(map.contains(-1, null));
        assertFalse(map.contains(-2, null));
        assertFalse(map.contains(-3, null));

        assertEquals(map.put(0, "haba", new Integer(0)), null);
        assertEquals(map.put(-1, null, new Integer(1)), null);
        assertEquals(map.put(-2, null, new Integer(2)), null);

        assertEquals(map.getSymbol(new char[] {'h', 'a', 'b', 'a'}, 0, 4), "haba");
        assertEquals(map.getSymbol(new char[] {'q'}, 0, 1), null);

        assertTrue(map.contains(0, "haba"));
        assertTrue(map.contains(-1, null));
        assertTrue(map.contains(-2, null));
        assertFalse(map.contains(-3, null));

        assertEquals(map.size(), 3);

        assertEquals(map.get(0, "haba"), new Integer(0));
        assertEquals(map.get(-1, null), new Integer(1));
        assertEquals(map.get(-2, null), new Integer(2));
        assertEquals(map.get(-3, null), null);

        assertEquals(map.put(0, "haba", new Integer(10)), new Integer(0));
        assertEquals(map.put(-1, null, new Integer(11)), new Integer(1));
        assertEquals(map.put(-2, null, new Integer(12)), new Integer(2));

        assertEquals(map.size(), 3);

        assertEquals(map.get(0, "haba"), new Integer(10));
        assertEquals(map.get(-1, null), new Integer(11));
        assertEquals(map.get(-2, null), new Integer(12));
        assertEquals(map.get(-3, null), null);

        assertEquals(map.remove(0, "haba"), new Integer(10));
        assertEquals(map.remove(-2, null), new Integer(12));

        assertEquals(map.size(), 1);

        assertEquals(map.get(0, "haba"), null);
        assertEquals(map.get(-1, null), new Integer(11));
        assertEquals(map.get(-2, null), null);
        assertEquals(map.get(-3, null), null);

        assertEquals(map.remove(-3, null), null);
        assertEquals(map.remove(-1, null), new Integer(11));

        assertEquals(map.size(), 0);

        assertEquals(map.get(0, "haba"), null);
        assertEquals(map.get(-1, null), null);
        assertEquals(map.get(-2, null), null);
        assertEquals(map.get(-3, null), null);
    }

    public void testChars() {
        // note: it does not test rehashing
        SymbolObjectMap map = SymbolObjectMap.createInstance();
        char[] chars = {0, 0, 'h', 'e', 'l', 'l', 'o', 0, 0};
        int offset = 2;
        int length = 5;
        String symbol = new String(chars, offset, length);

        assertEquals(map.getSymbol(chars, offset, length), null);
        assertEquals(map.getSymbol(new char[] {'q'}, 0, 1), null);

        assertFalse(map.contains(0, symbol));
        assertFalse(map.contains(0, chars, offset, length));
        assertFalse(map.contains(0, "haba"));
        assertFalse(map.contains(-3, null));

        assertEquals(map.put(0, chars, offset, length, new Integer(0)), null);

        assertEquals(map.getSymbol(chars, offset, length), symbol);
        assertEquals(map.getSymbol(new char[] {'q'}, 0, 1), null);

        assertTrue(map.contains(0, symbol));
        assertTrue(map.contains(0, chars, offset, length));
        assertFalse(map.contains(0, "haba"));
        assertFalse(map.contains(-3, null));

        assertEquals(map.size(), 1);

        assertEquals(map.get(0, symbol), new Integer(0));
        assertEquals(map.get(0, chars, offset, length), new Integer(0));
        assertEquals(map.get(0, "haba"), null);
        assertEquals(map.get(-3, null), null);

        assertEquals(map.put(0, chars, offset, length, new Integer(10)), new Integer(0));

        assertEquals(map.size(), 1);

        assertEquals(map.get(0, symbol), new Integer(10));
        assertEquals(map.get(0, chars, offset, length), new Integer(10));
        assertEquals(map.get(0, "haba"), null);
        assertEquals(map.get(-3, null), null);

        assertEquals(map.remove(0, chars, offset, length), new Integer(10));

        assertEquals(map.size(), 0);

        assertEquals(map.get(0, symbol), null);
        assertEquals(map.get(0, chars, offset, length), null);
        assertEquals(map.get(0, "haba"), null);
        assertEquals(map.get(-3, null), null);

        assertEquals(map.remove(-3, null), null);

        assertEquals(map.size(), 0);

        assertEquals(map.get(0, symbol), null);
        assertEquals(map.get(0, chars, offset, length), null);
        assertEquals(map.get(0, "haba"), null);
        assertEquals(map.get(-3, null), null);
    }

    private static int CNT = 1000;

    public void testComplex() {
        SymbolObjectMap map = SymbolObjectMap.createInstance();

        for (int i = 1; i <= CNT; i++) {
            assertEquals(map.put(-i, null, new Integer(i)), null);
            assertEquals(map.size(), i);
        }

        final HashMap<Integer, Object> ciphers_to_value = new HashMap<Integer, Object>();

        assertFalse(map.examineEntries(new SymbolObjectVisitor() {
            public boolean hasCapacity() {
                return true;
            }

            public void visitEntry(int cipher, String symbol, Object value) {
                assertNull(ciphers_to_value.put(new Integer(cipher), value));
                assertNull(null, symbol);
            }
        }));

        assertEquals(ciphers_to_value.size(), CNT);
        for (int i = 1; i <= CNT; i++) {
            assertEquals(new Integer(i), ciphers_to_value.get(new Integer(-i)));
        }

        for (int i = 1; i <= CNT; i++)
            assertEquals(map.get(-i, null), new Integer(i));

        for (int i = 1; i <= CNT; i++) {
            assertEquals(map.remove(-i, null), new Integer(i));
            assertEquals(map.size(), CNT - i);
        }
    }

    public void testComplexMapped() {
        SymbolObjectMap map = SymbolObjectMap.createInstance();

        for (int i = 1; i <= CNT; i++) {
            assertEquals(map.put(0, Integer.toString(i), new Integer(i)), null);
            assertEquals(map.size(), i);
        }

        final HashMap<String, Object> symbols_to_value = new HashMap<String, Object>();

        assertFalse(map.examineEntries(new SymbolObjectVisitor() {
            public boolean hasCapacity() {
                return true;
            }

            public void visitEntry(int cipher, String symbol, Object value) {
                assertEquals(0, cipher);
                assertNull(symbols_to_value.put(symbol, value));
            }
        }));

        assertEquals(symbols_to_value.size(), CNT);
        for (int i = 1; i <= CNT; i++) {
            assertEquals(new Integer(i), symbols_to_value.get(Integer.toString(i)));
        }

        for (int i = 1; i <= CNT; i++)
            assertEquals(map.get(0, Integer.toString(i)), new Integer(i));

        for (int i = 1; i <= CNT; i++) {
            assertEquals(map.remove(0, Integer.toString(i)), new Integer(i));
            assertEquals(map.size(), CNT - i);
        }
    }

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
