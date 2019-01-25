/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.stripe;

import com.devexperts.qd.*;
import com.devexperts.qd.ng.*;
import com.devexperts.qd.stats.QDStats;

class StripedTicker extends StripedCollector<QDTicker> implements QDTicker {
    private final QDTicker[] collectors;

    StripedTicker(QDFactory base, Builder<?> builder, int n) {
        super(builder, n);
        collectors = new QDTicker[n];
        for (int i = 0; i < n; i++)
            collectors[i] = base.tickerBuilder()
                .copyFrom(builder)
                .withStats(stats.create(QDStats.SType.TICKER, "stripe=" + i))
                .build();
    }

    @Override
    QDTicker[] collectors() {
        return collectors;
    }

    @Override
    public boolean isAvailable(DataRecord record, int cipher, String symbol) {
        return collector(cipher, symbol).isAvailable(record, cipher, symbol);
    }

    @Override
    public int getInt(DataIntField field, int cipher, String symbol) {
        return collector(cipher, symbol).getInt(field, cipher, symbol);
    }

    @Override
    public Object getObj(DataObjField field, int cipher, String symbol) {
        return collector(cipher, symbol).getObj(field, cipher, symbol);
    }

    @Override
    public void getData(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol) {
        collector(cipher, symbol).getData(owner, record, cipher, symbol);
    }

    @Override
    public boolean getDataIfAvailable(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol) {
        return collector(cipher, symbol).getDataIfAvailable(owner, record, cipher, symbol);
    }

    @Override
    public boolean getDataIfSubscribed(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol) {
        return collector(cipher, symbol).getDataIfSubscribed(owner, record, cipher, symbol);
    }

    @Override
    public void remove(RecordSource source) {
        RecordBuffer[] buf = Buffers.filterData(this, source);
        for (int i = 0; i < n; i++)
            if (buf[i] != null && !buf[i].isEmpty())
                collectors[i].remove(buf[i]);
    }
}
