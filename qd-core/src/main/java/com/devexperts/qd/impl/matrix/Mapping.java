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
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.util.StringUtil;

/**
 * The <code>Mapping</code> converts unencodeable symbols into keys and vice versa
 * using matrix-based approach. The 'key' is a 'cipher' for encodeable symbols, or
 * assigned number from cipher's reserved region otherwise.
 * <p>
 * For unencodeable symbols, the <code>Mapping</code> assigns keys sequentially
 * starting with 1, then 2, then 3 and so on, restarting with 1 when maximum
 * allowed number is reached. All assigned keys are subject to usage counting.
 * Each key is considered 'payload key' as long as its usage counter is positive.
 * Unused keys are considered garbage and will be removed during next rehash.
 * <p>
 * <b>NOTE:</b> The usage counting is explicit and all participants must properly
 * maintain usage counters.
 * <p>
 * <b>NOTE:</b> It is a fatal error to pass an illegal key to any of the methods.
 * Also, many methods check internal state and signal fatal error is something is wrong.
 * By default, JVM is terminated on a fatal error. See {@link FatalError#fatal}.
 * <p>
 * The <code>Mapping</code> requires synchronized write access, but it allows
 * unsynchronized read-only access at any time. The synchronization is usually
 * inherited from appropriate structural global lock of QD. For convenience
 * and maintainability, all write access must be performed only via corresponding
 * {@link Mapper} instance.
 */
final class Mapping {
    static final int VALID_KEY = 0x20000000; // all valid keys have this bit set
    static final int PAYLOAD = VALID_KEY | SymbolCodec.VALID_CIPHER; // all valid keys or ciphers are non-zero with this mask
    static final int DELETED_CIPHER = 1; // deleted ciphers are replaced with it

    // [first_key, last_key] is a set of key that mapping generates
    static final int FIRST_KEY = 0x20000002;
    static final int LAST_KEY = 0x3FFFFFFF;

    /**
     * Root "owner" object for debugging purposes.
     */
    private final Object owner;

    private final int magic;
    private final int shift;
    private final int[] keys; // [index] -> key; used for hashing by key.
    private final int[] counters; // [index] -> counter;
    private final String[] symbols; // [index] -> symbol;
    private final int[] indices; // [position] -> index; used for hashing by symbol.

    private int overall_size;
    private int payload_size;

    private int last_assigned_key = LAST_KEY;
    private int max_counter;

    // SYNC: global
    Mapping(Object owner, int capacity, int prev_magic) {
        this.owner = owner;
        magic = Hashing.nextMagic(prev_magic, capacity);
        shift = Hashing.getShift(capacity);
        int length = 1 << (32 - shift);
        keys = new int[length];
        counters = new int[length];
        symbols = new String[length];
        indices = new int[length];
    }

    // ========== Internal ==========

    // SYNC: none
    private int getIndex(int key, int miss_mask) {
        int index = (key * magic) >>> shift;
        int test_key;
        while ((test_key = keys[index]) != key) {
            if (test_key == 0) {
                if (index > 0)
                    return index & miss_mask;
                index = keys.length;
            }
            index--;
        }
        return index;
    }

    // SYNC: none
    private int getPosition(String symbol, int miss_mask) {
        int position = (symbol.hashCode() * magic) >>> shift;
        int test_index;
        while ((test_index = indices[position]) != 0 || position == 0) {
            if (symbol.equals(symbols[test_index]))
                return position;
            if (position == 0)
                position = indices.length;
            position--;
        }
        return position & miss_mask;
    }

    // SYNC: none
    private int getPosition(char[] chars, int offset, int length, int miss_mask) {
        int hash = StringUtil.hashCode(chars, offset, length);
        int position = (hash * magic) >>> shift;
        int test_index;
        while ((test_index = indices[position]) != 0 || position == 0) {
            if (StringUtil.equals(symbols[test_index], chars, offset, length, hash))
                return position;
            if (position == 0)
                position = indices.length;
            position--;
        }
        return position & miss_mask;
    }

    // ========== Read Access - Unsynchronized ==========

    /**
     * Returns key for specified symbol or 0 if symbol is not found.
     */
    // SYNC: none
    int getKey(String symbol) {
        return keys[indices[getPosition(symbol, 0)]];
    }

    /**
     * Returns key for specified symbol or 0 if symbol is not found.
     */
    // SYNC: none
    int getKey(char[] chars, int offset, int length) {
        return keys[indices[getPosition(chars, offset, length, 0)]];
    }

    /*
     * Same as {@link #getSymbol} but returns <code>null</code> if not found.
     */
    String getSymbolAnyway(int key) {
        return symbols[getIndex(key, 0)];
    }

    /**
     * Returns symbol for specified key.
     * This is a read-only method, may be called unsynchronized.
     * However, since it throws exception on failure, it is supposed
     * that caller guarantees that key is present, which implicitly
     * requires that caller holds some synchronization lock, though
     * this lock may differ from the one used to protect the mapping.
     */
    // SYNC: global or local
    String getSymbol(int key) {
        int index = getIndex(key, 0);
        if (index == 0) {
            throw FatalError.fatal(owner, "Unknown key=" + key);
        }
        return symbols[index];
    }

    /**
     * Same as {@link #getSymbol(int)} but returns <code>null</code> if not found.
     */
    // SYNC: none
    String getSymbolIfPresent(int key) {
        return symbols[getIndex(key, 0)];
    }

    // SYNC: none
    String getSymbolIfPresent(char[] chars, int offset, int length) {
        int hash = StringUtil.hashCode(chars, offset, length);
        int position = (hash * magic) >>> shift;
        int test_index;
        while ((test_index = indices[position]) != 0 || position == 0) {
            String symbol = symbols[test_index];
            if (StringUtil.equals(symbol, chars, offset, length, hash))
                return symbol;
            if (position == 0)
                position = indices.length;
            position--;
        }
        return null;
    }

    // ========== Write Access - Require Synchronization ==========

    /**
     * Returns key for specified symbol. Assigns new key if none exists yet.
     * This method <b>DOES NOT</b> increment counter.
     */
    // SYNC: global
    int addKey(String symbol) {
        int position = getPosition(symbol, -1);
        int key = keys[indices[position]];
        if (key != 0)
            return key;
        return addKey(symbol, position);
    }

    /**
     * Returns key for specified symbol. Assigns new key if none exists yet.
     * This method <b>DOES NOT</b> increment counter.
     */
    // SYNC: global
    int addKey(char[] chars, int offset, int length) {
        int position = getPosition(chars, offset, length, -1);
        int key = keys[indices[position]];
        if (key != 0)
            return key;
        return addKey(new String(chars, offset, length), position);
    }

    // SYNC: global
    private int addKey(String symbol, int position) {
        int key = last_assigned_key;
        int index;
        do {
            if (++key > LAST_KEY)
                key = FIRST_KEY;
            index = getIndex(key, -1);
        } while (keys[index] != 0);

        if (counters[index] != 0)
            throw FatalError.fatal(owner, "Dirty counter for key=" + key + ", symbol=" + symbol);
        keys[index] = key;
        symbols[index] = symbol;
        indices[position] = index;
        overall_size++;
        return last_assigned_key = key;
    }

    /**
     * Increments maximum usage counter.
     */
    // SYNC: global
    void incMaxCounter(int delta) {
        if ((max_counter += delta) < 0) {
            throw FatalError.fatal(owner, "Maximum counter overflow.");
        }
    }

    /**
     * Decrements maximum usage counter.
     */
    // SYNC: global
    void decMaxCounter(int delta) {
        if ((max_counter -= delta) < 0)
            throw FatalError.fatal(owner, "Maximum counter underflow.");
        if (max_counter == 0 && payload_size != 0)
            throw FatalError.fatal(owner, "Excess payload.");
    }

    /**
     * Increments usage counter for the specified key.
     */
    // SYNC: global
    void incCounter(int key) {
        int index = getIndex(key, 0);
        if (index == 0)
            throw FatalError.fatal(owner, "Unknown key=" + key);
        int old_counter = counters[index]++;
        if (old_counter == 0) {
            if (++payload_size > overall_size)
                throw FatalError.fatal(owner, "Payload size overflow for key=" + key + ", symbol=" + symbols[index]);
        }
        if (old_counter >= max_counter)
            throw FatalError.fatal(owner, "Counter overflow for key=" + key + ", symbol=" + symbols[index]);
    }

    /**
     * Decrements usage counter for the specified key.
     */
    // SYNC: global
    void decCounter(int key) {
        int index = getIndex(key, 0);
        if (index == 0)
            throw FatalError.fatal(owner, "Unknown key=" + key);
        int new_counter = --counters[index];
        if (new_counter == 0) {
            if (--payload_size < 0)
                throw FatalError.fatal(owner, "Payload size underflow for key=" + key + ", symbol=" + symbols[index]);
        }
        if (new_counter < 0)
            throw FatalError.fatal(owner, "Counter underflow for key=" + key + ", symbol=" + symbols[index]);
        if (new_counter >= max_counter)
            throw FatalError.fatal(owner, "Counter overflow for key=" + key + ", symbol=" + symbols[index]);
    }

    // ========== Maintenance ==========

    // SYNC: global
    boolean needRehash() {
        return Hashing.needRehash(shift, overall_size, payload_size, Hashing.MAX_SHIFT);
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global
    Mapping rehash() {
        Mapping dest = new Mapping(owner, payload_size, magic);
        int copied = 0;
        for (int index = keys.length; --index > 0;) {
            int counter = counters[index];
            if (counter == 0)
                continue;
            int key = keys[index];
            String symbol = symbols[index];
            int dest_index = dest.getIndex(key, -1);
            if (dest.keys[dest_index] != 0)
                throw FatalError.fatal(owner, "Repeated key=" + key + ", symbol=" + symbols[index]);
            int dest_position = dest.getPosition(symbol, -1);
            if (dest.indices[dest_position] != 0)
                throw FatalError.fatal(owner, "Repeated symbol=" + symbols[index] + ", key=" + key);
            dest.keys[dest_index] = key;
            dest.counters[dest_index] = counter;
            dest.symbols[dest_index] = symbol;
            dest.indices[dest_position] = dest_index;
            copied++;
        }
        if (copied != payload_size)
            throw FatalError.fatal(owner, "Payload integrity corrupted.");
        dest.overall_size = copied;
        dest.payload_size = copied;
        dest.last_assigned_key = last_assigned_key;
        dest.max_counter = max_counter;
        return dest;
    }

    // ========== Debugging ==========

    void verify(CollectorDebug.Log log, Mapping verifyMapping) {
        log.info("Verifying mapping...");
        int totalKeys = 0;
        for (int index = 0; index < keys.length; index++) {
            int key = keys[index];
            String symbol = symbols[index];
            int counter = counters[index];
            if (key == 0) {
                if (symbol != null)
                    log.warn("Lost symbol " + symbol + " at " + index);
                if (counter != 0)
                    log.warn("Lost counter " + counter + " at " + index);
                continue;
            }
            if (symbol == null) {
                if (counter != 0)
                    log.warn("Undefined symbol for key " + key + " at " + index);
            }
            totalKeys++;
            if (counter == 0)
                continue;
            int verifyKey = verifyMapping.getKey(symbol);
            int verifyIndex = verifyMapping.getIndex(verifyKey, -1);
            int verifyCounter = verifyMapping.counters[verifyIndex];
            if (verifyCounter != counter)
                log.warn("Unexpected counter " + counter + " (expected counter " + verifyCounter + ") for key " + key + ", symbol " + symbol + " at " + index);
            verifyMapping.counters[verifyIndex] = 0;
        }
        for (int index = 0; index < verifyMapping.keys.length; index++) {
            int verifyCounter = verifyMapping.counters[index];
            if (verifyCounter != 0)
                log.warn("Missing key or zero counter for symbol " + verifyMapping.symbols[index] + " (expected counter " + verifyCounter + ")");
        }
        log.info("Verified " + totalKeys + " keys");
    }
}
