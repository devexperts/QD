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

/**
 * The <code>Mapper</code> is a convenient container for {@link Mapping} which
 * helps with write access and structural maintenance. All documentation of
 * {@link Mapping} is applicable to the <code>Mapper</code>.
 * <p>
 * Usually, every matrix that uses keys references the <code>Mapper</code> for
 * convenience. The matrices that allows really unsynchronized reads usually
 * switch to {@link Mapping} after rehash to support any ongoing parallel reads.
 */
final class Mapper {
    private volatile Mapping mapping;

    Mapper(Object owner) {
        mapping = new Mapping(owner, 0, 0);
    }

    /**
     * Returns current mapping instance. The mapping instance changes
     * with every structural modification (i.e. with every rehash).
     */
    Mapping getMapping() {
        return mapping;
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
        return mapping.getSymbol(key);
    }

    /**
     * Returns key for specified symbol. Assigns new key if none exists yet.
     * This method <b>DOES NOT</b> increment counter.
     */
    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global
    int addKey(String symbol) {
        return rehashIfNeeded().addKey(symbol);
    }

    /**
     * Returns key for specified symbol. Assigns new key if none exists yet.
     * This method <b>DOES NOT</b> increment counter.
     */
    // SYNC: global
    int addKey(char[] chars, int offset, int length) {
        return rehashIfNeeded().addKey(chars, offset, length);
    }

    // This method can try to allocate a lot of memory for rehash and die due to OutOfMemoryError.
    // SYNC: global
    Mapping rehashIfNeeded() {
        Mapping mapping = this.mapping;
        if (mapping.needRehash())
            this.mapping = mapping = mapping.rehash();
        return mapping;

    }

    /**
     * Increments maximum usage counter.
     */
    // SYNC: global
    void incMaxCounter(int delta) {
        mapping.incMaxCounter(delta);
    }

    /**
     * Decrements maximum usage counter.
     */
    // SYNC: global
    void decMaxCounter(int delta) {
        mapping.decMaxCounter(delta);
    }

    /**
     * Increments usage counter for specified key.
     */
    // SYNC: global
    void incCounter(int key) {
        mapping.incCounter(key);
    }

    /**
     * Decrements usage counter for specified key.
     */
    // SYNC: global
    void decCounter(int key) {
        // we do not rehash after decrementing counter, to provide clean, fast and OOM-free SubMatrix.close
        mapping.decCounter(key);
    }
}
