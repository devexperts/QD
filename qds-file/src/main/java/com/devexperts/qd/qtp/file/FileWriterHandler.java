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
package com.devexperts.qd.qtp.file;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.qtp.AbstractConnectionHandler;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.MessageListener;
import com.devexperts.qd.qtp.MessageProvider;
import com.devexperts.qd.qtp.ProtocolOption;
import com.devexperts.qd.stats.QDStats;

public class FileWriterHandler extends AbstractConnectionHandler<TapeConnector> {
    private static final Logging log = Logging.getLogging(FileWriterHandler.class);

    private final FileWriterImpl writer;
    private final MessageAdapter adapter;

    private final State state = new State();

    public FileWriterHandler(TapeConnector connector) {
        super(connector);
        MessageAdapter.Factory factory = MessageConnectors.retrieveMessageAdapterFactory(connector.getFactory());
        adapter = factory.createAdapter(connector.getStats().getOrCreate(QDStats.SType.CONNECTIONS));
        setRemoteOptSet(adapter, ProtocolOption.parseProtocolOptions(connector.getOpt()));
        writer = new FileWriterImpl(connector.getAddress(), adapter.getScheme(), connector);
    }

    public void init() {
        adapter.setMessageListener(state);
        adapter.start();
        // note: open by itself cannot fail. The actual files are open when we start writing there.
        writer.open();
        subscribe();
    }

    private void subscribe() {
        RecordBuffer buf = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        DataScheme scheme = adapter.getScheme();
        for (int i = 0; i < scheme.getRecordCount(); i++) {
            buf.add(scheme.getRecord(i), scheme.getCodec().getWildcardCipher(), null);
        }
        adapter.processStreamAddSubscription(buf);
        buf.release();
    }

    @Override
    protected void doWork() throws InterruptedException {
        while (true) {
            if (isClosed())
                return; // bail out if closed
            state.awaitAvailable();
            if (isClosed())
                return; // bail out if closed
            boolean hasMore = true;
            try {
                hasMore = adapter.retrieveMessages(writer);
            } finally {
                state.processed(hasMore);
            }
        }
    }

    void awaitProcessed() throws InterruptedException {
        state.awaitProcessed();
    }

    @Override
    protected void closeImpl(Throwable reason) {
        writer.close();
        try {
            adapter.close();
        } catch (Throwable t) {
            log.error("Failed to close adapter", t);
        }
        state.close();
        if (reason == null || reason instanceof RuntimeException || reason instanceof Error) {
            // QTP worker thread had already logged any unchecked exceptions
            log.info("Writing stopped");
        } else {
            log.error("Writing stopped", reason);
        }
    }

    private static class State implements MessageListener {
        private volatile boolean messagesAreAvailable = true;
        private volatile boolean processed = false;

        State() {}

        // messagesAvailable() could be invoked by several concurrent threads at a time.
        public void messagesAvailable(MessageProvider provider) {
            if (!messagesAreAvailable)
                notifyAvailableSync();
        }

        private synchronized void notifyAvailableSync() {
            if (!messagesAreAvailable) {
                messagesAreAvailable = true;
                processed = false;
                notifyAll();
            }
        }

        synchronized void close() {
            // Do whatever to wakeup all waiting threads.
            messagesAreAvailable = true;
            processed = true;
            notifyAll();
        }

        // awaitProcessed() could be invoked by several concurrent threads at a time.
        synchronized void awaitProcessed() throws InterruptedException {
            while (!processed)
                wait();
        }

        // awaitAvailable() and processed() are supposed to be invoked in pairs by a SINGLE working thread.
        synchronized void awaitAvailable() throws InterruptedException {
            while (!messagesAreAvailable)
                wait();
            messagesAreAvailable = false;
        }

        // awaitAvailable() and processed() are supposed to be invoked in pairs by a SINGLE working thread.
        synchronized void processed(boolean hasMore) {
            if (hasMore)
                messagesAreAvailable = true;
            if (!messagesAreAvailable) {
                processed = true;
                notifyAll();
            }
        }
    }
}
