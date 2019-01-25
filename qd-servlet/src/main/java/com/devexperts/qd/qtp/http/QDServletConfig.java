/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.http;

import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.transport.stats.EndpointStats;
import com.devexperts.util.TimeUtil;

public abstract class QDServletConfig {
    public static final long DEFAULT_CONNECTION_TIMEOUT = TimeUtil.MINUTE;

    private final EndpointStats closedConnectionsStats = new EndpointStats();

    public abstract MessageAdapter.Factory getMessageAdapterFactory();

    /**
     * Returns stats for this servlet. Stats should be of type {@link QDStats.SType#QD_SERVLET} or
     * a suitable substitute. This implementation returns {@link QDStats#VOID}.
     */
    public QDStats getStats() {
        return QDStats.VOID;
    }

    /**
     * Returns connection timeout value in milliseconds.
     * This implementaion returns {@link #DEFAULT_CONNECTION_TIMEOUT}.
     */
    public long getConnectionTimeout() {
        return DEFAULT_CONNECTION_TIMEOUT;
    }

    public EndpointStats getEndpointStats() {
        EndpointStats stats = new EndpointStats();
        stats.addEndpointStats(closedConnectionsStats);
        return stats;
    }

    void addClosedConnectionStats(long readBytes, long writtenBytes) {
        closedConnectionsStats.addClosedConnectionCount(1);
        closedConnectionsStats.addReadBytes(readBytes);
        closedConnectionsStats.addWrittenBytes(writtenBytes);
    }
}
