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
package com.devexperts.qd.monitoring;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for monitoring counters.
 */
public class MonitoringCounter extends AtomicLong {
    public void add(long value) {
        addAndGet(value);
    }

    /**
     * Updates value to the newValue, returns the difference between newValue and old value.
     */
    public long update(long newValue) {
        long oldValue = getAndSet(newValue);
        return newValue - oldValue;
    }
}
