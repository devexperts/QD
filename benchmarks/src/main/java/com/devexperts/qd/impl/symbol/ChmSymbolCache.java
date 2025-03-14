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

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

// SymbolCache incomplete implementation based on ConcurrentHashMap
@SuppressWarnings("unused")
public class ChmSymbolCache extends ConcurrentHashMap<String, SymbolCacheSet.Entry>
    implements BenchmarkSymbolCache
{
    private final Clock clock;
    private final long ttl;

    public ChmSymbolCache(int initialCapacity, Clock clock, long ttl) {
        super(initialCapacity);
        this.clock = clock;
        this.ttl = ttl;
    }

    private SymbolCacheSet.Entry createEntry(String symbol) {
        return SymbolCacheSet.Entry.createWithTime(symbol, clock);
    }

    public String resolveKey(char[] chars) {
        // Generating short-lived garbage strings just for lookup
        String symbol = SymbolCache.stringFromKey(chars);
        return resolve(symbol);
    }

    public String resolve(String symbol) {
        SymbolCacheSet.Entry entry = get(symbol);
        if (entry != null && entry.updateTime(clock))
            return entry.symbol;

        // Incorrect implementation (could extract REMOVED entry)
        return computeIfAbsent(symbol, this::createEntry).symbol;
    }

    public String getIfPresent(String symbol) {
        SymbolCacheSet.Entry entry = get(symbol);
        return (entry != null) ? entry.symbol : symbol;
    }

    public String resolveAndAcquire(String symbol) {
        SymbolCacheSet.Entry entry = get(symbol);
        if (entry != null && entry.incrementCount())
            return entry.symbol;

        // Correct implementation dealing with REMOVED entry
        while (true) {
            entry = computeIfAbsent(symbol, this::createEntry);
            if (entry.incrementCount()) {
                return entry.symbol;
            } else {
                if (replace(symbol, entry, SymbolCacheSet.Entry.createWithCounter(symbol)))
                    return entry.symbol;
            }
        }
    }

    public void release(String symbol) {
        SymbolCacheSet.Entry entry = get(symbol);
        if (entry != null && entry.decrementCount(clock))
            return;
        throw new IllegalStateException("Illegal reference count for " + symbol);
    }

    public void cleanUp() {
        long expireTime = clock.millis() - ttl;
        for (SymbolCacheSet.Entry entry : values()) {
            entry.expireIfNeeded(expireTime);
            if (entry.isRemoved()) {
                remove(entry.symbol, entry);
            }
        }
    }
}
