/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.file;

import com.devexperts.qd.qtp.AbstractConnectionHandler;
import com.devexperts.qd.qtp.AbstractMessageConnector;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.stats.QDStats;

public class FileReaderHandler extends AbstractConnectionHandler<AbstractMessageConnector> {
    // --------- factory class ---------

    public static class Factory implements AbstractConnectionHandler.Factory {
        @Override
        public AbstractConnectionHandler<AbstractMessageConnector>
            createHandler(String protocol, AbstractMessageConnector connector)
        {
            return protocol.equals("file") ? new FileReaderHandler(connector) : null;
        }
    }

    // --------- instance ---------

    private final FileReader reader;
    private final MessageAdapter adapter;

    public FileReaderHandler(AbstractMessageConnector connector) {
        super(connector);
        // Note: This connector can be either FileConnector or HttpConnector, thus the following code
        FileReaderParams params = connector instanceof FileReaderParams ?
            (FileReaderParams) connector : new FileReaderParams.Default();
        MessageAdapter.Factory factory = MessageConnectors.retrieveMessageAdapterFactory(connector.getFactory());
        reader = new FileReader(connector.getAddress(), getConnectionStats(), params) {
            @Override
            protected void onConnected() {
                makeConnected();
            }
        };
        adapter = factory.createAdapter(params.getStats().getOrCreate(QDStats.SType.CONNECTIONS));
        adapter.start();
        reader.setScheme(adapter.getScheme());
    }

    @Override
    protected void doWork() throws InterruptedException {
        reader.readInto(adapter);
    }

    @Override
    protected void closeImpl(Throwable reason) {
        reader.close();
        try {
            adapter.close();
        } catch (Throwable t) {
            log.error("Failed to close adapter", t);
        }
        if (reason == null || reason instanceof RuntimeException || reason instanceof Error)
            // QTP worker thread had already logged any unchecked exceptions
            log.info("Reading stopped");
        else
            // This can be, for example, EOFException while trying to open broken .gz file
            log.error("Reading stopped", reason);
    }

    public long getDelayActual() {
        return reader.getDelayActual();
    }
}

