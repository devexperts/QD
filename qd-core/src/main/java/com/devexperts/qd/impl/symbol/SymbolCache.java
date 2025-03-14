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
import com.devexperts.qd.util.StringUtil;
import com.devexperts.util.SystemProperties;

import java.time.Clock;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * A string cache for symbols that allows <i>resolve</i> symbol, i.e. get its canonical string representation,
 * using raw data (i.e. character array) to avoid string construction.
 *
 * <p>Cache supports two storage modes:
 * <ul>
 *     <li>Strong - where the reference count for a symbol is increased, preventing its eviction.</li>
 *     <li>Weak - a timer is started, allowing symbol eviction once the timer expires.</li>
 * </ul>
 *
 * <p>In order to effectively resolve string from character array, additional data like length and hash
 * must be embedded into array (see {@link #embedKey(char[], int, int)} for details).
 *
 * <p>The {@code SymbolCache} is thread-safe.
 */
@Internal
public class SymbolCache {

    // MAGIC0 + MAGIC1 should never occur in the valid UTF-16 char sequence
    /** Value of the first char in the embedded key char array. */
    public static final char KEY_MAGIC0 = 0xDFFE; // UTF-16 low-surrogate
    /** Value of the second char in the embedded key char array. */
    public static final char KEY_MAGIC1 = 0xDFFF; // Another UTF-16 low-surrogate

    /** Length of the header in the embedded key char array. */
    public static final int KEY_HEADER_SIZE = 6;

    public static final long DEFAULT_TTL = SystemProperties.getLongProperty(SymbolCache.class, "ttl", 10_000L);
    public static final int DEFAULT_SHARDING = SystemProperties.getIntProperty(SymbolCache.class, "sharding", 16);

    private final SymbolCacheSet[] sets;
    private final int mask;
    private final Clock clock; // Ignore possible Clock problems (time adjustments, etc)
    private final long ttl;

    protected SymbolCache(Builder builder) {
        // Make shard size power of 2 but no more than 1 << 10
        int shards = 1 << (Math.min(10, Integer.SIZE - Integer.numberOfLeadingZeros(builder.sharding - 1)));
        int shardCapacity = builder.initialCapacity / shards;

        this.sets = IntStream.range(0, shards)
            .mapToObj(i -> new SymbolCacheSet(shardCapacity))
            .toArray(SymbolCacheSet[]::new);
        this.mask = shards - 1;
        this.clock = builder.clock;
        this.ttl = builder.ttl;
    }

    // Public utility methods for key-embedded chars

    public static boolean isKey(char[] chars, int offset) {
        return (offset == KEY_HEADER_SIZE) && (chars[0] == KEY_MAGIC0) && (chars[1] == KEY_MAGIC1);
    }

    public static char[] embedKey(char[] chars, int offset, int length) {
        if (offset != KEY_HEADER_SIZE)
            throw new IllegalArgumentException();

        // Char array layout:
        // MAGIC (2 chars) + Length (2 chars) + Hash (2 chars) + String
        int hash = StringUtil.hashCode(chars, KEY_HEADER_SIZE, length);
        chars[0] = KEY_MAGIC0; // Invalid UTF-16
        chars[1] = KEY_MAGIC1;
        chars[2] = (char) (length >> 16);
        chars[3] = (char) length;
        chars[4] = (char) (hash >> 16);
        chars[5] = (char) hash;

        return chars;
    }

    public static int lengthFromKey(char[] chars) {
        return chars[2] << 16 | chars[3];
    }

    public static int hashFromKey(char[] chars) {
        return chars[4] << 16 | chars[5];
    }

    public static String stringFromKey(char[] chars) {
        return new String(chars, KEY_HEADER_SIZE, lengthFromKey(chars));
    }

    public static boolean equalsWithKey(String s, char[] chars) {
        return StringUtil.equals(s, chars, KEY_HEADER_SIZE, lengthFromKey(chars));
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    private SymbolCacheSet shard(String symbol) {
        return sets[symbol.hashCode() & mask];
    }

    private SymbolCacheSet shard(char[] symbol) {
        return sets[hashFromKey(symbol) & mask];
    }

    /**
     * Returns resolved symbol for the specified key char array and stores it using "weak" storage mode
     * if it was not previously present in this cache.
     *
     * <p><b>Note</b> that key char array must already contain additional information (hash and length) embedded inside!
     *
     * @see #isKey(char[], int)
     * @see #embedKey(char[], int, int)
     * @param keyChars string encoded in char array (with length and hash encoded in the array)
     * @return canonical string representation, never {@code null}
     */
    public String resolveKey(char[] keyChars) {
        assert (isKey(keyChars, KEY_HEADER_SIZE));
        return shard(keyChars).resolveKey(keyChars, clock);
    }

    /**
     * Returns resolved symbol for the specified string and stores it using "weak" storage mode
     * if it was not previously present in this cache.
     *
     * @param symbol symbol
     * @return canonical string representation, never {@code null}
     */
    public String resolve(String symbol) {
        return shard(symbol).resolveString(symbol, clock);
    }

    /**
     * Returns resolved symbol for the specified string,
     * or {@code null} if it was not previously present in this cache.
     *
     * @param symbol symbol
     * @return canonical string representation, or {@code null}
     */
    public String getIfPresent(String symbol) {
        return shard(symbol).getIfPresent(symbol);
    }

    /**
     * Returns resolved symbol for the specified string and stores it using strong storage mode,
     * i.e. increments its reference count.
     *
     * @param symbol symbol
     */
    public String resolveAndAcquire(String symbol) {
        return shard(symbol).acquire(symbol);
    }

    /**
     * Decrements symbol's reference counter, and starts a timer ("weak" storage mode) if the counter reaches 0.
     *
     * @param symbol symbol
     */
    public void release(String symbol) {
        shard(symbol).release(symbol, clock);
    }

    /**
     * Evict expired symbols from the cache.
     */
    public void cleanUp() {
        long expireTime = clock.millis() - ttl;
        for (SymbolCacheSet set : sets) {
            set.cleanUp(expireTime);
        }
    }

    /**
     * Removes all symbols from the cache.
     */
    public void clear() {
        for (SymbolCacheSet set : sets) {
            set.clear();
        }
    }

    public int size() {
        int size = 0;
        for (SymbolCacheSet set : sets) {
            size += set.size();
        }
        return size;
    }

    // Utility classes

    public static class Builder {
        protected int initialCapacity = 0;
        protected Clock clock = Clock.systemUTC();
        protected long ttl = DEFAULT_TTL;
        protected int sharding = DEFAULT_SHARDING;

        public Builder() {
        }

        /** Specifies cache total initial capacity. */
        public Builder withInitialCapacity(int initialCapacity) {
            if (initialCapacity < 0)
                throw new IllegalArgumentException("Invalid initial capacity: " + initialCapacity);
            this.initialCapacity = initialCapacity;
            return this;
        }

        /** Specifies cache clock (for eviction timers). */
        public Builder withClock(Clock clock) {
            this.clock = Objects.requireNonNull(clock, "clock");
            return this;
        }

        /** Specifies "time-to-live" in millis for weakly stored symbols. */
        public Builder withTtl(long ttl) {
            if (ttl < 0)
                throw new IllegalArgumentException("Invalid time-to-live: " + ttl);
            this.ttl = ttl;
            return this;
        }

        /** Specifies number of shards for the cache. */
        public Builder withSharding(int sharding) {
            if (sharding <= 0)
                throw new IllegalArgumentException("Invalid sharding: " + sharding);
            this.sharding = sharding;
            return this;
        }

        public SymbolCache build() {
            return new SymbolCache(this);
        }
    }
}
