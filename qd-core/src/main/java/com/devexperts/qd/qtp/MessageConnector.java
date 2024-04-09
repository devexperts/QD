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
package com.devexperts.qd.qtp;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.stats.QDStats;

import java.net.UnknownHostException;

/**
 * Implemented by every QTP message connector.
 */
public interface MessageConnector extends MessageConnectorMBean {

    /**
     * Common interface for connectors supporting server socket binding
     *
     * @deprecated Experimental API. May be subject to backward-incompatible changes in the future.
     */
    public interface Bindable {
        public static final String ANY_BIND_ADDRESS = "*";

        public static String normalizeBindAddr(String bindAddress) {
            return (bindAddress == null || bindAddress.isEmpty() || bindAddress.equals(ANY_BIND_ADDRESS)) ?
                ANY_BIND_ADDRESS : bindAddress;
        }

        public String getBindAddr();

        public void setBindAddr(String bindAddress) throws UnknownHostException;
    }

    /**
     * Returns {@link QDStats} associated with this message connector.
     *
     * @return QDStats associated with this message connector
     */
    public QDStats getStats();

    /**
     * Changes {@link QDStats} associated with this message connector.
     */
    public void setStats(QDStats stats);

    /**
     * Adds the specified {@link MessageConnectorListener listener} to this message connector.
     *
     * @param listener newly adding {@link MessageConnectorListener}.
     */
    public void addMessageConnectorListener(MessageConnectorListener listener);

    /**
     * Removes the specified {@link MessageConnectorListener listener} from this message connector.
     *
     * @param listener removing {@link MessageConnectorListener}.
     */
    public void removeMessageConnectorListener(MessageConnectorListener listener);

    /**
     * Returns {@link ApplicationConnectionFactory} that is used by this message connector.
     * @return {@link ApplicationConnectionFactory} that is used by this message connector.
     */
    public ApplicationConnectionFactory getFactory();

    /**
     * Changes {@link ApplicationConnectionFactory} that is used by this message connector.
     * @param factory {@link ApplicationConnectionFactory} that will be used by this message connector.
     */
    public void setFactory(ApplicationConnectionFactory factory);

    /**
     * Waits until this connector stops processing (becomes quescient).
     *
     * @implSpec Default implementation does nothing.
     *
     * @throws InterruptedException if interrupted.
     */
    public default void awaitProcessed() throws InterruptedException {}

    /**
     * Stops connector and waits while all its threads are terminated.
     * @throws InterruptedException if interrupted.
     */
    public void stopAndWait() throws InterruptedException;

    /**
     * Returns total number of closed connector since the creation of connector.
     */
    public long getClosedConnectionCount();

    /**
     * Permanently closes connector and releases its resources.
     */
    public default void close() {
        stop();
        QDStats stats = getStats();
        if (stats != null)
            stats.close();
    }
}
