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
package com.devexperts.qd.impl.symbol;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SymbolCacheTest {

    public static final String SYMBOL = "ABCD";

    @Test
    public void testCacheBuilder() {
        assertNotNull(SymbolCache.newBuilder()
            .withTtl(10_000)
            .withSharding(4)
            .build()
        );

        assertThrows(NullPointerException.class, () -> SymbolCache.newBuilder().withClock(null).build());
        assertThrows(IllegalArgumentException.class, () -> SymbolCache.newBuilder().withInitialCapacity(-1).build());
        assertThrows(IllegalArgumentException.class, () -> SymbolCache.newBuilder().withTtl(-1).build());
        assertThrows(IllegalArgumentException.class, () -> SymbolCache.newBuilder().withSharding(-1).build());
    }

    @Test
    public void testEmbedKey() {
        String symbol = "SYMBOL";
        char[] chars = toKey(symbol);

        assertTrue(SymbolCache.isKey(chars, SymbolCache.KEY_HEADER_SIZE));
        assertEquals(SymbolCache.KEY_MAGIC0, chars[0]);
        assertEquals(SymbolCache.KEY_MAGIC1, chars[1]);
        assertEquals(symbol.length(), SymbolCache.lengthFromKey(chars));
    }

    @Test
    public void testGetByString() {
        SymbolCache cache = SymbolCache.newBuilder().build();

        String result = cache.resolve(SYMBOL);
        assertEquals(SYMBOL, result);
        assertSame(result, cache.getIfPresent(SYMBOL));

        assertNull(cache.getIfPresent("BCDF"));
    }

    @Test
    public void testGetByChars() {
        SymbolCache cache = SymbolCache.newBuilder().build();

        String result = cache.resolveKey(toKey(SYMBOL));
        assertEquals(SYMBOL, result);
        assertSame(result, cache.getIfPresent(SYMBOL));
    }

    @Test
    public void testResolveTtl() {
        SymbolCache cache = SymbolCache.newBuilder().withTtl(0).build();

        assertSame(SYMBOL, cache.resolve(SYMBOL));
        assertSame(SYMBOL, cache.getIfPresent(SYMBOL));

        cache.cleanUp();
        assertNull(cache.getIfPresent(SYMBOL));
    }

    @Test
    public void testAcquireReleaseTtl() {
        SymbolCache cache = SymbolCache.newBuilder().withTtl(0).build();

        assertSame(SYMBOL, cache.resolveAndAcquire(SYMBOL));
        assertSame(SYMBOL, cache.getIfPresent(SYMBOL));
        assertSame(SYMBOL, cache.resolve(new String(SYMBOL.toCharArray())));

        cache.release(SYMBOL);
        assertSame(SYMBOL, cache.getIfPresent(SYMBOL));

        cache.cleanUp();
        assertNull(cache.getIfPresent(SYMBOL));
    }

    private static char[] toKey(String symbol) {
        int length = symbol.length();
        char[] key = new char[length + SymbolCache.KEY_HEADER_SIZE];
        System.arraycopy(symbol.toCharArray(), 0, key, SymbolCache.KEY_HEADER_SIZE, length);
        return SymbolCache.embedKey(key, SymbolCache.KEY_HEADER_SIZE, length);
    }
}
