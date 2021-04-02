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
package com.devexperts.qd.util;

import com.devexperts.qd.impl.matrix.MatrixSymbolObjectMap;

/**
 * Specialized hash map that maps (cipher, symbol) pair that denote symbol to an
 * arbitrary <code>Object</code> of type <code>T</code>.
 */
public abstract class SymbolObjectMap<T> {
    protected SymbolObjectMap() {}

    public static <T>SymbolObjectMap<T> createInstance() {
        return new MatrixSymbolObjectMap<T>();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public abstract int size();

    /**
     * Returns symbol used for specified characters or <code>null</code> if not found.
     */
    public abstract String getSymbol(char[] chars, int offset, int length);

    public abstract boolean contains(int cipher, String symbol);

    public abstract boolean contains(int cipher, char[] chars, int offset, int length);

    public abstract T get(int cipher, String symbol);

    public abstract T get(int cipher, char[] chars, int offset, int length);

    public abstract T put(int cipher, String symbol, T value);

    public abstract T put(int cipher, char[] chars, int offset, int length, T value);

    public abstract T remove(int cipher, String symbol);

    public abstract T remove(int cipher, char[] chars, int offset, int length);

    public abstract void clear();

    /**
     * Examines map entries via specified <code>SymbolObjectVisitor</code>.
     * Returns <code>true</code> if not all entries were examined
     * (this happens when {@link com.devexperts.qd.util.SymbolObjectVisitor#hasCapacity() visitor.hasCapacity()}
     * return <code>false</code>)
     * or <code>false</code> if all entries were examined.
     * @param visitor SymbolObjectVisitor to use
     * @return true, if some data left unexamined
     */
    public abstract boolean examineEntries(SymbolObjectVisitor<T> visitor);
}
