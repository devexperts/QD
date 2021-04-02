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
package com.devexperts.mars.jvm;

/**
 * Management interface for {@link JVMSelfMonitoring} class.
 *
 * @dgen.annotate method {}
 */
public interface JVMSelfMonitoringMXBean {
    /**
     * JVM up time as a human-readable string
     */
    public String getUptime();

    /**
     * Current CPU usage in percents as a human-readable string
     */
    public String getCpuUsage();

    /**
     * Current heap memory size as a human-readable string
     */
    public String getHeapSize();

    /**
     * Current heap memory usage as a human-readable string
     */
    public String getHeapUsage();

    /**
     * Current non-heap memory size as a human-readable string
     */
    public String getNonHeapSize();

    /**
     * Current non-heap memory usage as a human-readable string
     */
    public String getNonHeapUsage();

    /**
     * Current number of threads and peak as a human-readable string
     */
    public String getThreadCount();

    /**
     * Number of deadlocked threads
     */
    public int getThreadDeadlockedCount();

    /**
     * Default JVM time zone description
     */
    public String getTimeZone();

    /**
     * Number of remaining thread dumps to take
     */
    public int getThreadDumpsCount();

    /**
     * Path and name of the file to write thread dumps to (stdout when empty)
     */
    public String getThreadDumpsFile();

    /**
     * Period between thread dumps
     */
    public String getThreadDumpsPeriod();

    /**
     * Date and time when thread dumps are scheduled to start
     */
    public String getThreadDumpsScheduledAt();

    /**
     * Makes a number of period thread dumps to a specified file with s specified period
     *
     * @param count       Number of thread dumps to make (zero to stop making dumps)
     * @param file        Path and name of the file to write thread dumps to (stdout when empty)
     * @param period      Period between thread dumps (leave empty to keep currently configured period)
     * @param scheduledAt Date and time when thread dumps are scheduled to start (leave empty to start immediately)
     */
    public void makeThreadDumps(int count, String file, String period, String scheduledAt);

    /**
     * Forces GC via System.gc() call
     */
    public void forceGarbageCollection();
}
