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

/**
 * {@link SymbolCache} methods extracted as interface for easier testing.
 */
public interface BenchmarkSymbolCache {

    public String resolveKey(char[] chars);

    public String resolve(String symbol);

    public String resolveAndAcquire(String symbol);

    public void cleanUp();

    public void clear();

    public static class OriginalSymbolCache extends SymbolCache implements BenchmarkSymbolCache {
        public OriginalSymbolCache(Builder builder) {
            super(builder);
        }
    }
}
