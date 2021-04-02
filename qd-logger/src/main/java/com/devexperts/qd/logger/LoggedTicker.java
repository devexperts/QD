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
package com.devexperts.qd.logger;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.ng.RecordCursor;

public class LoggedTicker extends LoggedCollector implements QDTicker {
    private final QDTicker delegate;

    public LoggedTicker(Logger log, QDTicker delegate, Builder<?> builder) {
        super(log, delegate, builder);
        this.delegate = delegate;
    }

    @Override
    public boolean isAvailable(DataRecord record, int cipher, String symbol) {
        return delegate.isAvailable(record, cipher, symbol);
    }

    @Override
    public int getInt(DataIntField field, int cipher, String symbol) {
        return delegate.getInt(field, cipher, symbol);
    }

    @Override
    public Object getObj(DataObjField field, int cipher, String symbol) {
        return delegate.getObj(field, cipher, symbol);
    }

    @Override
    public void getData(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol) {
        delegate.getData(owner, record, cipher, symbol);
    }

    @Override
    public boolean getDataIfAvailable(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol) {
        return delegate.getDataIfAvailable(owner, record, cipher, symbol);
    }

    @Override
    public boolean getDataIfSubscribed(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol) {
        return delegate.getDataIfSubscribed(owner, record, cipher, symbol);
    }
}
