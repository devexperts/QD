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
package com.dxfeed.webservice.comet;

import com.devexperts.util.TimePeriod;

import java.util.Comparator;
import java.util.Objects;

class SessionStats implements Cloneable {

    public String sessionId;
    public volatile int numSessions;
    public volatile long createTime = -1;
    public volatile long lastActiveTime = -1;
    public volatile long lastSendTime = -1;

    public volatile int maxQueueSize;
    public volatile int queueSize;
    public volatile int subSize;
    public volatile int subTimeSeriesSize;

    public volatile long writeEvents;
    public volatile long write;
    public volatile long writeMeta;
    public volatile long readEvents;
    public volatile long read;
    public volatile long readMeta;

    public static Comparator<SessionStats> getComparator(String column) {
        switch (Objects.requireNonNull(column).trim().toLowerCase()) {
        case "id":
            return Comparator.comparing((SessionStats stats) -> stats.sessionId);
        case "read_mps":
            return Comparator.comparing((SessionStats stats) -> stats.readEvents).reversed();
        case "read":
            return Comparator.comparing((SessionStats stats) -> stats.read + stats.readMeta).reversed();
        case "write_mps":
            return Comparator.comparing((SessionStats stats) -> stats.writeEvents).reversed();
        case "write":
            return Comparator.comparing((SessionStats stats) -> stats.write + stats.writeMeta).reversed();
        case "queue":
            return Comparator.comparing((SessionStats stats) -> stats.queueSize).reversed();
        case "time":
            return Comparator.comparing((SessionStats stats) -> stats.createTime);
        case "inactivity":
            return Comparator.comparing((SessionStats stats) -> stats.lastActiveTime);
        case "send_inactivity":
            return Comparator.comparing((SessionStats stats) -> stats.lastSendTime);
        }
        throw new IllegalArgumentException("Unknown sort column: " + column);
    }

    public void regSubscription(int size, boolean timeSeries) {
        if (timeSeries) {
            subTimeSeriesSize = size;
        } else {
            subSize = size;
        }
    }

    public void regQueueSize(int size) {
        queueSize = size;
        if (size > maxQueueSize)
            maxQueueSize = size;
    }

    public void clear() {
        numSessions = 0;
        createTime = 0;
        lastActiveTime = 0;

        maxQueueSize = 0;
        queueSize = 0;
        subSize = 0;
        subTimeSeriesSize = 0;
        writeEvents = 0;
        write = 0;
        writeMeta = 0;
        readEvents = 0;
        read = 0;
        readMeta = 0;
    }

    @Override
    public SessionStats clone() {
        try {
            return (SessionStats) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new InternalError(e);
        }
    }

    public void accumulate(SessionStats other, boolean up) {
        int maxSize = other.maxQueueSize;
        if (maxSize > maxQueueSize)
            maxQueueSize = maxSize;

        sessionId = other.sessionId;

        int sizeMultiplier = up ? 1 : 0;
        queueSize += other.queueSize * sizeMultiplier;
        subSize += other.subSize * sizeMultiplier;
        subTimeSeriesSize += other.subTimeSeriesSize * sizeMultiplier;
        numSessions += other.numSessions * sizeMultiplier;
        createTime += other.createTime * sizeMultiplier;
        lastActiveTime += other.lastActiveTime * sizeMultiplier;
        lastSendTime += other.lastSendTime * sizeMultiplier;

        int multiplier = up ? 1 : -1;
        writeEvents += other.writeEvents * multiplier;
        write += other.write * multiplier;
        writeMeta += other.writeMeta * multiplier;
        readEvents += other.readEvents * multiplier;
        read += other.read * multiplier;
        readMeta += other.readMeta * multiplier;
    }

    public String getTotalRated(int sessions, double period) {
        return "Sessions: " + numSessions + ";"
            + " Sub: " + getRated(subSize, sessions) + ";"
            + " SubTs: " + getRated(subTimeSeriesSize, sessions) + ";"
            + " Queue: " + getRated(queueSize, sessions) + ", max " + maxQueueSize + ";"
            + " Write: " + getRated(writeEvents, period) + " mps, "
            + getRated(write, period) + " pps, meta " + getRated(writeMeta, period) + " pps;"
            + " Read: " + getRated(readEvents, period) + " mps, "
            + getRated(read, period) + " pps, meta " + getRated(readMeta, period) + " pps;";
    }

    public String getRated(double period) {
        return "Sub: " + subSize + ";"
            + " SubTs: " + subTimeSeriesSize + ";"
            + " Queue: " + queueSize + ", max " + maxQueueSize + ";"
            + " Write: " + getRated(writeEvents, period) + " mps, "
            + getRated(write, period) + " pps, meta " + getRated(writeMeta, period) + " pps;"
            + " Read: " + getRated(readEvents, period) + " mps, "
            + getRated(read, period) + " pps, meta " + getRated(readMeta, period) + " pps;";
    }

    protected void dumpStats(StringBuilder buff, double period, long currentTime) {
        buff.append(String.format("\n%30s", sessionId));
        if (currentTime != 0) {
            long inactivity = currentTime - lastActiveTime;
            long send = currentTime - lastSendTime;
            buff.append(" ").append(periodToString(inactivity))
                .append("(").append(periodToString(send))
                .append(")/").append(TimePeriod.valueOf(currentTime - createTime));
        }
        buff.append(" - ").append(getRated(period));
    }

    private static String periodToString(long time) {
        return (time > 10000) ? TimePeriod.valueOf(time).toString() : Long.toString(time);
    }

    public static double getRated(long k, double period) {
        if (k <= 0 || period <= 0)
            return 0;
        double d = k / period;
        return d <= 9.99 ? Math.max(Math.floor(d * 100 + 0.5) / 100, 0.01) :
            d <= 99.9 ? Math.floor(d * 10 + 0.5) / 10 : Math.floor(d + 0.5);
    }
}
