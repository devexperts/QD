/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2020 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.stripe;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.impl.AbstractCollectorBuilder;
import com.devexperts.qd.impl.matrix.MatrixFactory;
import com.devexperts.util.SystemProperties;

public class StripedFactory extends QDFactory {
    private static final String STRIPE_PROP = "com.devexperts.qd.impl.stripe";

    private final QDFactory base = new MatrixFactory();
    private final int n;

    public static QDFactory getInstance() {
        return new StripedFactory(SystemProperties.getIntProperty(STRIPE_PROP, 0));
    }

    // TODO Experimental API to access stripes.
    @SuppressWarnings("rawtypes")
    public static QDCollector[] getStripes(QDCollector collector) {
        return collector instanceof StripedCollector ? ((StripedCollector) collector).collectors().clone() : new QDCollector[] {collector};
    }

    // TODO Experimental API to access stripes.
    @SuppressWarnings("rawtypes")
    public static int getStripe(QDCollector collector, int cipher, String symbol) {
        return collector instanceof StripedCollector ? ((StripedCollector) collector).index(cipher, symbol) : 0;
    }

    public StripedFactory(int n) {
        this.n = n;
    }

    @Override
    public QDCollector.Builder<?> collectorBuilder(QDContract contract) {
        return new AbstractCollectorBuilder(contract) {
            @Override
            public QDCollector build() {
                int n = getStripeProp(contract.toString());
                if (n <= 1)
                    return base.collectorBuilder(getContract())
                        .copyFrom(this)
                        .build();
                switch (contract) {
                case TICKER:
                    return new StripedTicker(base, this, n);
                case STREAM:
                    return new StripedStream(base, this, n);
                case HISTORY:
                    return new StripedHistory(base, this, n);
                default:
                    throw new IllegalArgumentException();
                }
            }

            private int getStripeProp(String contract) {
                return SystemProperties.getIntProperty(STRIPE_PROP + "." + contract, StripedFactory.this.n);
            }
        };
    }

    @Override
    public QDAgent.Builder createVoidAgentBuilder(QDContract contract, DataScheme scheme) {
        return base.createVoidAgentBuilder(contract, scheme);
    }
}
