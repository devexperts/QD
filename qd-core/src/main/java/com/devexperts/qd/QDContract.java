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
package com.devexperts.qd;

import com.devexperts.qd.stats.QDStats;

import java.util.Locale;

/**
 * All available contracts for {@link QDCollector}.
 */
public enum QDContract implements QDCollector.Factory {
    TICKER(QDTicker.class),
    STREAM(QDStream.class),
    HISTORY(QDHistory.class);

    private final Class<? extends QDCollector> intf;
    private final String string;

    QDContract(Class<? extends QDCollector> intf) {
        this.intf = intf;
        StringBuilder sb = new StringBuilder(name().toLowerCase(Locale.US));
        sb.setCharAt(0, Character.toUpperCase(sb.charAt(0)));
        this.string = sb.toString();
    }

    public Class<? extends QDCollector> getInterface() {
        return intf;
    }

    @Override
    public QDContract getContract() {
        return this;
    }

    @Override
    public QDCollector createCollector(QDFactory factory, QDCollector.Builder<?> builder) {
        return factory.collectorBuilder(this).copyFrom(builder).build();
    }

    @Override
    public QDStats.SType getStatsType() {
        switch (this) {
        case TICKER:
            return QDStats.SType.TICKER;
        case STREAM:
            return QDStats.SType.STREAM;
        case HISTORY:
            return QDStats.SType.HISTORY;
        default:
            throw new AssertionError("cannot happen");
        }
    }

    public boolean hasTime() {
        return this == HISTORY;
    }

    public boolean hasSnapshotData() {
        return this != STREAM;
    }

    public boolean usesEventFlags() {
        return this == HISTORY;
    }

    public String toString() {
        return string;
    }
}
