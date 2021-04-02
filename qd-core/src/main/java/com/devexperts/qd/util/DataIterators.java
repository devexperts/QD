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
import com.devexperts.qd.SubscriptionFilter;

/**
 * Helper methods to work with {@link DataIterator DataIterator} interface.
 */
public class DataIterators {
    private DataIterators() {}

    public static void skipRecord(DataRecord record, DataIterator it) {
        for (int i = record.getIntFieldCount(); --i >= 0;)
            it.nextIntField();
        for (int i = record.getObjFieldCount(); --i >= 0;)
            it.nextObjField();
    }

    public static DataIterator filter(DataIterator it, SubscriptionFilter filter) {
        if (filter == null)
            return it;
        return new FilteringDataIterator(it, filter);
    }

    private static class FilteringDataIterator implements DataIterator {
        private final DataIterator it;
        private final SubscriptionFilter filter;

        public FilteringDataIterator(DataIterator it, SubscriptionFilter filter) {
            this.it = it;
            this.filter = filter;
        }

        public int getCipher() {
            return it.getCipher();
        }

        public String getSymbol() {
            return it.getSymbol();
        }

        public DataRecord nextRecord() {
            DataRecord record = it.nextRecord();
            while (record != null && !filter.acceptRecord(record, it.getCipher(), it.getSymbol())) {
                skipRecord(record, it);
                record = it.nextRecord();
            }
            return record;
        }

        public int nextIntField() {
            return it.nextIntField();
        }

        public Object nextObjField() {
            return it.nextObjField();
        }
    }
}
