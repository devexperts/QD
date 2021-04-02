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

import com.devexperts.management.Management;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.impl.matrix.Collector;
import com.devexperts.util.JMXNameBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public class CollectorManagementImplAnyContract extends CollectorManagementImplBase {
    private static final Map<DataScheme, String> SCHEMES = new HashMap<DataScheme, String>();
    private static final Set<String> SCHEME_NAMES = new HashSet<String>();
    private static final Map<String, CollectorManagementImplAnyContract> INSTANCES = new HashMap<String, CollectorManagementImplAnyContract>();

    public static synchronized CollectorManagementImplAnyContract getInstance(DataScheme scheme) {
        String name = getBeanName(scheme);
        CollectorManagementImplAnyContract management = INSTANCES.get(name);
        if (management != null)
            return management;
        management = new CollectorManagementImplAnyContract(scheme, name);
        INSTANCES.put(name, management);
        return management;
    }

    /**
     * Returns JMX name that is used for collector management.
     */
    public static String getBeanName(DataScheme scheme) {
        JMXNameBuilder builder = new JMXNameBuilder("com.devexperts.qd.impl.matrix");
        builder.append("scheme", getSchemeName(scheme));
        builder.append("c", "Any");
        return builder.toString();
    }

    private static final String[] NAME_SUFFIXES = { "DataScheme", "Scheme", "Schema"};

    /**
     * Returns simple scheme name for use in JMX.
     */
    public static synchronized String getSchemeName(DataScheme scheme) {
        String name = SCHEMES.get(scheme);
        if (name != null)
            return name;
        name = scheme.getClass().getSimpleName();
        for (String suffix : NAME_SUFFIXES)
            if (name.length() > suffix.length() && name.endsWith(suffix)) {
                name = name.substring(0, name.length() - suffix.length());
                break;
            }
        String unique = name;
        for (int i = 0; SCHEME_NAMES.contains(unique); i++)
            unique = name + "-" + i;
        SCHEMES.put(scheme, unique);
        SCHEME_NAMES.add(unique);
        return unique;
    }

    // ------------------------ INSTANCE ------------------------

    private final List<CollectorManagementImplOneContract> impls = new CopyOnWriteArrayList<CollectorManagementImplOneContract>();
    Management.Registration registration;

    public CollectorManagementImplAnyContract(DataScheme scheme, String name) {
        super(scheme, null, "", name);
    }

    public synchronized void addImpl(CollectorManagementImplOneContract o) {
        impls.add(o);
        if (registration == null)
            registration = Management.registerMBean(this, CollectorMXBean.class, getBeanName(scheme));
    }

    public synchronized void removeImpl(CollectorManagementImplOneContract o) {
        impls.remove(o);
        if (impls.isEmpty() && registration != null) {
            registration.unregister();
            registration = null;
        }
    }

    @Override
    public QDContract getContract() {
        throw new UnsupportedOperationException();
    }

    public void resetToDefaults() {
        for (CollectorManagementImplOneContract impl : impls)
            impl.resetToDefaults();
    }

    @Override
    public int getSubscriptionBucket() {
        throw new UnsupportedOperationException();
    }

    public void setSubscriptionBucket(int bucket) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getDistributionBucket() {
        throw new UnsupportedOperationException();
    }

    public void setDistributionBucket(int bucket) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getInterleave() {
        throw new UnsupportedOperationException();
    }

    public void setInterleave(int interleave) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getAgentBufferSizeDefault() {
        throw new UnsupportedOperationException();
    }

    public void setAgentBufferSizeDefault(int size) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getMaxDistributionSpins() {
        throw new UnsupportedOperationException();
    }

    public void setMaxDistributionSpins(int spins) {
        throw new UnsupportedOperationException();
    }

    public String getBufferOverflowLogInterval() {
        throw new UnsupportedOperationException();
    }

    public void setBufferOverflowLogInterval(String bufferOverflowLogInterval) {
        throw new UnsupportedOperationException();
    }

    public String getLockWaitLogInterval() {
        throw new UnsupportedOperationException();
    }

    public void setLockWaitLogInterval(String lockWaitLogIntervalSecs) {
        throw new UnsupportedOperationException();
    }

    public String getUseLockPriority() {
        throw new UnsupportedOperationException();
    }

    public void setUseLockPriority(String string) {
        throw new UnsupportedOperationException();
    }

    public String getUseLockCounters() {
        throw new UnsupportedOperationException();
    }

    public void setUseLockCounters(String string) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected List<Collector> getCollectors() {
        List<Collector> result = new ArrayList<Collector>();
        for (CollectorManagementImplOneContract impl : impls)
            result.addAll(impl.getCollectors());
        return result;
    }

    public String getFatalError() {
        StringBuilder sb = new StringBuilder();
        for (CollectorManagementImplOneContract impl : impls) {
            String s = impl.getFatalError();
            if (s.length() > 0) {
                if (sb.length() > 0)
                    sb.append("\r\n\r\n");
                sb.append(s);
            }
        }
        return sb.toString();
    }
}


