/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.SymbolList;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.qtp.QDEndpoint;

import java.util.concurrent.locks.LockSupport;

/**
 * This thread connects to collector via {@link com.devexperts.qd.QDAgent agent}
 * and counts number of received records.
 *
 * @see NetTestConsumerSide
 * @see NetTestWorkingThread
 */
class NetTestConsumerAgentThread extends NetTestWorkingThread {

    private static final Logging log = Logging.getLogging(NetTestConsumerAgentThread.class);
    private static final int MILLIS_SHIFT = 22;

    public interface LatencyFunction {
        long getLatency(RecordCursor cursor, long currentTimeMillis);
    }

    private final QDAgent[] agents;
    private final LatencyFunction latencyFunc;
    private long currentTimeMillis;
    private long currentRecords;
    private long currentLatency;

    NetTestConsumerAgentThread(int index, NetTestConsumerSide side, QDEndpoint endpoint) {
        super("ConsumerAgentThread", index, side, endpoint);
        agents = endpoint.getCollectors().stream().map(c -> c.agentBuilder().build()).toArray(QDAgent[]::new);
        latencyFunc = createLatencyFunction();
    }
    
    private LatencyFunction createLatencyFunction() {
        int indexFieldTimeMillis = -1;
        int indexFieldTimeSecond = -1;
        int indexFieldSequence = -1;
        for (int j = 0; j < NetTestSide.RECORD.getIntFieldCount(); j++) {
            DataIntField intField = NetTestSide.RECORD.getIntField(j);
            if (intField.getName().endsWith("Time")) {
                if (intField.getSerialType().isLong() && indexFieldTimeMillis == -1) {
                    indexFieldTimeMillis = intField.getIndex();
                } else if (!intField.getSerialType().isLong() && indexFieldTimeSecond == -1) {
                    indexFieldTimeSecond = intField.getIndex();
                }
            }
            if (intField.getName().endsWith("Sequence") && indexFieldSequence == -1)
                indexFieldSequence = intField.getIndex();
            if (intField.getSerialType().isLong())
                j++; // skip the next VoidIntField
        }
        final int finalIndexFieldTimeMillis = indexFieldTimeMillis;
        final int finalIndexFieldTimeSecond = indexFieldTimeSecond;
        final int finalIndexFieldSequence = indexFieldSequence;

        if (finalIndexFieldTimeMillis != -1) {
            return (cursor, currentTimeMillis) -> currentTimeMillis - cursor.getLong(finalIndexFieldTimeMillis);
        } else if (finalIndexFieldTimeSecond != -1 && finalIndexFieldSequence != -1) {
            return (cursor, currentTimeMillis) -> currentTimeMillis -
                (cursor.getInt(finalIndexFieldTimeSecond) * 1000L +
                (cursor.getInt(finalIndexFieldSequence) >>> MILLIS_SHIFT));
        } else {
            return (cursor, currentTimeMillis) -> 0;
        }
    }
    
    @Override
    public void run() {
        subscribe();
        AbstractRecordSink sink = new AbstractRecordSink() {
            @Override
            public void append(RecordCursor cursor) {
                currentRecords++;
                currentLatency += latencyFunc.getLatency(cursor, currentTimeMillis);
            }

            @Override
            public boolean hasCapacity() {
                return currentRecords < 100;
            }
        };

        RecordListener listener = provider -> LockSupport.unpark(NetTestConsumerAgentThread.this);
        for (QDAgent agent : agents) {
            agent.setRecordListener(listener);
        }
        while (true) {
            for (QDAgent agent : agents) {
                boolean hasMore;
                do {
                    currentTimeMillis = System.currentTimeMillis();
                    currentRecords = 0;
                    currentLatency = 0;
                    hasMore = agent.retrieve(sink);
                    addStats(currentLatency, currentRecords);
                } while (hasMore);
            }
            LockSupport.park();
        }
    }

    private void subscribe() {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        int num = 0;
        if (side.config.wildcard) {
            sub.add(NetTestSide.RECORD, NetTestSide.SCHEME.getCodec().getWildcardCipher(), null);
        } else {
            SymbolList subList = side.createSublist();
            for (int i = 0; i < subList.size(); i++) {
                sub.add(NetTestSide.RECORD, subList.getCipher(i), subList.getSymbol(i));
            }
            num = subList.size();
        }
        for (QDAgent agent : agents) {
            agent.setSubscription(sub);
            sub.rewind();
        }
        sub.release();
        log.info("Consumer #" + index + " subscribed to " +
            (side.config.wildcard ? "wildcard" : num + " symbols"));
    }

}
