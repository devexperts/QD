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
package com.devexperts.qd.spi;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.services.Service;

import java.util.List;

@Service(combineMethod = "combineFactories")
public abstract class SymbolStriperFactory implements DataSchemeService {
    private volatile DataScheme scheme; // double-checked initialization

    protected SymbolStriperFactory() {}

    protected SymbolStriperFactory(DataScheme scheme) {
        this.scheme = scheme;
    }

    /**
     * Returns data scheme of this filter factory.
     */
    public final DataScheme getScheme() {
        return scheme;
    }

    /**
     * Sets data scheme for this filter factory. This method can be invoked only once in an object lifetime.
     * @throws NullPointerException when scheme is null.
     * @throws IllegalStateException when scheme is already set.
     */
    @Override
    public void setScheme(DataScheme scheme) {
        if (scheme == null)
            throw new NullPointerException();
        if (this.scheme == scheme)
            return;
        setSchemeSync(scheme);
    }

    private synchronized void setSchemeSync(DataScheme scheme) {
        if (this.scheme != null && this.scheme != scheme)
            throw new IllegalStateException("Different scheme is already set");
        this.scheme = scheme;
    }

    /**
     * Creates custom project-specific filter based on the specification string.
     * Returns null if the given {@code spec} is not supported by this factory.
     */
    public abstract SymbolStriper createStriper(String spec);

    @SuppressWarnings("unused")
    public static SymbolStriperFactory combineFactories(List<SymbolStriperFactory> factories) {
        return new Combined(factories.toArray(new SymbolStriperFactory[0]));
    }

    private static class Combined extends SymbolStriperFactory {
        private final SymbolStriperFactory[] factories;

        Combined(SymbolStriperFactory[] factories) {
            this.factories = factories;
        }

        @Override
        public void setScheme(DataScheme scheme) {
            super.setScheme(scheme);
            for (SymbolStriperFactory factory : factories)
                factory.setScheme(scheme);
        }

        @Override
        public SymbolStriper createStriper(String spec) {
            for (SymbolStriperFactory factory : factories) {
                SymbolStriper striper = factory.createStriper(spec);
                if (striper != null)
                    return striper;
            }
            return null;
        }
    }
}
