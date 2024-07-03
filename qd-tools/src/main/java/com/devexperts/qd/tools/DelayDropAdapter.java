/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordConsumer;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.DataIterators;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

class DelayDropAdapter extends DistributorAdapter {
    public static class Factory extends DistributorAdapter.Factory {
        private final long delayMillis;
        private final double dropFraction;

        public Factory(QDEndpoint endpoint, QDFilter filter, long delayMillis, double dropFraction) {
            super(endpoint, filter);
            this.delayMillis = delayMillis;
            this.dropFraction = dropFraction;
        }

        @Override
        public MessageAdapter createAdapter(QDStats stats) {
            return new DelayDropAdapter(endpoint, ticker, stream, history,
                getFilter(), getStripe(), stats, delayMillis, dropFraction);
        }
    }

    private static final Timer delayTimer = new Timer(true);
    private static final Random dropRandom = new Random();

    private final long delayMillis;
    private final double dropFraction;

    DelayDropAdapter(QDEndpoint endpoint, QDTicker ticker, QDStream stream, QDHistory history,
        QDFilter filter, QDFilter stripe, QDStats stats, long delayMillis, double dropFraction)
    {
        super(endpoint, ticker, stream, history, filter, stripe, stats, null);
        this.delayMillis = delayMillis;
        this.dropFraction = dropFraction;
    }

    private static void copyRecord(DataRecord record, DataIterator iterator, RecordBuffer buf) {
        buf.visitRecord(record, iterator.getCipher(), iterator.getSymbol());
        for (int i = 0, n = record.getIntFieldCount(); i < n; i++) {
            buf.visitIntField(record.getIntField(i), iterator.nextIntField());
        }
        for (int i = 0, n = record.getObjFieldCount(); i < n; i++) {
            buf.visitObjField(record.getObjField(i), iterator.nextObjField());
        }
    }

    private void processData(DataIterator iterator, final RecordConsumer consumer) {
        final RecordBuffer buf = new RecordBuffer();

        // drop records if needed
        if (dropFraction == 0) {
            buf.processData(iterator);
        } else {
            while (true) {
                DataRecord record = iterator.nextRecord();
                if (record == null)
                    break;
                if (dropRandom.nextDouble() < dropFraction) {
                    DataIterators.skipRecord(record, iterator);
                } else {
                    copyRecord(record, iterator, buf);
                }
            }
        }

        // delay records if needed
        if (delayMillis == 0) {
            consumer.process(buf);
        } else {
            delayTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    consumer.process(buf);
                }
            }, delayMillis);
        }
    }

    @Override
    protected void processData(DataIterator iterator, final MessageType message) {
        processData(iterator, source -> DelayDropAdapter.super.processData(source, message));
    }
}
