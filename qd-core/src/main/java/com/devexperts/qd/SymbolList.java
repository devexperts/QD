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
package com.devexperts.qd;

import java.util.Random;

public class SymbolList {
    protected final int[] ciphers;
    protected final String[] symbols;
    protected final int n;
    private int sequence = 0;

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

    public SymbolList selectRandomSublist(int size) {
        if (size >= n) {
            return new SymbolList(this);
        }
        Random rnd = new Random();
        int[] indices = new int[n];
        for (int i = 0; i < n; i++) {
            indices[i] = i;
        }
        for (int i = n - 1; i >= n - size; i--) {
            int randomIndex = rnd.nextInt(i + 1);
            int temp = indices[randomIndex];
            indices[randomIndex] = indices[i];
            indices[i] = temp;
        }
        SymbolList res = new SymbolList(size);
        for (int i = 0; i < size; i++) {
            int idx = indices[n - size + i];
            res.ciphers[i] = ciphers[idx];
            res.symbols[i] = symbols[idx];
        }
        return res;
    }

    public synchronized SymbolList selectNextSequenceSublist(int size) {
        SymbolList res = new SymbolList(size);
        for (int i = 0; i < size; i++) {
            sequence = (sequence + 1) % n;
            res.ciphers[i] = ciphers[sequence];
            res.symbols[i] = symbols[sequence];
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
