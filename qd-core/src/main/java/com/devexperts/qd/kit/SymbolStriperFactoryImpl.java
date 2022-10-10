/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.kit;

import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.spi.SymbolStriperFactory;
import com.devexperts.services.ServiceProvider;

import static com.devexperts.qd.kit.MonoStriper.MONO_STRIPER_NAME;
import static com.devexperts.qd.kit.HashStriper.HASH_STRIPER_PREFIX;
import static com.devexperts.qd.kit.RangeStriper.RANGE_STRIPER_PREFIX;

@SuppressWarnings("unused")
@ServiceProvider(order = -100)
public class SymbolStriperFactoryImpl extends SymbolStriperFactory {

    @Override
    public SymbolStriper createStriper(String spec) {
        if (spec == null || spec.isEmpty() || spec.equals(MONO_STRIPER_NAME))
            return MonoStriper.INSTANCE;
        if (spec.startsWith(RANGE_STRIPER_PREFIX))
            return RangeStriper.valueOf(getScheme(), spec);
        if (spec.startsWith(HASH_STRIPER_PREFIX))
            return HashStriper.valueOf(getScheme(), spec);
        return null;
    }
}
