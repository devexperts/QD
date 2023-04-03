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
package com.devexperts.qd.qtp;

import com.devexperts.transport.stats.ConnectionStats;

public abstract class AbstractConnectionHandler<C extends AbstractMessageConnector> extends QTPWorkerThread {
    protected final C connector;
    protected final String address;
    protected final ConnectionStats connectionStats = new ConnectionStats();

    private volatile MessageConnectorState state = MessageConnectorState.CONNECTING;

    public static interface Factory {
        public AbstractConnectionHandler<AbstractMessageConnector>
            createHandler(String protocol, AbstractMessageConnector connector);
    }

    protected AbstractConnectionHandler(C connector) {
        super(connector.getName());
        this.connector = connector;
        this.address = connector.getAddress();
    }

    public final ConnectionStats getConnectionStats() {
        return connectionStats;
    }

    public final MessageConnectorState getHandlerState() {
        return state;
    }

    protected final boolean makeConnected() {
        synchronized (this) {
            if (state != MessageConnectorState.CONNECTING)
                return false;
            state = MessageConnectorState.CONNECTED;
        }
        connector.notifyMessageConnectorListeners();
        return true;
    }

    @Override
    protected final void handleShutdown() {
        connector.stop();
    }

    @Override
    protected final void handleClose(Throwable reason) {
        state = MessageConnectorState.DISCONNECTED;
        try {
            closeImpl(reason);
        } finally {
            connector.handlerClosed(this);
            connector.addClosedConnectionStats(connectionStats);
            connector.notifyMessageConnectorListeners();
        }
    }

    protected abstract void closeImpl(Throwable reason);

    // helper method for configuring adapters
    protected void setRemoteOptSet(MessageAdapter adapter, ProtocolOption.Set optSet) {
        adapter.setRemoteOptSet(optSet);
    }
}
