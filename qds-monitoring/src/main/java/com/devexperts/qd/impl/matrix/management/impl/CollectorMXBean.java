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
package com.devexperts.qd.impl.matrix.management.impl;

import com.devexperts.qd.QDContract;

/**
 * Management interface for core QD collectors.
 * This interface manages implementation-dependent tuning parameters and is subject to change
 * in any future version.
 *
 * @dgen.annotate method {}
 */
public interface CollectorMXBean {
    /**
     * Number of managed collector instances
     */
    public int getCollectorCount();

    /**
     * Contract name
     */
    public QDContract getContract();

    public void resetToDefaults();

    /**
     * Size of the bucket (in records) for one phase of subscription change operation
     */
    public int getSubscriptionBucket();

    public void setSubscriptionBucket(int bucket);

    /**
     * Size of the bucket (in records) for one phase of distribution operation
     */
    public int getDistributionBucket();

    public void setDistributionBucket(int bucket);

    /**
     * Number of concurrent scans of subscription structure during distribution
     */
    public int getInterleave();

    public void setInterleave(int interleave);

    /**
     * Default size of agent buffer
     */
    public int getAgentBufferSizeDefault();

    public void setAgentBufferSizeDefault(int size);

    /**
     * Maximal number of tryLock spins during distribution before waiting for local lock
     */
    public int getMaxDistributionSpins();

    public void setMaxDistributionSpins(int spins);

    /**
     * Duration between logging messages about buffer overflow
     */
    public String getBufferOverflowLogInterval();

    public void setBufferOverflowLogInterval(String bufferOverflowLogInterval);

    /**
     * Duration before logging that global lock is taking too long
     */
    public String getLockWaitLogInterval();

    public void setLockWaitLogInterval(String lockWaitLogIntervalSecs);

    /**
     * Regexp of operations that have global lock priority
     */
    public String getUseLockPriority();

    public void setUseLockPriority(String string);

    /**
     * Regexp of operations that count global locks and time inside the lock
     */
    public String getUseLockCounters();

    public void setUseLockCounters(String string);

    /**
     * A list of all operations that require global lock
     */
    public String getAllLockOperations();

    /**
     * Non-empty when fatal error had happened inside core processing methods
     */
    public String getFatalError();

    /**
     * Performance counters since last reset
     */
    public String getCounters();

    /**
     * Reports performance counters since last reset
     *
     * @param format html (default) or csv
     * @param topSize max size of TOP tables, 5 by default
     */
    public String reportCounters(String format, Integer topSize);

    /**
     * Resets performance counters
     */
    public void resetCounters();

    /**
     * Dumps detailed information about all subscriptions to the file in QDSD format. Read it with SubscriptionDumpParser tool
     *
     * @param file the name of the file
     */
    public void dumpSubscriptionToFile(String file);

    /**
     * Dumps detailed information about subscription to the file, read it with com.devexperts.qd.impl.matrix.management.dump.DebugDumpReader class
     *
     * @param file the name of the file
     */
    public void dumpCollectorsToFile(String file);

    /**
     * Verifies consistency of internal collector data structures (see log for verification report)
     */
    public void verifyCollectors();

    /**
     * Reports stored snapshot data
     *
     * @param recordName the name of the record (use * for all)
     * @param symbol     the symbol (use * for all)
     * @param boundsOnly true to report only first and last rows from history
     * @param format     html (default), csv, or text
     */
    public String reportData(String recordName, String symbol, boolean boundsOnly, String format);

    /**
     * Reports subscription
     *
     * @param recordName the name of the record (use * for all)
     * @param symbol     the symbol (use * for all)
     * @param format     html (default), csv, or text
     */
    public String reportSubscription(String recordName, String symbol, String format);
}
