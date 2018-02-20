/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import java.util.*;

import com.devexperts.qd.*;
import com.devexperts.qd.ng.*;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.DataIterators;

class DelayDropAdapter extends DistributorAdapter {
    public static class Factory extends DistributorAdapter.Factory {
        private final long delay_millis;
        private final double drop_fraction;

        public Factory(QDEndpoint endpoint, SubscriptionFilter filter, long delay_millis, double drop_fraction) {
            super(endpoint, filter);
            this.delay_millis = delay_millis;
            this.drop_fraction = drop_fraction;
        }

        @Override
        public MessageAdapter createAdapter(QDStats stats) {
            return new DelayDropAdapter(ticker, stream, history, getFilter(), stats, delay_millis, drop_fraction);
        }
    }

    private static final Timer delay_timer = new Timer(true);
    private static final Random drop_random = new Random();

    private final long delay_millis;
    private final double drop_fraction;

    DelayDropAdapter(QDTicker ticker, QDStream stream, QDHistory history, SubscriptionFilter filter, QDStats stats, long delay_millis, double drop_fraction) {
        super(ticker, stream, history, filter, stats);
        this.delay_millis = delay_millis;
        this.drop_fraction = drop_fraction;
    }

    private static void copyRecord(DataRecord record, DataIterator iterator, RecordBuffer buf) {
        buf.visitRecord(record, iterator.getCipher(), iterator.getSymbol());
        for (int i = 0, n = record.getIntFieldCount(); i < n; i++)
            buf.visitIntField(record.getIntField(i), iterator.nextIntField());
        for (int i = 0, n = record.getObjFieldCount(); i < n; i++)
            buf.visitObjField(record.getObjField(i), iterator.nextObjField());
    }

    private void processData(DataIterator iterator, final RecordConsumer consumer) {
        final RecordBuffer buf = new RecordBuffer();

        // drop records if needed
        if (drop_fraction == 0)
            buf.processData(iterator);
        else
            while (true) {
                DataRecord record = iterator.nextRecord();
                if (record == null)
                    break;
                if (drop_random.nextDouble() < drop_fraction)
                    DataIterators.skipRecord(record, iterator);
                else
                    copyRecord(record, iterator, buf);
            }

        // delay records if needed
        if (delay_millis == 0)
            consumer.process(buf);
        else
            delay_timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    consumer.process(buf);
                }
            }, delay_millis);
    }

    @Override
    protected void processData(DataIterator iterator, final MessageType message) {
        processData(iterator, new RecordConsumer() {
            public void process(RecordSource source) {
                DelayDropAdapter.super.processData(source, message);
            }
        });
    }
}
