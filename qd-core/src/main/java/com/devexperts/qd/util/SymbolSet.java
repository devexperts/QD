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

import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolReceiver;
import com.devexperts.qd.impl.matrix.MatrixSymbolSet;

import java.util.Arrays;

/**
 * Specialized hash set that contains (cipher, symbol) pair that denote symbol.
 */
public abstract class SymbolSet {
    // ------- static methods -------

    public static SymbolSet createInstance() {
        return new MatrixSymbolSet();
    }

    public static SymbolSet copyOf(SymbolSet other) {
        SymbolSet set = createInstance();
        set.addAll(other);
        return set;
    }

    // ------- instance fields & constructor -------

    protected SymbolSet() {}

    // ------- abstract methods -------

    public abstract int size();

    /**
     * Returns symbol used for specified characters or <code>null</code> if not found.
     */
    public abstract String getSymbol(char[] chars, int offset, int length);

    public abstract boolean contains(int cipher, String symbol);

    public abstract boolean contains(int cipher, char[] chars, int offset, int length);

    /**
     * Examines set via specified <code>SymbolReceiver</code>.
     */
    public abstract void examine(SymbolReceiver receiver);

    // ------- methods for override in mutable implementations -------

    public SymbolSet unmodifiable() {
        return this;
    }

    public boolean add(int cipher, String symbol) {
        throw new UnsupportedOperationException("unmodifiable");
    }

    public boolean add(int cipher, char[] chars, int offset, int length) {
        throw new UnsupportedOperationException("unmodifiable");
    }

    public boolean remove(int cipher, String symbol) {
        throw new UnsupportedOperationException("unmodifiable");
    }

    public boolean remove(int cipher, char[] chars, int offset, int length) {
        throw new UnsupportedOperationException("unmodifiable");
    }

    public void clear() {
        throw new UnsupportedOperationException("unmodifiable");
    }

    // ------- concrete methods -------

    public boolean isEmpty() {
        return size() == 0;
    }

    public void addAll(SymbolSet other) {
        other.examine(this::add);
    }

    public void retainAll(SymbolSet other) {
        examine((cipher, symbol) -> {
            if (!other.contains(cipher, symbol))
                remove(cipher, symbol);
        });
    }

    public void retainAll(QDFilter filter) {
        examine((cipher, symbol) -> {
            if (!filter.accept(null, null, cipher, symbol))
                remove(cipher, symbol);
        });
    }

    public boolean containsAll(SymbolSet other) {
        boolean[] result = { true };
        other.examine((cipher, symbol) -> {
            if (!contains(cipher, symbol))
                result[0] = false;
        });
        return result[0];
    }

    @Override
    public int hashCode() {
        int[] hashCode = { 0 };
        examine((cipher, symbol) -> {
            if (cipher != 0)
                hashCode[0] += cipher;
            else
                hashCode[0] += symbol.hashCode();
        });
        return hashCode[0];
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SymbolSet))
            return false;
        final SymbolSet other = (SymbolSet) obj;
        return size() == other.size() && containsAll(other);
    }

    // ------- unmodifiable set for short lists -------

    protected static class ListSet extends SymbolSet {
        private final int[] ciphers;
        private final String[] symbols;
        private final int size;

        public ListSet(SymbolSet set) {
            int[] sizes = new int[2];
            set.examine((cipher, symbol) -> sizes[cipher != 0 ? 0 : 1]++);
            ciphers = new int[sizes[0]];
            symbols = new String[sizes[1]];
            size = sizes[0] + sizes[1];
            Arrays.fill(sizes, 0);
            set.examine((cipher, symbol) -> {
                if (cipher != 0)
                    ciphers[sizes[0]++] = cipher;
                else
                    symbols[sizes[1]++] = symbol;
            });
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public String getSymbol(char[] chars, int offset, int length) {
            for (String s : symbols) {
                if (StringUtil.equals(s, chars, offset, length))
                    return s;
            }
            return null;
        }

        @Override
        public boolean contains(int cipher, String symbol) {
            return cipher != 0 ? containsCipher(cipher) : containsSymbol(symbol);
        }

        @Override
        public boolean contains(int cipher, char[] chars, int offset, int length) {
            return cipher != 0 ? containsCipher(cipher) : getSymbol(chars, offset, length) != null;
        }

        @Override
        public void examine(SymbolReceiver receiver) {
            for (int cipher : ciphers)
                receiver.receiveSymbol(cipher, null);
            for (String symbol : symbols)
                receiver.receiveSymbol(0, symbol);
        }

        private boolean containsCipher(int cipher) {
            for (int c : ciphers) {
                if (c == cipher)
                    return true;
            }
            return false;
        }

        private boolean containsSymbol(String symbol) {
            for (String s : symbols) {
                if (s.equals(symbol))
                    return true;
            }
            return false;
        }
    }
}
