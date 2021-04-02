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

import java.io.Serializable;

/**
 * Statistics for a connection.
 */
public class ConnectionStats implements Serializable {
    private static final long serialVersionUID = 0;

    private volatile long readBytes;
    private volatile long writtenBytes;

    public ConnectionStats() {
    }

    public ConnectionStats(ConnectionStats other) {
        readBytes = other.readBytes;
        writtenBytes = other.writtenBytes;
    }

    public ConnectionStats(ConnectionStats cur, ConnectionStats old) {
        readBytes = cur.readBytes - old.readBytes;
        writtenBytes = cur.writtenBytes - old.writtenBytes;
    }

    public long getReadBytes() {
        return readBytes;
    }

    public void setReadBytes(long readBytes) {
        this.readBytes = readBytes;
    }

    public void addReadBytes(long readBytes) {
        this.readBytes += readBytes;
    }

    public long getWrittenBytes() {
        return writtenBytes;
    }

    public void setWrittenBytes(long writtenBytes) {
        this.writtenBytes = writtenBytes;
    }

    public void addWrittenBytes(long writtenBytes) {
        this.writtenBytes += writtenBytes;
    }

    public void addConnectionStats(ConnectionStats stats) {
        this.readBytes += stats.readBytes;
        this.writtenBytes += stats.writtenBytes;
    }

    public String toString() {
        return "read " + readBytes + ", written " + writtenBytes;
    }
}
