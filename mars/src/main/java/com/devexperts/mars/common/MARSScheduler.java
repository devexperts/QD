/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.mars.common;

import com.devexperts.logging.Logging;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;

/**
 * Provides convenient execution scheduling services for periodic stats gathering, logging and reporting.
 * Guarantees stable order of task execution according to the order of their registration.
 * Provides convenient API to simplify stats reporting to MARS.
 * <p>
 * This service differs from standard {@link ScheduledExecutorService} in that it attempts to execute
 * scheduled tasks at times that are multiples of specified periods (subject to available CPU resources).
 * As a result stats are gathered and reported at "even" times, including independent stats in separate JVMs.
 */
public class MARSScheduler {
    public static final String MARS_DELAY_PROPERTY = "mars.delay"; // in seconds

    public static final int MARS_DELAY = Integer.getInteger(MARS_DELAY_PROPERTY, 10); // in seconds!

    private static final MARSScheduler INSTANCE = new MARSScheduler();

    /**
     * Schedules specified command for execution with default MARS period.
     */
    public static void schedule(Runnable command) {
        INSTANCE.scheduleInternal(command, command, MARS_DELAY, TimeUnit.SECONDS);
    }

    /**
     * Schedules specified command for execution with specified period.
     */
    public static void schedule(Runnable command, long period, TimeUnit unit) {
        INSTANCE.scheduleInternal(command, command, period, unit);
    }

    /**
     * Watches specified object and reports it's {@link #toString()} value into specified MARS node
     * with default MARS period. This method is suitable both for text values and for numeric values.
     * <p>
     * Intended use-case is to report value contained in {@link AtomicInteger} or {@link AtomicLong}.
     * This way atomicity, concurrency and thread-safety are guaranteed with flexibility of value
     * aggregation policy.
     */
    public static void watch(final Object o, MARSNode node) {
        INSTANCE.scheduleInternal(o, new Watcher(node) {
            @Override
            public void run() {
                node.setValue(o.toString());
            }
        }, MARS_DELAY, TimeUnit.SECONDS);
    }

    /**
     * Watches specified collection and reports it's {@link Collection#size() size} into specified MARS node
     * with default MARS period.
     * <p>
     * Intended use-case is to report size of some cache or length of some queue.
     */
    public static void watchSize(final Collection<?> c, MARSNode node) {
        INSTANCE.scheduleInternal(c, new Watcher(node) {
            @Override
            public void run() {
                node.setIntValue(c.size());
            }
        }, MARS_DELAY, TimeUnit.SECONDS);
    }

    /**
     * Watches specified number and reports it's delta into specified MARS node with default MARS period.
     * The delta is computed as difference between new value and old value taken at measurement points.
     * <p>
     * Intended use-case is to report delta of a value contained in {@link AtomicInteger} or {@link AtomicLong}.
     * This way atomicity, concurrency and thread-safety are guaranteed with flexibility of value
     * aggregation policy.
     */
    public static void watchDelta(final Number number, MARSNode node) {
        INSTANCE.scheduleInternal(number, new Watcher(node) {
            private double value;

            @Override
            public void run() {
                double oldValue = value;
                value = number.doubleValue();
                node.setDoubleValue(value - oldValue);
            }
        }, MARS_DELAY, TimeUnit.SECONDS);
    }

    /**
     * Watches specified number and reports it's rate of change per second into specified MARS node
     * with default MARS period. The rate of change is computed as difference between new value and
     * old value taken at measurement points scaled by specified multiplier and elapsed time interval.
     * The output rate is rounded according to specified precision (number of decimal digits after point).
     * <p>
     * Intended use-case is to report rate of change of a value contained in {@link AtomicInteger} or {@link AtomicLong}.
     * This way atomicity, concurrency and thread-safety are guaranteed with flexibility of value
     * aggregation policy.
     */
    public static void watchTimeRate(final Number number, final double multiplier, int precision, MARSNode node) {
        if (Double.isNaN(multiplier) || Double.isInfinite(multiplier) || multiplier == 0)
            throw new IllegalArgumentException("multiplier is undefined");
        if (precision < -18 || precision > 18)
            throw new IllegalArgumentException("precision is out of range");
        long power = 1;
        while (precision-- > 0)
            power *= 10;
        final long scale = power;
        INSTANCE.scheduleInternal(number, new Watcher(node) {
            private long time = System.currentTimeMillis();
            private double value = number.doubleValue();

            @Override
            public void run() {
                long oldTime = time;
                time = System.currentTimeMillis();
                double oldValue = value;
                value = number.doubleValue();
                node.setDoubleValue(time <= oldTime ? 0 :
                    Math.floor((value - oldValue) * multiplier * 1000 / (time - oldTime) * scale + 0.5) / scale);
            }
        }, MARS_DELAY, TimeUnit.SECONDS);
    }

    /**
     * Cancels scheduling of specified command or stops watching of specified object. Accepts same
     * references that were specified to <code>schedule()</code> or <code>watchXXX()</code> methods.
     * When cancelling previously watched object this method also removes MARS node that was specified.
     * If specified object was scheduled or watched multiple times, then this method cancels all those tasks.
     * This method does nothing if specified object was not scheduled or watched.
     */
    public static void cancel(Object o) {
        INSTANCE.cancelInternal(o);
    }

    // ========== Implementation Details ==========

    private final HashMap<Long, Task> tasks = new HashMap<>();
    private final DelayQueue<Task> queue = new DelayQueue<>();

    private volatile Thread scheduler;

    private MARSScheduler() { // To prevent unwanted instantiation.
    }

    private synchronized void scheduleInternal(Object key, Runnable command, long period, TimeUnit unit) {
        if (key == null || command == null)
            throw new NullPointerException();
        period = unit.toNanos(period);
        if (period <= 0 || period >= Long.MAX_VALUE)
            throw new IllegalArgumentException("period must be positive finite number");
        Task task = tasks.get(period);
        if (task == null) {
            tasks.put(period, task = new Task(period));
            queue.add(task);
        }
        List<Runnable> commands = task.commands.get(key);
        if (commands == null)
            task.commands.put(key, commands = new ArrayList<>(1));
        commands.add(command);
        task.commandsCache = null; // Atomic volatile write.
        if (scheduler == null) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    MARSScheduler.this.runInternal();
                }
            }, "MonitoringScheduler");
            t.setDaemon(true);
            t.start();
            scheduler = t;
        }
    }

    private synchronized void cancelInternal(Object key) {
        for (Iterator<Task> it = tasks.values().iterator(); it.hasNext(); ) {
            Task task = it.next();
            if (task.commands.containsKey(key)) {
                for (Runnable command : task.commands.remove(key))
                    if (command instanceof Watcher)
                        ((Watcher) command).node.remove();
                task.commandsCache = null; // Atomic volatile write.
                if (task.commands.isEmpty()) {
                    it.remove(); // remove current task
                    if (tasks.isEmpty() && scheduler != null) {
                        scheduler.interrupt(); // forced kill -- don't wait on queue
                        scheduler = null; // make sure that next scheduleInternal creates new thread
                    }
                }
            }
        }
    }

    private synchronized void clearSchedulerInstance() {
        if (scheduler == Thread.currentThread())
            scheduler = null;
    }

    private void runInternal() {
        try {
            while (true) {
                try {
                    Task task = queue.take();
                    List<Runnable> commands = task.commandsCache; // Atomic volatile read.
                    if (commands == null)
                        synchronized (this) {
                            if (task.commands.isEmpty())
                                continue;
                            commands = new ArrayList<>(task.commands.size() * 5 / 4 + 1);
                            for (List<Runnable> c : task.commands.values())
                                commands.addAll(c);
                            task.commandsCache = commands; // Atomic volatile write.
                        }
                    for (Runnable command : commands)
                        try {
                            command.run();
                        } catch (Throwable t) {
                            log().error("Error running scheduled command:", t);
                        }
                    task.updateTime();
                    queue.add(task);
                } catch (InterruptedException e) {
                    return; // stopped because queue was emptied
                } catch (Throwable t) {
                    log().error("Scheduling error:", t);
                }
            }
        } finally {
            // for whatever reason we quit...
            clearSchedulerInstance();
        }
    }

    private static Logging log() {
        return Logging.getLogging(MARSScheduler.class);
    }

    private static class Task implements Delayed {
        final long period; // in nanoseconds
        long time; // in nanoseconds counted from UNIX epoch
        final LinkedHashMap<Object, List<Runnable>> commands = new LinkedHashMap<>();
        volatile List<Runnable> commandsCache; // cached commands for efficient asynchronous iteration

        Task(long period) {
            this.period = period;
            updateTime();
        }

        void updateTime() {
            time = Math.max(time, (TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()) + period / 2) / period * period) + period;
        }

        @Override
        public long getDelay(@Nonnull TimeUnit unit) {
            return unit.convert(time - TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(@Nonnull Delayed delayed) {
            Task other = (Task) delayed;
            if (time < other.time)
                return -1;
            if (time > other.time)
                return 1;
            if (period < other.period)
                return -1;
            if (period > other.period)
                return 1;
            return 0;
        }
    }

    private abstract static class Watcher implements Runnable {
        final MARSNode node;

        Watcher(MARSNode node) {
            if (node == null)
                throw new NullPointerException();
            this.node = node;
        }
    }
}
