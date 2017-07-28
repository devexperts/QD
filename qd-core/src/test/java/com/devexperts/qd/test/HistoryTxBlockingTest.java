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

import com.devexperts.qd.QDAgent;
import com.devexperts.qd.ng.*;

/**
 * The same test suite as in {@link HistoryTxTest}, but buffer size is limited to 1
 * and blocking is configured, while the actual buffer is off-loaded to a separate buffer.
 * This way buffer blocking in History is stress-tested.
 */
public class HistoryTxBlockingTest extends HistoryTxTest {
    private RecordBuffer buf;
    private RecordListener delegateListener;
    private AbstractRecordProvider provider;

    public HistoryTxBlockingTest() {
        blocking = true;
    }

    @Override
    RecordProvider getProvider(final QDAgent agent) {
        agent.setBufferOverflowStrategy(QDAgent.BufferOverflowStrategy.BLOCK);
        agent.setMaxBufferSize(1);
        // our buffer
        buf = RecordBuffer.getInstance(agent.getMode());
        // our provider
        provider = new AbstractRecordProvider() {
            @Override
            public RecordMode getMode() {
                return agent.getMode();
            }

            @Override
            public boolean retrieve(RecordSink sink) {
                return buf.retrieve(sink);
            }

            @Override
            public void setRecordListener(RecordListener listener) {
                delegateListener = listener;
                if (listener != null && buf.hasNext())
                    listener.recordsAvailable(provider);
            }
        };
        // will mimic conflation logic
        final RecordSink conflatingSink = new AbstractRecordSink() {
            long lastPosition = -1;
            @Override
            public void append(RecordCursor cursor) {
                if (lastPosition >= buf.getPosition()) {
                    RecordCursor writeCursor = buf.writeCursorAt(lastPosition);
                    if (writeCursor.getTime() == cursor.getTime()) {
                        // conflate
                        writeCursor.setEventFlags(cursor.getEventFlags());
                        writeCursor.copyDataFrom(cursor);
                        return;
                    }
                }
                lastPosition = buf.getLimit();
                buf.append(cursor);
            }
        };
        // install agent's listener
        agent.setRecordListener(p -> {
            boolean wasEmpty = !buf.hasNext();
            agent.retrieve(conflatingSink);
            if (wasEmpty && buf.hasNext() && delegateListener != null)
                delegateListener.recordsAvailable(provider);
        });
        return provider;
    }

    @Override
    void closeAgent() {
        super.closeAgent();
        delegateListener = null;
    }
}
