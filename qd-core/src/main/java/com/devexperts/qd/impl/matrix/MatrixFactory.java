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

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.impl.AbstractCollectorBuilder;

/**
 * The <code>MatrixFactory</code> creates matrix-based implementations for core QD components.
 */
public class MatrixFactory extends QDFactory {

    @Override
    public QDCollector.Builder<?> collectorBuilder(QDContract contract) {
        return new AbstractCollectorBuilder(contract) {
            @Override
            public QDCollector build() {
                switch (contract) {
                case TICKER:
                    return new Ticker(this);
                case STREAM:
                    return new Stream(this);
                case HISTORY:
                    return new History(this);
                default:
                    throw new IllegalArgumentException();
                }
            }
        };
    }

    @Override
    public QDAgent.Builder createVoidAgentBuilder(QDContract contract, DataScheme scheme) {
        return new VoidAgentBuilder(contract, scheme);
    }
}
