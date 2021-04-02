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
package com.devexperts.qd.qtp;

import com.devexperts.io.StreamOutput;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.SubscriptionProvider;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Writes messages into a specified {@link OutputStream} using a specified QTP composer.
 * It typically used to format text onto the console. This class is thread-safe.
 * It contains its own synchronization and can be used by multiple threads.
 */
public class OutputStreamMessageVisitor extends AbstractMessageVisitor
    implements MessageListener, Closeable, Flushable
{
    private final StreamOutput out;
    private final AbstractQTPComposer composer;
    private final boolean autoFlush;

    /**
     * Creates output stream message visitor writing to a specified output stream with a specified composer.
     *
     * @param out the output stream.
     * @param composer the composer.
     * @param autoFlush if {@code true}, then flushes composed bytes to an underlying output stream after each packet.
     */
    public OutputStreamMessageVisitor(OutputStream out, AbstractQTPComposer composer, boolean autoFlush) {
        this.out = new StreamOutput(out);
        this.composer = composer;
        this.autoFlush = autoFlush;
        this.composer.setOutput(this.out);
    }

    @Override
    public synchronized void visitHeartbeat(HeartbeatPayload heartbeatPayload) {
        composer.visitHeartbeat(heartbeatPayload);
        flushIfNeeded();
    }

    @Override
    public synchronized boolean visitData(DataProvider provider, MessageType message) {
        boolean result = composer.visitData(provider, message);
        flushIfNeeded();
        return result;
    }

    @Override
    public synchronized boolean visitSubscription(SubscriptionProvider provider, MessageType message) {
        boolean result = composer.visitSubscription(provider, message);
        flushIfNeeded();
        return result;
    }

    @Override
    public synchronized boolean visitOtherMessage(int messageType, byte[] messageBytes, int offset, int length) {
        boolean result = composer.visitOtherMessage(messageType, messageBytes, offset, length);
        flushIfNeeded();
        return result;
    }

    public synchronized void close() throws IOException {
        out.close();
    }

    public synchronized void flush() throws IOException {
        out.flush();
    }

    public synchronized void messagesAvailable(MessageProvider provider) {
        provider.retrieveMessages(this);
    }

    private void flushIfNeeded() {
        if (autoFlush)
            try {
                out.flush();
            } catch (IOException e) {
                throw new RuntimeQTPException(e);
            }
    }
}
