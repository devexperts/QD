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
package com.devexperts.qd.impl.matrix.management;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.impl.matrix.Collector;
import com.devexperts.services.Services;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;

import java.util.EnumSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class CollectorManagement {

    private static final CollectorManagementFactory FACTORY = Services.createService(CollectorManagementFactory.class, null, null);

    public static final int DEFAULT_SUBSCRIPTION_BUCKET = 10000;
    public static final int DEFAULT_DISTRIBUTION_BUCKET = 100000;
    public static final int DEFAULT_INTERLEAVE = 4;
    public static final int DEFAULT_AGENT_BUFFER_SIZE_DEFAULT =
        SystemProperties.getIntProperty("com.devexperts.qd.impl.matrix.Agent.MaxBufferSize", 1000000); // default size of agent buffer, legacy property name
    public static final int DEFAULT_MAX_DISTRIBUTION_SPINS = 8;
    public static final String DEFAULT_BUFFER_OVERFLOW_LOG_INTERVAL = "10s";
    public static final String DEFAULT_LOCK_WAIT_LOG_INTERVAL = "10s";
    public static final String DEFAULT_USE_LOCK_PRIORITY = ".*Data";

    // Note: It has different default in sever-side implementation with monitoring.
    public static final String DEFAULT_USE_LOCK_COUNTERS = "";

    // --- static factory method ---

    public static CollectorManagement getInstance(DataScheme scheme, QDContract contract, String keyProperties) {
        return FACTORY == null ?
            new CollectorManagement(scheme, contract) :
            FACTORY.getInstance(scheme, contract, keyProperties);
    }

    // --- instance fields ---

    protected final DataScheme scheme;
    protected final QDContract contract;

    protected int subscriptionBucket = DEFAULT_SUBSCRIPTION_BUCKET; // Maximum number of records per subscription change.
    protected int distributionBucket = DEFAULT_DISTRIBUTION_BUCKET; // Maximum number of records per distribution.
    protected int interleave = DEFAULT_INTERLEAVE; // interleaved distributions
    protected int agentBufferSizeDefault = DEFAULT_AGENT_BUFFER_SIZE_DEFAULT;

    protected int maxDistributionSpins = DEFAULT_MAX_DISTRIBUTION_SPINS; // max number of tryLock spins in retrieveData
    protected TimePeriod bufferOverflowLogInterval = TimePeriod.valueOf(DEFAULT_BUFFER_OVERFLOW_LOG_INTERVAL); // interval between logs about dropped records
    protected TimePeriod lockWaitLogInterval = TimePeriod.valueOf(DEFAULT_LOCK_WAIT_LOG_INTERVAL); // interval to wait before logging that wait takes too long

    protected OperationSet useLockPriority = new OperationSet(DEFAULT_USE_LOCK_PRIORITY);
    protected OperationSet useLockCounters = new OperationSet(DEFAULT_USE_LOCK_COUNTERS); // don't do lock counters by default

    // --- instance constructor & methods ---

    protected CollectorManagement(DataScheme scheme, QDContract contract) {
        this.scheme = scheme;
        this.contract = contract;
    }

    public QDContract getContract() {
        return contract;
    }

    public int getSubscriptionBucket() {
        return subscriptionBucket;
    }

    public int getDistributionBucket() {
        return distributionBucket;
    }

    public int getInterleave() {
        if (contract != QDContract.TICKER)
            throw new UnsupportedOperationException("Interleave is not supported");
        return interleave;
    }

    public int getAgentBufferSizeDefault() {
        if (contract == QDContract.TICKER)
            throw new UnsupportedOperationException("AgentBufferSizeDefault is not supported");
        return agentBufferSizeDefault;
    }

    public int getMaxDistributionSpins() {
        return maxDistributionSpins;
    }

    public long getBufferOverflowLogIntervalSeconds() {
        return TimeUnit.MILLISECONDS.toSeconds(bufferOverflowLogInterval.getTime());
    }

    public long getLockWaitLogIntervalNanos() {
        return TimeUnit.MILLISECONDS.toNanos(lockWaitLogInterval.getTime());
    }

    public boolean useLockPriority(CollectorOperation op) {
        return useLockPriority.set.contains(op);
    }

    public boolean useLockCounters(CollectorOperation op) {
        return useLockCounters.set.contains(op);
    }

    // impl overrides
    public CollectorCounters createCounters() {
        return new CollectorCounters();
    }

    public void addCollector(Collector collector) {
        // impl overrides
    }

    public void removeCollector(Collector collector) {
        // impl overrides
    }

    public void setFatalError(Throwable error) {
        // impl overrides
    }

    protected static class OperationSet {
        final String string;
        final EnumSet<CollectorOperation> set = EnumSet.noneOf(CollectorOperation.class);

        public OperationSet(String string) {
            this.string = string;
            Pattern pattern = Pattern.compile(string);
            for (CollectorOperation op : CollectorOperation.values())
                if (pattern.matcher(op.toString()).matches())
                    set.add(op);
        }

        public String toString() {
            return string;
        }
    }
}
