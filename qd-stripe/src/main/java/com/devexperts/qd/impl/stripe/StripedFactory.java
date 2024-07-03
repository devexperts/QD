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
package com.devexperts.qd.impl.stripe;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.impl.AbstractCollectorBuilder;
import com.devexperts.qd.impl.matrix.MatrixFactory;
import com.devexperts.qd.kit.HashStriper;
import com.devexperts.qd.kit.MonoStriper;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.util.SystemProperties;

public class StripedFactory extends QDFactory {

    /**
     * Deprecated property to specify hash striper for collectors.
     */
    @Deprecated
    private static final String STRIPE_PROPERTY = "com.devexperts.qd.impl.stripe";

    private static final Logging log = Logging.getLogging(StripedFactory.class);

    static {
        if (SystemProperties.getProperty(STRIPE_PROPERTY, null) != null) {
            log.warn("WARNING: DEPRECATED use of \"" + STRIPE_PROPERTY + "\" property, use \"" +
                QDEndpoint.DXFEED_STRIPE_PROPERTY + "=byhash<N>\" instead!");
        }
    }

    private final QDFactory base = new MatrixFactory();
    private final int hashCount;

    public static QDFactory getInstance() {
        // Resolve striping value each time during Collector's creation
        return new StripedFactory(-1);
    }

    // TODO Experimental API to access stripes.
    @SuppressWarnings("rawtypes")
    public static QDCollector[] getStripes(QDCollector collector) {
        return collector instanceof StripedCollector ?
            ((StripedCollector) collector).collectors().clone() : new QDCollector[] {collector};
    }

    // TODO Experimental API to access stripes.
    @SuppressWarnings("rawtypes")
    public static int getStripe(QDCollector collector, int cipher, String symbol) {
        return collector instanceof StripedCollector ? ((StripedCollector) collector).index(cipher, symbol) : 0;
    }

    public StripedFactory(int hashCount) {
        this.hashCount = hashCount;
    }

    @Override
    public QDCollector.Builder<?> collectorBuilder(QDContract contract) {
        return new AbstractCollectorBuilder(contract) {
            @Override
            public QDCollector build() {
                SymbolStriper striper = getStriper();
                if (striper.getStripeCount() == 1) {
                    // Get striper from the legacy property.
                    // Legacy property can override mono striper - it is intentional until it is removed.
                    striper = getHashStriper(contract.toString());
                    if (striper.getStripeCount() == 1) {
                        return buildDefault();
                    }
                }

                log.info("Creating striped " + getContract() +
                    "[" + getStats().getFullKeyProperties() + "], striper=" + striper);

                switch (contract) {
                    case TICKER:
                        return new StripedTicker(base, this, striper);
                    case STREAM:
                        return new StripedStream(base, this, striper);
                    case HISTORY:
                        return new StripedHistory(base, this, striper);
                    default:
                        throw new IllegalArgumentException();
                }
            }

            private QDCollector buildDefault() {
                return base.collectorBuilder(getContract()).copyFrom(this).build();
            }

            private SymbolStriper getHashStriper(String contract) {
                int defaultHashCount = hashCount;
                if (defaultHashCount < 0) {
                    defaultHashCount = SystemProperties.getIntProperty(STRIPE_PROPERTY, 0);
                }
                int hashCount = SystemProperties.getIntProperty(STRIPE_PROPERTY + "." + contract, defaultHashCount);
                return (hashCount > 1) ? HashStriper.valueOf(getScheme(), hashCount) : MonoStriper.INSTANCE;
            }
        };
    }

    @Override
    public QDAgent.Builder createVoidAgentBuilder(QDContract contract, DataScheme scheme) {
        return base.createVoidAgentBuilder(contract, scheme);
    }
}
