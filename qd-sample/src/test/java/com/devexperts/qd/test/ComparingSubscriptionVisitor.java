/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.SubscriptionVisitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Compares visited subscription against given source.
 */
class ComparingSubscriptionVisitor implements SubscriptionVisitor {
    private final SubscriptionIterator source;
    private int count;
    private DataRecord nextRecord;

    public static void compare(SubscriptionProvider provider, SubscriptionIterator source) {
        ComparingSubscriptionVisitor visitor = new ComparingSubscriptionVisitor(source);
        provider.retrieveSubscription(visitor);
        assertFalse("capacity", visitor.hasCapacity());
    }

    public ComparingSubscriptionVisitor(SubscriptionIterator source) {
        this.source = source;
        count = 1;
        nextRecord = source.nextRecord();
    }

    public boolean hasCapacity() {
        return nextRecord != null;
    }

    public void visitRecord(DataRecord record, int cipher, String symbol, long time) {
        assertEquals("record#" + count, record, nextRecord);
        assertEquals("cipher#" + count, cipher, source.getCipher());
        assertEquals("symbol#" + count, symbol, source.getSymbol());
        assertEquals("time#" + count, time, source.getTime());
        nextRecord = source.nextRecord();
        count++;
    }
}
