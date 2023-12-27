/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.SubscriptionVisitor;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.qtp.AbstractMessageVisitor;
import com.devexperts.qd.qtp.MessageListener;
import com.devexperts.qd.qtp.MessageProvider;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.MessageVisitor;
import com.devexperts.qd.qtp.OutputStreamMessageVisitor;
import com.devexperts.qd.qtp.ProtocolOption;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.qtp.file.FileWriterImpl;
import com.devexperts.qd.qtp.text.TextQTPComposer;
import com.devexperts.util.InvalidFormatException;

import java.io.Closeable;
import java.util.Set;

final class ConnectionProcessor extends Thread implements Closeable, ConnectorRecordsSymbols.Listener, MessageListener {

    private static final Logging log = Logging.getLogging(ConnectionProcessor.class);

    private final RecordBuffer localBuf = new RecordBuffer(RecordMode.FLAGGED_DATA);

    private final Object[] queue = new Object[(QDContract.values().length * 2 + 1) * 2];
    private int queueHead = 0;
    private int queueTail = 0;
    private boolean closed;

    private final FileWriterImpl tape;
    private final OutputStreamMessageVisitor consoleWriter;
    private final TopSymbolsCounter topSymbolsCounter;

    @SuppressWarnings({"unchecked"})
    ConnectionProcessor(QDEndpoint endpoint, String tapeName, boolean quiet, boolean stamp,
        RecordFields[] rfs, TopSymbolsCounter topSymbolsCounter)
        throws InvalidFormatException
    {
        this(endpoint.getName(), endpoint.getScheme(), endpoint.getContracts(), tapeName, quiet, stamp, rfs,
            topSymbolsCounter);
    }

    ConnectionProcessor(String name, DataScheme scheme, Set<QDContract> contracts, String tapeName, boolean quiet,
        boolean stamp, RecordFields[] rfs, TopSymbolsCounter topSymbolsCounter) throws InvalidFormatException
    {
        super(name);
        tape = tapeName != null ? FileWriterImpl.open(tapeName, scheme) : null;
        TextQTPComposer composer = stamp || rfs != null ? new StampComposer(scheme, rfs) : new TextQTPComposer(scheme);
        composer.setOptSet(ProtocolOption.SUPPORTED_SET); // use available protocol options
        this.consoleWriter = quiet ? null : new OutputStreamMessageVisitor(System.out, composer, true);
        this.topSymbolsCounter = topSymbolsCounter;

        if (tape != null) {
            for (QDContract contract : contracts) {
                tape.addSendMessageType(MessageType.forData(contract));
            }
        }
    }

    // waits until processing is finished on close
    @Override
    public void close() {
        synchronized (this) {
            closed = true;
            notifyAll();
        }
        try {
            join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (tape != null)
            tape.close();
    }

    @Override
    public void recordsAvailable(RecordProvider provider, MessageType message) {
        enqueue(provider, message);
    }

    @Override
    public void messagesAvailable(MessageProvider adapter) {
        enqueue(adapter, null);
    }

    private synchronized void enqueue(Object provider, MessageType message) {
        for (int i = queueHead; i != queueTail; i = (i + 2) % queue.length)
            if (queue[i] == provider)
                return;
        queue[queueTail++] = provider;
        queue[queueTail++] = message;
        if (queueTail >= queue.length)
            queueTail = 0;
        notifyAll();
    }

    private synchronized boolean dequeue(Object[] result) throws InterruptedException {
        while (queueTail == queueHead && !closed)
            wait();
        if (queueHead == queueTail)
            return false; // return "closed" only when queue is empty (processing finished)
        result[0] = queue[queueHead++];
        result[1] = queue[queueHead++];
        if (queueHead >= queue.length)
            queueHead = 0;
        return true;
    }

    @Override
    public void run() {
        Object[] result = new Object[2];
        while (!Thread.currentThread().isInterrupted())
            try {
                if (!dequeue(result))
                    return;
                try {
                    if (result[0] instanceof MessageProvider) {
                        ((MessageProvider) result[0]).retrieveMessages(dataVisitor);
                    } else {
                        process((RecordProvider) result[0], (MessageType) result[1]);
                    }
                } catch (Exception e) {
                    log.error("Unexpected exception while processing data for " + result[0], e);
                    // enqueue here again, because otherwise this agent may not notify again
                    enqueue(result[0], (MessageType) result[1]);
                }
            } catch (InterruptedException e) {
                // interrupted -- return
            }
    }

    final MessageVisitor dataVisitor = new AbstractMessageVisitor() {
        @Override
        public boolean visitData(DataProvider provider, MessageType message) {
            return process((RecordProvider) provider, message);
        }

        @Override
        public boolean visitSubscription(SubscriptionProvider provider, MessageType message) {
            return provider.retrieveSubscription(SubscriptionVisitor.VOID);
        }
    };

    private boolean process(RecordProvider provider, MessageType message) {
        boolean hasMore = false;
        localBuf.clear();
        if ((tape == null ? 0 : 1) + (consoleWriter == null ? 0 : 1) + (topSymbolsCounter == null ? 0 : 1) != 1) {
            // if there are exactly 1 consumer - we use original provider, otherwise we use localBuf for rewind
            hasMore = provider.retrieve(localBuf);
            provider = localBuf;
        }
        if (tape != null) {
            processData(tape, provider, message);
            localBuf.rewind();
        }
        if (consoleWriter != null) {
            processData(consoleWriter, provider, message);
            localBuf.rewind();
        }
        if (topSymbolsCounter!= null)
            processData(topSymbolsCounter, provider, message);
        localBuf.clear();
        return hasMore;
    }

    private void processData(AbstractMessageVisitor visitor, RecordProvider data, MessageType message) {
        //noinspection StatementWithEmptyBody
        while (visitor.visitData(data, message)) {
            // process until everything is processed.
        }
    }

    @Override
    public String toString() {
        // This string is shown in the log when connection processor is being closed on exit
        return "connection processor for " + getName();
    }
}
