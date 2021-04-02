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
package com.devexperts.qd.impl;

import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSource;

public abstract class AbstractDistributor implements QDDistributor {

    @Override
    public final SubscriptionProvider getAddedSubscriptionProvider() {
        return getAddedRecordProvider();
    }

    @Override
    public final SubscriptionProvider getRemovedSubscriptionProvider() {
        return getRemovedRecordProvider();
    }

    @Override
    public final void processData(DataIterator it) {
        if (it instanceof RecordSource) {
            process((RecordSource) it);
            return;
        }
        Deprecation.legacyDataIteratorWarning(it);
        // will copy data into capacity-limited buffer from pool
        boolean withTimeSequence = getAddedRecordProvider().getMode().hasEventTimeSequence();
        RecordMode bufMode = withTimeSequence ? RecordMode.TIMESTAMPED_DATA : RecordMode.DATA;
        RecordBuffer buf = RecordBuffer.getInstance(bufMode);
        buf.setCapacityLimited(true);
        boolean done;
        do {
            done = false;
            DataRecord record;
            while (true) {
                record = it.nextRecord();
                if (record == null) {
                    done = true;
                    break;
                }
                int cipher = it.getCipher();
                String symbol = it.getSymbol();
                buf.add(record, cipher, symbol).copyFrom(it);
                if (!buf.hasCapacity())
                    break;
            }
            // and process it
            process(buf);
            buf.clear();
        } while (!done);
        buf.release();
    }
}
