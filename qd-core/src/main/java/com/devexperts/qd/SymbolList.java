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
package com.devexperts.qd;

import java.util.Random;

public class SymbolList {
    protected final int[] ciphers;
    protected final String[] symbols;
    protected final int n;

    public SymbolList(String[] symbols, SymbolCodec codec) {
        this(symbols.length);
        for (int i = 0; i < symbols.length; i++) {
            String s = symbols[i];
            int c = codec.encode(s);
            this.ciphers[i] = c;
            this.symbols[i] = c == 0 ? s : null;
        }
    }

    public SymbolList(int[] ciphers, String[] symbols) {
        this(symbols.length);
        if (ciphers.length != n) {
            throw new IllegalArgumentException("symbols and ciphers must have equal length");
        }
        System.arraycopy(symbols, 0, this.symbols, 0, n);
        System.arraycopy(ciphers, 0, this.ciphers, 0, n);
    }

    protected SymbolList(int n) {
        this.n = n;
        this.symbols = new String[n];
        this.ciphers = new int[n];
    }

    public SymbolList(SymbolList another) {
        this.n = another.n;
        this.ciphers = another.ciphers;
        this.symbols = another.symbols;
    }

    public String getSymbol(int i) {
        return symbols[i];
    }

    public int getCipher(int i) {
        return ciphers[i];
    }

    public int size() {
        return n;
    }

    public SymbolList generateRandomSublist(int expectedSize) {
        Random rnd = new Random();
        int[] a = new int[n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            if (rnd.nextInt(n) < expectedSize) {
                a[k++] = i;
            }
        }
        SymbolList res = new SymbolList(k);
        for (int i = 0; i < k; i++) {
            res.ciphers[i] = ciphers[a[i]];
            res.symbols[i] = symbols[a[i]];
        }
        return res;
    }

    public int getUncodedCount() {
        int res = 0;
        for (int i = 0; i < n; i++)
            if (ciphers[i] == 0)
                res++;
        return res;
    }
}
