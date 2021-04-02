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
package com.dxfeed.ipf.filter;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

class IPFUpdater {
    private IPFUpdater() {} // do not create

    private static final ScheduledThreadPoolExecutor EXECUTOR = new ScheduledThreadPoolExecutor(0, r -> {
        Thread thread = new Thread(r, "IPFUpdater");
        thread.setDaemon(true);
        return thread;
    });

    static {
        EXECUTOR.setMaximumPoolSize(1);
        EXECUTOR.setKeepAliveTime(100, TimeUnit.MILLISECONDS);
    }

    public static Future<?> track(IPFSymbolFilter filter) {
        long delay = filter.getUpdateMillis();
        // compute expected next check time (lastChecked + delay) and initialDelay based on that
        long initialDelay = Math.max(0, Math.min(delay, filter.getLastChecked() + delay - System.currentTimeMillis()));
        return EXECUTOR.scheduleWithFixedDelay(new UpdateTask(filter), initialDelay, delay, TimeUnit.MILLISECONDS);
    }

    private static class UpdateTask implements Runnable {
        private final IPFSymbolFilter filter;

        UpdateTask(IPFSymbolFilter filter) {
            this.filter = filter;
        }

        public void run() {
            filter.update();
        }
    }
}
