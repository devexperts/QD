/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import com.devexperts.qd.*;
import junit.framework.Assert;

/**
 * Compares visited subscription against given source.
 */
class ComparingSubscriptionVisitor implements SubscriptionVisitor {
    private final SubscriptionIterator source;
    private int count;
    private DataRecord next_record;

    public static void compare(SubscriptionProvider provider, SubscriptionIterator source) {
        ComparingSubscriptionVisitor visitor = new ComparingSubscriptionVisitor(source);
        provider.retrieveSubscription(visitor);
        Assert.assertFalse("capacity", visitor.hasCapacity());
    }

    public ComparingSubscriptionVisitor(SubscriptionIterator source) {
        this.source = source;
        count = 1;
        next_record = source.nextRecord();
    }

    public boolean hasCapacity() {
        return next_record != null;
    }

    public void visitRecord(DataRecord record, int cipher, String symbol, long time) {
        Assert.assertEquals("record#" + count, record, next_record);
        Assert.assertEquals("cipher#" + count, cipher, source.getCipher());
        Assert.assertEquals("symbol#" + count, symbol, source.getSymbol());
        Assert.assertEquals("time#" + count, time, source.getTime());
        next_record = source.nextRecord();
        count++;
    }
}
