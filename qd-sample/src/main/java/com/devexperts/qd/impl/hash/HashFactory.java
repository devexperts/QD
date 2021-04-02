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
package com.devexperts.qd.impl.hash;

import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.impl.AbstractCollectorBuilder;
import com.devexperts.qd.impl.HistoryViaTicker;
import com.devexperts.qd.impl.StreamViaCollector;

/**
 * The <code>HashFactory</code> creates hash-based implementations for core QD components.
 */
public class HashFactory extends QDFactory {

    @Override
    public QDCollector.Builder<?> collectorBuilder(QDContract contract) {
        return new AbstractCollectorBuilder(contract) {
            @Override
            public QDCollector build() {
                QDTicker ticker = new HashTicker(this);
                switch (contract) {
                case TICKER:
                    return ticker;
                case STREAM:
                    return new StreamViaCollector(ticker, this);
                case HISTORY:
                    return new HistoryViaTicker(ticker, this);
                default:
                    throw new IllegalArgumentException();
                }
            }
        };
    }
}
