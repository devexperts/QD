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
package com.devexperts.qd.util;

import com.devexperts.util.TimeUtil;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This is singleton for internal short periodic tasks. This class is based on {@link ScheduledExecutorService}.
 * Used one thread to process {@link Runnable} tasks. There are two modes of operation, a single launch with delay time
 * in the millis and daily periodic work in {@link LocalTime}. There is no guarantee of the exact launch time.
 */
public class DxTimer {

    /**
     * The result is triggered by the {@code DxTimer}, used only to cancel the scheduled task.
     * The behavior is very similar to {@link java.util.concurrent.Future#cancel(boolean)} with false argument.
     */
    public interface Cancellable {
        void cancel();
    }

    private static class CancellableTask implements Cancellable {

        private volatile boolean canceled;
        private volatile ScheduledFuture<?> future;

        private void setFuture(ScheduledFuture<?> future) {
            this.future = future;
            if (canceled) {
                future.cancel(false);
            }
        }

        @Override
        public void cancel() {
            canceled = true;
            future.cancel(false);
        }
    }

    private static class InstanceHolder {
        private static final DxTimer instance = new DxTimer(ZoneId.systemDefault());
    }

    /**
     * Lazy creation of a singleton instance {@code DxTimer}
     * @return {@code DxTimer} instance
     */
    public static DxTimer getInstance() {
        return DxTimer.InstanceHolder.instance;
    }

    private final ZoneId zoneId;
    private final ScheduledExecutorService scheduledExecutorService;

    private DxTimer(ZoneId zoneId) {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "DxTimer");
            thread.setDaemon(true);
            return thread;
        });

        executor.setKeepAliveTime(10, TimeUnit.SECONDS);
        executor.allowCoreThreadTimeOut(true);

        this.zoneId = zoneId;
        this.scheduledExecutorService = executor;
    }

    /**
     * Run one-time delayed {@code Runnable} task
     *
     * @param action {@code Runnable} task
     * @param delayMillis delay time in millis
     * @return {@code Cancellable}
     */
    public Cancellable runOnce(Runnable action, long delayMillis) {
        Objects.requireNonNull(action, "action");

        CancellableTask cancellableTask = new CancellableTask();
        cancellableTask.setFuture(scheduledExecutorService.schedule(action, delayMillis, TimeUnit.MILLISECONDS));
        return cancellableTask;
    }

    /**
     * Run periodic {@code Runnable} task that will start every day in {@code LocalTime}
     *
     * @param action {@code Runnable} task
     * @param localTime periodic daily invoke time
     * @return {@code Cancellable}
     */
    public Cancellable runDaily(Runnable action, LocalTime localTime) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(localTime, "localTime");

        final CancellableTask cancellableTask = new CancellableTask();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                action.run();
                if (!cancellableTask.canceled) {
                    scheduleTask(cancellableTask, this, localTime);
                }
            }
        };

        scheduleTask(cancellableTask, runnable, localTime);
        return cancellableTask;
    }

    private void scheduleTask(CancellableTask cancellableTask, Runnable runnable, LocalTime localTime) {
        synchronized (scheduledExecutorService) {
            long currentTime = System.currentTimeMillis();
            long invokeTime = TimeUtil.computeDailyTime(currentTime, localTime, zoneId);
            cancellableTask.setFuture(
                scheduledExecutorService.schedule(runnable, invokeTime - currentTime, TimeUnit.MILLISECONDS));
        }
    }
}
