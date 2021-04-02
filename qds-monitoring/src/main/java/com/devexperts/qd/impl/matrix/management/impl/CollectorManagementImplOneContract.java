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

import com.devexperts.logging.Logging;
import com.devexperts.management.Management;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.impl.matrix.Collector;
import com.devexperts.qd.impl.matrix.management.CollectorCounters;
import com.devexperts.qd.impl.matrix.management.CollectorManagement;
import com.devexperts.qd.impl.matrix.management.dump.DebugDumpExclude;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.util.JMXNameBuilder;
import com.devexperts.util.TimePeriod;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CollectorManagementImplOneContract extends CollectorManagementImplBase {
    /**
     * Use lock counters for all operations in this implementation.
     */
    public static final String DEFAULT_USE_LOCK_COUNTERS = ".*";

    private static final Logging log = Logging.getLogging(CollectorManagement.class);
    private static final Map<String, CollectorManagementImplOneContract> INSTANCES = new HashMap<String, CollectorManagementImplOneContract>();

    @DebugDumpExclude
    private final List<WeakReference<Collector>> collectors = new LinkedList<WeakReference<Collector>>();

    @DebugDumpExclude
    private Management.Registration registration;

    private FatalError fatalError;

    public static synchronized CollectorManagementImplOneContract getInstance(DataScheme scheme, QDContract contract, String keyProperties) {
        String name = getBeanName(scheme, contract, keyProperties);
        CollectorManagementImplOneContract management = INSTANCES.get(name);
        if (management != null)
            return management;
        management = new CollectorManagementImplOneContract(scheme, contract, keyProperties, name);
        INSTANCES.put(name, management);
        return management;
    }

    /**
     * Returns JMX name that is used for collector management.
     */
    public static String getBeanName(DataScheme scheme, QDContract contract, String keyProperties) {
        JMXNameBuilder builder = new JMXNameBuilder("com.devexperts.qd.impl.matrix");
        builder.appendKeyProperties(keyProperties);
        builder.append("scheme", CollectorManagementImplAnyContract.getSchemeName(scheme));
        builder.append("c", contract.toString());
        return builder.toString();
    }

    // ------------------------------ INSTANCE ------------------------------

    private CollectorManagementImplOneContract(DataScheme scheme, QDContract contract, String keyProperties,
        String name) {
        super(scheme, contract, keyProperties, name);
        // default is different from base class
        useLockCounters = new OperationSet(DEFAULT_USE_LOCK_COUNTERS);
        resetConfiguredDefaults();
    }

    public void resetToDefaults() {
        log.info("Resetting to defaults");
        resetBuiltInDefaults();
        resetConfiguredDefaults();
    }

    private void resetConfiguredDefaults() {
        QDConfig.setDefaultProperties(this, CollectorMXBean.class, "com.devexperts.qd.impl.matrix.Collector");
        QDConfig.setDefaultProperties(this, CollectorMXBean.class, "com.devexperts.qd.impl.matrix." + contract);
    }

    private void resetBuiltInDefaults() {
        Class<?> clazz = getClass();
        for (QDConfig.Property prop : QDConfig.getProperties(CollectorMXBean.class)) {
            try {
                Object value = clazz.getField(getConstantName(prop)).get(null);
                prop.getSetterMethod().invoke(this, value);
            } catch (Exception e) {
                throw new RuntimeException("Failed to reset property '" + prop.getName() + "' to builtin default", e);
            }
        }
    }

    private String getConstantName(QDConfig.Property prop) {
        String suffix = prop.getSuffix();
        StringBuilder sb = new StringBuilder("DEFAULT");
        for (int i = 0; i < suffix.length(); i++) {
            char c = suffix.charAt(i);
            if (c >= 'A' && c <= 'Z')
                sb.append('_');
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    public void setSubscriptionBucket(int subscriptionBucket) {
        if (subscriptionBucket < 1)
            throw new IllegalArgumentException("subscriptionBucket=" + subscriptionBucket);
        if (subscriptionBucket != this.subscriptionBucket) {
            logSet("subscriptionBucket", subscriptionBucket);
            this.subscriptionBucket = subscriptionBucket;
        }
    }

    public void setDistributionBucket(int distributionBucket) {
        if (distributionBucket < 1)
            throw new IllegalArgumentException("distributionBucket=" + distributionBucket);
        if (distributionBucket != this.distributionBucket) {
            logSet("distributionBucket", distributionBucket);
            this.distributionBucket = distributionBucket;
        }
    }

    public void setInterleave(int interleave) {
        if (interleave != this.interleave) {
            logSet("interleave", interleave);
            this.interleave = interleave;
        }
    }

    public void setAgentBufferSizeDefault(int agentBufferSizeDefault) {
        if (agentBufferSizeDefault < 1)
            throw new IllegalArgumentException("agentBufferSizeDefault=" + agentBufferSizeDefault);
        if (agentBufferSizeDefault != this.agentBufferSizeDefault) {
            logSet("agentBufferSizeDefault", agentBufferSizeDefault);
            this.agentBufferSizeDefault = agentBufferSizeDefault;
        }
    }

    public String getUseLockPriority() {
        return useLockPriority.toString();
    }

    public void setUseLockPriority(String string) {
        if (!string.equals(useLockPriority.toString())) {
            logSet("useLockPriority", string);
            useLockPriority = new OperationSet(string);
        }
    }

    public String getUseLockCounters() {
        return useLockCounters.toString();
    }

    public void setUseLockCounters(String string) {
        if (!string.equals(useLockCounters.toString())) {
            logSet("useLockCounters", string);
            useLockCounters = new OperationSet(string);
        }
    }

    public void setMaxDistributionSpins(int maxDistributionSpins) {
        if (maxDistributionSpins < 1)
            throw new IllegalArgumentException("maxDistributionSpins=" + maxDistributionSpins);
        if (maxDistributionSpins != this.maxDistributionSpins) {
            logSet("maxDistributionSpins", maxDistributionSpins);
            this.maxDistributionSpins = maxDistributionSpins;
        }
    }

    public String getBufferOverflowLogInterval() {
        return bufferOverflowLogInterval.toString();
    }

    public void setBufferOverflowLogInterval(String bufferOverflowLogInterval) {
        TimePeriod tp = TimePeriod.valueOf(bufferOverflowLogInterval);
        if (tp.getTime() <= 0)
            throw new IllegalArgumentException("bufferOverflowLogInterval must be positive");
        if (!tp.equals(this.bufferOverflowLogInterval)) {
            logSet("bufferOverflowLogInterval", tp);
            this.bufferOverflowLogInterval = tp;
        }
    }

    public String getLockWaitLogInterval() {
        return lockWaitLogInterval.toString();
    }

    public void setLockWaitLogInterval(String lockWaitLogInterval) {
        TimePeriod tp = TimePeriod.valueOf(lockWaitLogInterval);
        if (tp.getTime() <= 0)
            throw new IllegalArgumentException("lockWaitLogInterval must be positive");
        if (!tp.equals(this.lockWaitLogInterval)) {
            logSet("lockWaitLogInterval", tp);
            this.lockWaitLogInterval = tp;
        }
    }

    @Override
    public synchronized List<Collector> getCollectors() {
        List<Collector> result = new ArrayList<Collector>(collectors.size());
        for (Iterator<WeakReference<Collector>> it = collectors.iterator(); it.hasNext();) {
            Collector c = it.next().get();
            if (c == null)
                it.remove();
            else
                result.add(c);
        }
        return result;
    }

    @Override
    public CollectorCounters createCounters() {
        return new CollectorCountersImpl(scheme);
    }

    @Override
    public synchronized void addCollector(Collector c) {
        collectors.add(new WeakReference<Collector>(c));
        cleanupCollectors();
        if (registration == null) {
            registration = Management.registerMBean(this, CollectorMXBean.class, getBeanName(scheme, contract, keyProperties));
            CollectorManagementImplAnyContract.getInstance(scheme).addImpl(this);
        }
    }

    @Override
    public synchronized void removeCollector(Collector collector) {
        for (Iterator<WeakReference<Collector>> it = collectors.iterator(); it.hasNext(); ) {
            WeakReference<Collector> weakReference = it.next();
            if (weakReference.get() == collector) {
                it.remove();
            }
        }
        cleanupCollectors();
        if (collectors.isEmpty() && registration != null) {
            CollectorManagementImplAnyContract.getInstance(scheme).removeImpl(this);
            registration.unregister();
            registration = null;
        }
    }

    private void cleanupCollectors() {
        for (Iterator<WeakReference<Collector>> it = collectors.iterator(); it.hasNext();)
            if (it.next().get() == null)
                it.remove();
    }

    public String getFatalError() {
        FatalError fatalError = this.fatalError;
        return fatalError == null ? "" : fatalError.toString();
    }

    @Override
    public void setFatalError(Throwable error) {
        this.fatalError = new FatalError(error);
    }

    public String toString() {
        return contract + " management";
    }

    private static void logSet(String property, Object value) {
        log.info("Setting " + property + "=" + value);
    }
}
