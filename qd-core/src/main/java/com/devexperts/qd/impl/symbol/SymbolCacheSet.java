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

import com.devexperts.annotation.Internal;
import com.devexperts.util.IndexerFunction;
import com.devexperts.util.SynchronizedIndexedSet;

import java.time.Clock;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Synchronized symbol set for {@link SymbolCache}.
 */
@Internal
class SymbolCacheSet extends SynchronizedIndexedSet<Object, SymbolCacheSet.Entry> {

    // Async methods run concurrently on CAS in entries
    // Sync methods represent slow path where we have to take a lock in order to update missing or removed entry

    public SymbolCacheSet(int initialCapacity) {
        super(INDEXER, initialCapacity);
    }

    public String resolveKey(char[] symbol, Clock clock) {
        Entry entry = getByKey(symbol);
        if (entry != null && entry.updateTime(clock)) {
            return entry.symbol;
        }
        // Sync method iff entry was null or removed
        return resolveKeySync(symbol, clock);
    }

    private synchronized String resolveKeySync(char[] symbol, Clock clock) {
        Entry oldEntry = getByKey(symbol);
        if (oldEntry != null && oldEntry.updateTime(clock)) {
            return oldEntry.symbol;
        }
        if (oldEntry != null) {
            removeValue(oldEntry);
        }
        Entry newEntry = Entry.createWithTime(symbol, clock);
        put(newEntry);
        return newEntry.symbol;
    }

    public String resolveString(String symbol, Clock clock) {
        Entry entry = getByKey(symbol);
        if (entry != null && entry.updateTime(clock)) {
            return entry.symbol;
        }
        // Sync method iff entry was null or removed
        return resolveStringSync(symbol, clock);
    }

    private synchronized String resolveStringSync(String symbol, Clock clock) {
        Entry oldEntry = getByKey(symbol);
        if (oldEntry != null && oldEntry.updateTime(clock)) {
            return oldEntry.symbol;
        }
        if (oldEntry != null) {
            removeValue(oldEntry);
        }
        Entry newEntry = Entry.createWithTime(symbol, clock);
        put(newEntry);
        return newEntry.symbol;
    }

    public String getIfPresent(String symbol) {
        Entry entry = getByKey(symbol);
        return (entry != null && !entry.isRemoved()) ? entry.symbol : null;
    }

    public String acquire(String symbol) {
        Entry entry = getByKey(symbol);
        if (entry != null && entry.incrementCount()) {
            return entry.symbol;
        }
        // Sync method iff entry was null or removed
        return acquireSync(symbol);
    }

    private synchronized String acquireSync(String symbol) {
        Entry oldEntry = getByKey(symbol);
        if (oldEntry != null && oldEntry.incrementCount()) {
            return oldEntry.symbol;
        }
        if (oldEntry != null) {
            removeValue(oldEntry);
        }
        Entry newEntry = Entry.createWithCounter(symbol);
        put(newEntry);
        return newEntry.symbol;
    }

    public void release(String symbol, Clock clock) {
        Entry entry = getByKey(symbol);
        if (entry != null && entry.decrementCount(clock))
            return;
        throw new IllegalStateException("Illegal reference count for " + symbol);
    }

    //TODO Perform cleanUp on rehash
    public void cleanUp(long expireTime) {
        for (Iterator<Entry> iter = concurrentIterator(); iter.hasNext(); ) {
            Entry entry = iter.next();

            entry.expireIfNeeded(expireTime);
            if (entry.isRemoved()) {
                // Synced in SynchronizedIndexedSet
                iter.remove();
            }
        }
    }

    // Utility classes

    // Class extends AtomicLong to avoid extra dereference
    //TODO Migrate to VarHandle
    static class Entry extends AtomicLong {
        // State depending on the TTL_BIT contains either RefCount (0) or TTL (1).
        private static final long TTL_BIT = 1L << 63;
        private static final long REMOVED = 0;

        public final String symbol;
        public final int hash; // Materialized hash value to avoid extra dereference

        private Entry(String symbol, long state) {
            super(state);
            this.symbol = symbol;
            this.hash = symbol.hashCode();
        }

        public static Entry createWithCounter(String symbol) {
            return new Entry(symbol, 1);
        }

        public static Entry createWithTime(String symbol, Clock clock) {
            return new Entry(symbol, Entry.TTL_BIT | clock.millis());
        }

        public static Entry createWithTime(char[] chars, Clock clock) {
            int hash = SymbolCache.hashFromKey(chars);
            String symbol = SymbolCache.stringFromKey(chars);
            if (symbol.hashCode() != hash) {
                throw new IllegalArgumentException(
                    "String hashes differ: given " + hash + ", required " + symbol.hashCode());
            }
            return new Entry(symbol, Entry.TTL_BIT | clock.millis());
        }

        /** Try to increment counter on non-removed entry. */
        public boolean incrementCount() {
            while (true) {
                long s = get();
                if (s == REMOVED)
                    return false;
                boolean isTtl = (s & TTL_BIT) != 0;
                if (compareAndSet(s, isTtl ? 1 : s + 1))
                    return true;
            }
        }

        /** Try to decrement counter on non-removed entry. */
        public boolean decrementCount(Clock clock) {
            while (true) {
                long s = get();
                boolean isTtl = (s & TTL_BIT) != 0;
                if (isTtl || s <= 0)
                    return false;
                // If counter is 1 then set TTL
                if (compareAndSet(s, s == 1 ? (TTL_BIT | clock.millis()) : s - 1))
                    return true;
            }
        }

        /** Try to update TTL on non-removed TTL entry. */
        public boolean updateTime(Clock clock) {
            long time = Long.MIN_VALUE;
            while (true) {
                long s = get();
                if (s == REMOVED)
                    return false;
                if ((s & TTL_BIT) == 0)
                    return true;
                if (time == Long.MIN_VALUE)
                    time = clock.millis();
                // Only update TTL if time is later than existing time
                if (time <= (s & ~TTL_BIT) || compareAndSet(s, TTL_BIT | time))
                    return true;
            }
        }

        /** Try to set expired entry to the final REMOVED state. */
        public void expireIfNeeded(long expireTime) {
            long s = get();
            if (s == REMOVED || (s & TTL_BIT) == 0)
                return;
            if ((s & ~TTL_BIT) <= expireTime)
                compareAndSet(s, REMOVED);
        }

        public boolean isRemoved() {
            return get() == REMOVED;
        }
    }

    private static final IndexerFunction<Object, Entry> INDEXER = new IndexerFunction<Object, Entry>() {
        @Override
        public Object getObjectKey(Entry value) {
            return value.symbol;
        }

        @Override
        public int hashCodeByKey(Object key) {
            if (key instanceof char[]) {
                return SymbolCache.hashFromKey((char[]) key);
            }
            return key.hashCode();
        }

        @Override
        public int hashCodeByValue(Entry value) {
            return value.hash;
        }

        @Override
        public boolean matchesByKey(Object key, Entry value) {
            if (key instanceof char[]) {
                return matchChars((char[]) key, value);
            }
            return Objects.equals(key, value.symbol);
        }

        private boolean matchChars(char[] chars, Entry value) {
            int hash = SymbolCache.hashFromKey(chars);
            if (value.hash != hash)
                return false;
            return SymbolCache.equalsWithKey(value.symbol, chars);
        }

        @Override
        public boolean matchesByValue(Entry newValue, Entry oldValue) {
            //noinspection NumberEquality
            return newValue == oldValue;
        }
    };
}
