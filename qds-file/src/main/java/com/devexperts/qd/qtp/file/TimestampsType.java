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
package com.devexperts.qd.qtp.file;

/**
 * Type of timestamps to store.
*/
public enum TimestampsType {
    /**
     * Do not store timestamp.
     */
    NONE(false, false, false),

    /**
     * Store a separate ".time" file with "&lt;long-time-millis>:&lt;position>" records.
     */
    LONG(true, false, true),

    /**
     * Store a separate ".time" file with "&lt;text-time-millis>:&lt;position>" records.
     */
    TEXT(true, false, true),

    /**
     * Store timestamp in data messages (only) in a separate "EventTime" and "EventSequence" fields.
     */
    FIELD(false, true, false),

    /**
     * Store timestamp in separate message per block of data.
     */
    MESSAGE(false, true, true);

    private final boolean usingTimeFile;
    private final boolean usingEmbeddedTime;
    private final boolean slipMessageOnTime;

    TimestampsType(boolean usingTimeFile, boolean usingEmbeddedTime, boolean slipMessageOnTime) {
        this.usingTimeFile = usingTimeFile;
        this.usingEmbeddedTime = usingEmbeddedTime;
        this.slipMessageOnTime = slipMessageOnTime;
    }

    /**
     * Returns {@code true} when event times are written to a separate ".time" file.
     */
    public boolean isUsingTimeFile() {
        return usingTimeFile;
    }

    /**
     * Returns {@code true} when event times are embedded into the data file.
     */
    public boolean isUsingEmbeddedTime() {
        return usingEmbeddedTime;
    }

    public boolean isSlipMessageOnTime() {
        return slipMessageOnTime;
    }
}
