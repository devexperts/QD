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
package com.devexperts.qd;

import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.util.DataIterators;

class Void implements
    DataVisitor, DataListener, DataIterator, DataConsumer,
    SubscriptionVisitor, SubscriptionListener, SubscriptionIterator, SubscriptionConsumer,
    RecordSink, RecordListener
{
    static final Void VOID = new Void();

    private Void() {}

    @Override
    public boolean hasCapacity() {
        return true;
    }

    @Override
    public void recordsAvailable(RecordProvider provider) {
        provider.retrieve(this);
    }

    @Override
    public void dataAvailable(DataProvider provider) {
        provider.retrieveData(this);
    }

    @Override
    public void subscriptionAvailable(SubscriptionProvider provider) {
        provider.retrieveSubscription(this);
    }

    @Override
    public void append(RecordCursor cursor) {}

    @Override
    public void flush() {}

    @Override
    public void visitRecord(DataRecord record, int cipher, String symbol) {}
    @Override
    public void visitRecord(DataRecord record, int cipher, String symbol, long time) {}
    @Override
    public void visitIntField(DataIntField field, int value) {}
    @Override
    public void visitObjField(DataObjField field, Object value) {}

    @Override
    public DataRecord nextRecord() { return null; }
    @Override
    public int getCipher() { throw new IllegalStateException(); }
    @Override
    public String getSymbol() { throw new IllegalStateException(); }
    @Override
    public int nextIntField() { throw new IllegalStateException(); }
    @Override
    public Object nextObjField() { throw new IllegalStateException(); }
    @Override
    public long getTime() { throw new IllegalStateException(); }

    @Override
    public void processData(DataIterator iterator) {
        DataRecord record;
        while ((record = iterator.nextRecord()) != null)
            DataIterators.skipRecord(record, iterator);
    }

    @Override
    public void processSubscription(SubscriptionIterator iterator) {
        while (iterator.nextRecord() != null);
    }
}
