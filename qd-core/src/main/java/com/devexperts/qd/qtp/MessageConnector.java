/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2020 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.stats.QDStats;

/**
 * Implemented by every QTP message connector.
 */
public interface MessageConnector extends MessageConnectorMBean {
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
     * @implSpec
     * Default implementation does nothing.
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
}
