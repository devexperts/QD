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

/**
 * Helper class to manage reconnection delay for message connectors.
 * A new instance of this class is required to track reconnection "from scratch" which
 * usually happens when a connector is restarted via user action.
 * This class should be used only from <code>com.devexperts.qd.qtp</code> subpackages.
 */
public final class ReconnectHelper {
    private volatile long delay;
    private volatile long startTime;

    public ReconnectHelper(long delay) {
        this.delay = delay;
    }

    /**
     * Change the delay period for the helper. Won't affect a thread currently sleeping using this helper.
     * @param delay reconnect delay period (millis)
     */
    public void setReconnectDelay(long delay) {
        this.delay = delay;
    }

    public void sleepBeforeConnection() throws InterruptedException {
        long startTime = this.startTime; // atomic read
        long delay = this.delay; // atomic read
        long worked = System.currentTimeMillis() - startTime;
        long sleepTime = worked >= delay ? 0 : (long) ((delay - worked) * (1.0 + Math.random()));
        if (sleepTime > 0)
            Thread.sleep(sleepTime);
        this.startTime = System.currentTimeMillis();
    }

    public void reset() {
        startTime = 0;
    }
}
