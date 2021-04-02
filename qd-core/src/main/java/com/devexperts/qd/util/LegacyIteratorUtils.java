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
package com.devexperts.qd.util;

import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.SubscriptionVisitor;
import com.devexperts.qd.ng.RecordBuffer;

public class LegacyIteratorUtils {
    private LegacyIteratorUtils() {} // do not create, static methods only

    /**
     * Processes data from specified iterator via specified visitor.
     * Returns <code>true</code> if some data may still remain in
     * the iterator or <code>false</code> if all data were processed.
     */
    public static boolean processData(DataIterator iterator, DataVisitor visitor) {
        if (iterator instanceof RecordBuffer)
            return ((RecordBuffer) iterator).retrieveData(visitor);
        while (visitor.hasCapacity()) {
            DataRecord record = iterator.nextRecord();
            if (record == null)
                return false;
            visitor.visitRecord(record, iterator.getCipher(), iterator.getSymbol());
            for (int i = 0, n = record.getIntFieldCount(); i < n; i++)
                visitor.visitIntField(record.getIntField(i), iterator.nextIntField());
            for (int i = 0, n = record.getObjFieldCount(); i < n; i++)
                visitor.visitObjField(record.getObjField(i), iterator.nextObjField());
        }
        return true;
    }

    /**
     * Processes subscription from specified iterator via specified visitor.
     * Returns <code>true</code> if some subscription may still remain in
     * the iterator or <code>false</code> if all subscription were processed.
     */
    public static boolean processSubscription(SubscriptionIterator iterator, SubscriptionVisitor visitor) {
        if (iterator instanceof RecordBuffer)
            return ((RecordBuffer) iterator).retrieveSubscription(visitor);
        while (visitor.hasCapacity()) {
            DataRecord record = iterator.nextRecord();
            if (record == null)
                return false;
            visitor.visitRecord(record, iterator.getCipher(), iterator.getSymbol(), iterator.getTime());
        }
        return true;
    }
}
