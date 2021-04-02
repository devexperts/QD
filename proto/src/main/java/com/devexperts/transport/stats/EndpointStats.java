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
package com.devexperts.transport.stats;

/**
 * Statistics for an endpoint including all connections (past and active).
 */
public class EndpointStats extends ConnectionStats {
    private static final long serialVersionUID = 0;

    private volatile long activeConnectionCount;
    private volatile long closedConnectionCount;

    public EndpointStats() {
    }

    public EndpointStats(EndpointStats other) {
        super(other);
        activeConnectionCount = other.activeConnectionCount;
        closedConnectionCount = other.closedConnectionCount;
    }

    public EndpointStats(EndpointStats cur, EndpointStats old) {
        super(cur, old);
        activeConnectionCount = cur.activeConnectionCount;
        closedConnectionCount = cur.closedConnectionCount - old.closedConnectionCount;
    }

    public long getActiveConnectionCount() {
        return activeConnectionCount;
    }

    public void setActiveConnectionCount(long activeConnectionCount) {
        this.activeConnectionCount = activeConnectionCount;
    }

    public void addActiveConnectionCount(long activeConnectionCount) {
        this.activeConnectionCount += activeConnectionCount;
    }

    public long getClosedConnectionCount() {
        return closedConnectionCount;
    }

    public void setClosedConnectionCount(long closedConnectionCount) {
        this.closedConnectionCount = closedConnectionCount;
    }

    public void addClosedConnectionCount(long closedConnectionCount) {
        this.closedConnectionCount += closedConnectionCount;
    }

    public void addEndpointStats(EndpointStats stats) {
        this.activeConnectionCount += stats.activeConnectionCount;
        this.closedConnectionCount += stats.closedConnectionCount;
        addConnectionStats(stats);
    }

    public String toString() {
        return "active " + activeConnectionCount + ", closed " + closedConnectionCount + ", " + super.toString();
    }
}
