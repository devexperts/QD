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
package com.devexperts.qd.qtp.file;

import com.devexperts.io.StreamOutput;
import com.devexperts.qd.DataIterator;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.qtp.AbstractQTPComposer;
import com.devexperts.qd.qtp.HeartbeatPayload;
import com.devexperts.qd.qtp.MessageConsumerAdapter;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.RuntimeQTPException;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes messages from {@link MessageReader} to a specified {@link OutputStream} using a specified QTP composer.
 * Use {@link #write(ProtocolDescriptor)} method to do the actual writing.
 * This class is not thread-safe and is designed for single-thread usage only.
 */
public class OutputStreamMessageConsumer {
    private final StreamOutput out = new StreamOutput();
    private final MessageReader reader;
    private final AbstractQTPComposer composer;
    private final Consumer consumer = new Consumer();
    private final Provider provider = new Provider();

    public OutputStreamMessageConsumer(OutputStream out, MessageReader reader, AbstractQTPComposer composer) {
        this.out.setOutput(out);
        this.reader = reader;
        this.composer = composer;
        this.composer.setOutput(this.out);
    }

    public void write(ProtocolDescriptor descriptor) throws InterruptedException, IOException {
        composer.visitDescribeProtocol(descriptor);
        reader.readInto(consumer);
        composer.composeEmptyHeartbeat(); // to wrap off
        out.flush();
    }

    protected boolean acceptCursor(QDContract contract, RecordCursor cursor) {
        return true;
    }

    private class Consumer extends MessageConsumerAdapter {
        @Override
        public void handleCorruptedStream() {
            super.handleCorruptedStream();
            reader.close();
        }

        @Override
        public void handleCorruptedMessage(int messageTypeId) {
            super.handleCorruptedMessage(messageTypeId);
            reader.close();
        }

        @Override
        public void handleUnknownMessage(int messageTypeId) {
            super.handleUnknownMessage(messageTypeId);
            reader.close();
        }

        @Override
        public void processTimeProgressReport(long timeMillis) {
            composer.composeTimeProgressReport(timeMillis);
            // actually flush underlying output
            try {
                out.flush();
            } catch (IOException e) {
                throw new RuntimeQTPException(e);
            }
        }

        @Override
        public void processHeartbeat(HeartbeatPayload heartbeatPayload) {
            composer.visitHeartbeat(heartbeatPayload);
        }

        @Override
        protected void processData(DataIterator iterator, MessageType message) {
            provider.contract = message.getContract();
            provider.source = (RecordSource) iterator; // we know they are all record sources
            while (composer.visitData(provider, message))
                /* just loop and create data messages */;
        }
    }

    private class Provider extends AbstractRecordProvider {
        QDContract contract;
        RecordSource source;

        @Override
        public RecordMode getMode() {
            return source.getMode();
        }

        @Override
        public boolean retrieve(RecordSink sink) {
            while (sink.hasCapacity()) {
                RecordCursor cursor;
                while (true) {
                    cursor = source.next();
                    if (cursor == null)
                        return false; // retrieved everything
                    if (acceptCursor(contract, cursor))
                        break; // accepted cursor
                }
                sink.append(cursor);
            }
            return true;
        }
    }
}
