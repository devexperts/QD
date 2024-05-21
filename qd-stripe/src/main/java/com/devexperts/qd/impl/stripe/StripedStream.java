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

import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.SymbolStriper;
import com.devexperts.qd.stats.QDStats;

class StripedStream extends StripedCollector<QDStream> implements QDStream {
    private final QDStream[] collectors;

    StripedStream(QDFactory base, Builder<?> builder, SymbolStriper striper) {
        super(builder, striper);
        collectors = new QDStream[n];
        for (int i = 0; i < n; i++) {
            collectors[i] = base.streamBuilder()
                .copyFrom(builder)
                .withStats(stats.create(QDStats.SType.STREAM, "stripe=" + striper.getStripeFilter(i)))
                .build();
        }
    }

    @Override
    QDStream[] collectors() {
        return collectors;
    }

    @Override
    public void setEnableWildcards(boolean enableWildcards) {
        this.enableWildcards = enableWildcards;
        for (int i = 0; i < n; i++) {
            collectors[i].setEnableWildcards(enableWildcards);
        }
    }

    @Override
    public boolean getEnableWildcards() {
        return enableWildcards;
    }
}
