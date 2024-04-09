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
package com.devexperts.mars.jvm;

import com.devexperts.logging.Logging;
import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Monitors process CPU usage.
 */
public class CpuCounter {

    private static Logging log() {
        return Logging.getLogging(CpuCounter.class);
    }

    private static class Accessor implements Runnable {
        static final long MS = 1000000;
        static final long INACCURACY = 30 * MS;
        static final long JUMP = 10 * 60 * 1000 * MS;
        static final OperatingSystemMXBean OS = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        static volatile Accessor currentAccessor;
        static Thread currentThread;

        static synchronized void start() {
            if (currentThread != null && currentThread.isAlive())
                return;
            currentAccessor = new Accessor();
            currentAccessor.readout();
            currentThread = new Thread(new Accessor(), "CpuMonitor");
            currentThread.setDaemon(true);
            currentThread.setPriority(Thread.MAX_PRIORITY);
            currentThread.start();
        }

        static synchronized void stop() {
            if (currentThread != null) {
                currentThread.interrupt();
                currentThread = null;
            }
        }

        int availableProcessors;
        long realTime; // nanos, System.currentTimeMillis() * MS;
        long processTime; // nanos, System.nanoTime();
        long cpuTime; // nanos, OS.getProcessCpuTime();
        long userTime; // nanos, cpuTime / availableProcessors;

        Accessor() {}

        private void readout() {
            for (int attempts = 0; attempts < 10; attempts++) {
                long time = System.currentTimeMillis();
                long real = Math.min(Math.max(0, time), (Long.MAX_VALUE - 10 * INACCURACY) / MS) * MS;
                long process = System.nanoTime();
                if (attempts > 0 && real == realTime && process >= processTime && process < processTime + MS / 10)
                    break;
                availableProcessors = Math.max(1, OS.getAvailableProcessors());
                realTime = real;
                processTime = process;
                cpuTime = OS.getProcessCpuTime();
                userTime = cpuTime / availableProcessors;
            }
        }

        public void run() {
            try {
                Accessor[] accessors = new Accessor[100];
                for (int i = 0; i < accessors.length; i++) {
                    accessors[i] = new Accessor();
                }
                int currentIndex = 0;
                Accessor prev = new Accessor();
                Accessor next = new Accessor();
                prev.readout();
                Accessor tmp = accessors[currentIndex];
                tmp.availableProcessors = prev.availableProcessors;
                tmp.realTime = prev.realTime;
                tmp.processTime = prev.processTime;
                tmp.cpuTime = prev.cpuTime;
                tmp.userTime = prev.userTime;
                currentAccessor = tmp; // Atomic volatile write.
                long period = 99;
                while (true) {
                    try {
                        Thread.sleep(period);
                        next.readout();
                        long real = next.realTime - prev.realTime;
                        if (real < period * MS - INACCURACY || real >= period * MS + JUMP)
                            log().warn("Real time changed by " + real / MS + " ms in " + period + " ms");
                        if (real < period * MS + JUMP) {
                            real = Math.max(real, period * MS);
                            long process = next.processTime - prev.processTime;
                            if (process < real - INACCURACY || process > real + INACCURACY)
                                log().warn("Process time changed by " + process / MS + " ms in " + real / MS + " ms");
                            process = Math.max(process, real);
                            long cpu = next.cpuTime - prev.cpuTime;
                            long user = cpu / next.availableProcessors;
                            if (user < 0 || user > process + INACCURACY) {
                                log().warn("CPU time changed by " + user / MS + " ms in " + process / MS + " ms");
                            } else {
                                cpu = Math.min(cpu, process * next.availableProcessors);
                                user = Math.min(user, process);
                                currentIndex = (currentIndex + 1) % accessors.length;
                                tmp = accessors[currentIndex];
                                tmp.availableProcessors = next.availableProcessors;
                                tmp.realTime = next.realTime;
                                tmp.processTime = currentAccessor.processTime + process;
                                tmp.cpuTime = currentAccessor.cpuTime + cpu;
                                tmp.userTime = currentAccessor.userTime + user;
                                currentAccessor = tmp; // Atomic volatile write.
                            }
                        }
                        tmp = prev;
                        prev = next;
                        next = tmp;
                    } catch (InterruptedException e) {
                        // stop was called, silently exit
                        return;
                    } catch (Throwable t) {
                        log().error("Failed to monitor CPU time:", t);
                    }
                }
            } catch (Throwable t) {
                log().error("Failed to monitor CPU time:", t);
            }
        }
    }

    private static final AtomicInteger CPU_COUNTER_INSTANCES = new AtomicInteger();

    private boolean initialized;
    private long processTime;
    private long userTime;

    public CpuCounter() {
        try {
            Accessor.start(); // initialize accessor
            readout();
            initialized = true;
            CPU_COUNTER_INSTANCES.getAndIncrement();
        } catch (Throwable t) {
            log().error("Failed to start CPU monitor:", t);
        }
    }

    private void readout() {
        for (int attempts = 0; attempts < 10; attempts++) {
            Accessor a = Accessor.currentAccessor; // Atomic volatile read.
            processTime = a.processTime;
            userTime = a.userTime;
            if (a == Accessor.currentAccessor) // Atomic volatile read.
                break;
        }
    }

    private long getProcessCpuTime() {
        return Accessor.currentAccessor.cpuTime; // Atomic volatile read.
    }

    /**
     * Returns amount of consumed CPU time since the JVM started, in nanoseconds.
     */
    public long getCpuTime() {
        if (!initialized)
            return 0;
        return getProcessCpuTime();
    }

    /**
     * Returns CPU usage since last call of this method, in fractions (from 0 to 1) of total CPU capacity.
     */
    public double getCpuUsage() {
        if (!initialized)
            return 0;
        long prevProcessTime = processTime;
        long prevUserTime = userTime;
        readout();
        return Math.floor(10000.0 * Math.max(0, userTime - prevUserTime) /
            Math.max(1, processTime - prevProcessTime) + 0.5) / 10000.0;
    }

    /**
     * Stops tracking CPU usage if that was the last instance.
     */
    public synchronized void close() {
        if (!initialized)
            return;
        initialized = false;
        if (CPU_COUNTER_INSTANCES.decrementAndGet() == 0) // this was the last initialized instance we know of
            Accessor.stop();
    }
}
