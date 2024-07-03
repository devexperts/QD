/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.kit;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SymbolStriper;

/**
 * Identity symbol striper that does no splitting.
 */
public final class MonoStriper implements SymbolStriper {

    public static final String MONO_STRIPER_NAME = "by1";

    public static final MonoStriper INSTANCE = new MonoStriper();

    private MonoStriper() {}

    @Override
    public String getName() {
        return MONO_STRIPER_NAME;
    }

    @Override
    public String toString() {
        return MONO_STRIPER_NAME;
    }

    @Override
    public DataScheme getScheme() {
        return null;
    }

    @Override
    public int getStripeCount() {
        return 1;
    }

    @Override
    public int getStripeIndex(int cipher, String symbol) {
        return 0;
    }

    @Override
    public int getStripeIndex(String symbol) {
        return 0;
    }

    @Override
    public int getStripeIndex(char[] symbol, int offset, int length) {
        return 0;
    }

    @Override
    public QDFilter getStripeFilter(int stripeIndex) {
        if (stripeIndex != 0)
            throw new IndexOutOfBoundsException();
        return QDFilter.ANYTHING;
    }
}
