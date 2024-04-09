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
package com.devexperts.qd.stats;

import com.devexperts.logging.Logging;
import com.devexperts.management.Management;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.impl.matrix.management.impl.CollectorCountersImpl;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.QuickSort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLongArray;
import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.AttributeNotFoundException;
import javax.management.DynamicMBean;
import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.MBeanRegistration;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

public class JMXStats extends QDStats implements DynamicMBean, MBeanRegistration {
    private static final Logging log = Logging.getLogging(JMXStats.class);

    private static final String LONG_CLASS_NAME = long.class.getName();
    private static final String LONG_ARRAY_CLASS_NAME = long[].class.getName();
    private static final String STRING_CLASS_NAME = String.class.getName();

    private static final MBeanAttributeInfo CHILDREN = createJXMAttr("+Children", ObjectName[].class.getName());
    private static final MBeanAttributeInfo PARENT = createJXMAttr("+Parent", ObjectName.class.getName());
    private static final int BASIC_ATTRIBUTE_COUNT = 2; // CHILDREN & PARENT

    private static final String ARRAY_ATTR_SUFFIX = "Array*";
    private static final String TOP_ATTR_SUFFIX = "Top*";
    private static final int TOP_COUNT = 5;

    private static final Map<String, MBeanAttributeInfo> ATTRIBUTE_MAP = new HashMap<>();
    private static final boolean[] SHOULD_REGISTER = new boolean[FLAG_COUNT]; // indexed by flags
    private static final MBeanInfo[] MBEAN_INFO = new MBeanInfo[FLAG_COUNT];

    private static final Comparator<ObjectName> NAMES_COMPARATOR =
        Comparator.comparing(ObjectName::getDomain).thenComparing(ObjectName::getKeyPropertyListString);

    private static MBeanAttributeInfo createJXMAttr(String name, String className) {
        return new MBeanAttributeInfo(name, className, name, true, false, false);
    }

    static {
        // init attributes lists
        @SuppressWarnings("unchecked")
        ArrayList<MBeanAttributeInfo>[] al = (ArrayList<MBeanAttributeInfo>[]) new ArrayList[FLAG_COUNT];
        for (int f = 0; f < FLAG_COUNT; f++) {
            al[f] = new ArrayList<>();
            al[f].add(CHILDREN);
            al[f].add(PARENT);
        }
        for (int i = 0; i < SValue.getValueCount(); i++) {
            SValue value = SValue.getValue(i);
            MBeanAttributeInfo attr = createJXMAttr(value.getName(), LONG_CLASS_NAME);
            for (int f = 0; f < FLAG_COUNT; f++) {
                if (value.supportsFlag(f)) {
                    al[f].add(attr);
                }
            }
        }
        for (int i = 0; i < SValue.getValueCount(); i++) {
            SValue value = SValue.getValue(i);
            if (value.isRid()) {
                MBeanAttributeInfo arrayAttr =
                    createJXMAttr(value.getName() + ARRAY_ATTR_SUFFIX, LONG_ARRAY_CLASS_NAME);
                MBeanAttributeInfo topAttr =
                    createJXMAttr(value.getName() + TOP_ATTR_SUFFIX, STRING_CLASS_NAME);
                for (int f = 0; f < FLAG_COUNT; f++) {
                    if ((f & FLAG_RID) != 0 && value.supportsFlag(f)) {
                        al[f].add(arrayAttr);
                        al[f].add(topAttr);
                    }
                }
            }
        }
        ArrayList<MBeanAttributeInfo> allAttributes = al[FLAG_COUNT - 1];
        for (MBeanAttributeInfo attr : allAttributes) {
            ATTRIBUTE_MAP.put(attr.getName(), attr);
        }
        // init MBeanInfo
        for (int f = 0; f < FLAG_COUNT; f++) {
            SHOULD_REGISTER[f] = al[f].size() > BASIC_ATTRIBUTE_COUNT;
            MBEAN_INFO[f] = new MBeanInfo(JMXStats.class.getName(), "JMXStats",
                al[f].toArray(new MBeanAttributeInfo[0]),
                null,
                new MBeanOperationInfo[]{
                    new MBeanOperationInfo("reportCounters", "Reports performance counters", new MBeanParameterInfo[] {
                        new MBeanParameterInfo("format", "java.lang.String", "html (default) or csv"),
                        new MBeanParameterInfo("topSize", "java.lang.Integer",
                            "max size of TOP tables, " + TOP_COUNT + " by default")
                    }, "java.lang.String", MBeanOperationInfo.INFO)
                },
                null);
        }
    }

    public static RootRegistration createRoot(String name, DataScheme scheme) {
        for (int i = 0;; i++) {
            String s = i == 0 ? name : name + "#" + i;
            QDStats rootStats = new JMXStats("name=" + s);
            rootStats.initRoot(SType.ANY, scheme);
            Management.Registration registration = Management.registerMBean(rootStats, null, getRootStatsObjectName(s));
            if (!registration.hasExisted())
                return new RootRegistration(rootStats, registration);
            i++;
        }
    }

    private static String getRootStatsObjectName(String name) {
        return "com.devexperts.qd.stats:name=" + name + ",type=Any";
    }

    public static class RootRegistration {
        private final QDStats rootStats;
        private final Management.Registration registration;

        public RootRegistration(QDStats rootStats, Management.Registration registration) {
            this.rootStats = rootStats;
            this.registration = registration;
        }

        public QDStats getRootStats() {
            return rootStats;
        }

        public void unregister() {
            registration.unregister();
        }
    }

    // ------------------------- instance -------------------------

    private JmxInfo jmx;
    private int index;
    private IndexedSet<String, ChildIndex> childIndexerByCollectorType;
    private volatile Map<String, AddedMBeanEntry> addedMBeans;

    public JMXStats() {}

    public JMXStats(String keyProperties) {
        super(keyProperties);
    }

    @Override
    protected QDStats newInstance(SType type, boolean unmanaged) {
        // anonymous AGENT and DISTRIBUTOR children are regular QDStats (see QD-445)
        if (unmanaged && (type == SType.AGENT || type == SType.DISTRIBUTOR))
            return new QDStats();
        return new JMXStats();
    }

    @Override
    protected JMXStats getParent() {
        return (JMXStats) super.getParent();
    }

    @Override
    // SYNC: lock
    protected void initChild(QDStats child, SType type, String keyProperties, int ridCount, DataScheme scheme) {
        super.initChild(child, type, keyProperties, ridCount, scheme);
        if (!(child instanceof JMXStats))
            return; // anonymous children are regular QDStats (see QD-445)

        JMXStats jmxChild = (JMXStats) child;
        // compute index for this bean
        String childCollectorType = jmxChild.getCollectorType();
        if (childIndexerByCollectorType == null)
            childIndexerByCollectorType = IndexedSet.create(ChildIndex::getCollectorType);
        ChildIndex childIndex = childIndexerByCollectorType.getByKey(childCollectorType);
        if (childIndex == null)
            childIndexerByCollectorType.add(childIndex = new ChildIndex(childCollectorType));
        jmxChild.index = childIndex.index++;
        // register if needed
        if (jmxChild.shouldRegister())
            registerChildBean(jmxChild);
    }

    private boolean shouldRegister() {
        return SHOULD_REGISTER[getType().getFlag()];
    }

    private JmxInfo getJmxInfoFromAncestors() {
        JMXStats stats = this;
        while (stats != null) {
            if (stats.jmx != null)
                return stats.jmx;
            stats = stats.getParent();
        }
        return null;
    }

    private void registerChildBean(JMXStats jmxChild) {
        JmxInfo jmx = getJmxInfoFromAncestors();
        if (jmx != null)
            try {
                // it will construct its name in preRegister
                jmx.server.registerMBean(jmxChild, jmx.name);
            } catch (InstanceAlreadyExistsException e) {
                log.warn("Already registered JMX bean " + e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected exception while registering JMX children bean of " + jmx.name, e);
            }
    }

    @Override
    protected void closeImpl() {
        if (jmx != null) {
            unregisterMBean();
        } else {
            unregisterChildrenRec();
        }
    }

    protected void registerAddedMBeans() {
        if (jmx != null && addedMBeans != null) {
            for (Map.Entry<String, AddedMBeanEntry> entry : addedMBeans.entrySet()) {
                registerAddedMBean(entry.getKey(), entry.getValue());
            }
        }
    }

    private void registerAddedMBean(String type, AddedMBeanEntry mbe) {
        if (jmx != null && mbe.name == null) {
            String name = constructName(jmx.name.getDomain(), type, jmx.name.getKeyPropertyListString());
            try {
                mbe.name = jmx.server.registerMBean(mbe.mbean, new ObjectName(name)).getObjectName();
            } catch (Exception e) {
                log.error("Unexpected exception registering JMX bean " + name, e);
            }
        }
    }

    protected void unregisterAddedMBeans() {
        if (jmx != null && addedMBeans != null) {
            for (Map.Entry<String, AddedMBeanEntry> entry : addedMBeans.entrySet()) {
                unregisterAddedMBean(entry.getValue());
            }
        }
    }

    private void unregisterAddedMBean(AddedMBeanEntry mbe) {
        if (jmx != null && mbe.name != null)
            try {
                if (jmx.server.isRegistered(mbe.name))
                    jmx.server.unregisterMBean(mbe.name);
                mbe.name = null;
            } catch (Exception e) {
                log.error("Unexpected exception unregistering JMX bean " + mbe.name, e);
            }
    }

    @Override
    public void addMBean(String type, Object mbean) {
        if (addedMBeans == null)
            synchronized (this) {
                if (addedMBeans == null)
                    addedMBeans = new ConcurrentHashMap<>(); // because we want synchronized access just in case...
            }
        AddedMBeanEntry mbe = addedMBeans.get(type);
        if (mbe != null)
            unregisterAddedMBean(mbe);
        mbe = new AddedMBeanEntry(Management.wrapMBean(mbean, null));
        registerAddedMBean(type, mbe);
        addedMBeans.put(type, mbe);
    }

    private static class AddedMBeanEntry {
        final DynamicMBean mbean;
        ObjectName name;

        AddedMBeanEntry(DynamicMBean mbean) {
            this.mbean = mbean;
        }
    }

    // ========== DynamicMBean Implementation ==========

    public Object getAttribute(String attribute) throws AttributeNotFoundException {
        try {
            MBeanAttributeInfo attr = ATTRIBUTE_MAP.get(attribute);
            if (attr == null) {
                throw new AttributeNotFoundException(attribute);
            }
            if (attr == CHILDREN) {
                List<ObjectName> names = new ArrayList<>(getChildren().length);
                addChildrenNamesRec(names);
                QuickSort.sort(names, NAMES_COMPARATOR);
                return names.toArray(new ObjectName[0]);
            }
            if (attr == PARENT) {
                return getParentNameRec();
            }
            String name = attr.getName();
            if (attr.getType().equals(LONG_CLASS_NAME)) {
                return getValue(SValue.valueOf(name), false);
            }
            if (attr.getType().equals(LONG_ARRAY_CLASS_NAME) && name.endsWith(ARRAY_ATTR_SUFFIX)) {
                long[] v = new long[getRidCount()];
                addValues(SValue.valueOf(name.substring(0, name.length() - ARRAY_ATTR_SUFFIX.length())), false, v);
                return v;
            }
            if (attr.getType().equals(STRING_CLASS_NAME) && name.endsWith(TOP_ATTR_SUFFIX)) {
                long[] v = new long[getRidCount()];
                addValues(SValue.valueOf(name.substring(0, name.length() - TOP_ATTR_SUFFIX.length())), false, v);
                return findTop(v);
            }
            throw new AttributeNotFoundException(attribute);
        } catch (RuntimeException e) {
            log.error("Unexpected JMX exception", e);
            throw e;
        }
    }

    private void addChildrenNamesRec(List<ObjectName> names) {
        QDStats[] children = getChildren(); // Atomic read.
        for (QDStats child : children) {
            if (!(child instanceof JMXStats))
                continue; // anonymous children are regular QDStats (see QD-445)

            JMXStats jmxChild = (JMXStats) child;
            if (jmxChild.jmx != null) {
                names.add(jmxChild.jmx.name);
            } else {
                jmxChild.addChildrenNamesRec(names);
            }
        }
    }

    public ObjectName getParentNameRec() {
        JMXStats stats = getParent();
        while (stats != null) {
            if (stats.jmx != null)
                return stats.jmx.name;
            stats = stats.getParent();
        }
        return null;
    }

    private String reportCounters(String format, int topSize) {
        Map<String, AtomicLongArray> counters = new LinkedHashMap<>();
        for (MBeanAttributeInfo attr : getMBeanInfo().getAttributes()) {
            String name = attr.getName();
            if (attr.getType().equals(STRING_CLASS_NAME) && name.endsWith(TOP_ATTR_SUFFIX)) {
                long[] v = new long[getRidCount()];
                addValues(SValue.valueOf(name.substring(0, name.length() - TOP_ATTR_SUFFIX.length())), false, v);
                counters.put(name, new AtomicLongArray(v));
            }
        }
        return CollectorCountersImpl.reportCounters(getScheme(), counters, format, topSize);
    }

    private String findTop(long[] v) {
        if (v.length == 0)
            return "";
        PriorityQueue<IndexedValue> pq = new PriorityQueue<>(v.length);
        for (int i = 0; i < v.length; i++) {
            pq.add(new IndexedValue(i, v[i]));
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TOP_COUNT; i++) {
            if (pq.isEmpty())
                break;
            IndexedValue iv = pq.remove();
            if (iv.value == 0)
                break;
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(getScheme() != null && iv.index < getScheme().getRecordCount() ?
                getScheme().getRecord(iv.index).getName() : "[" + iv.index + "]");
            sb.append('=').append(iv.value);
        }
        return sb.toString();
    }

    private static class IndexedValue implements Comparable<IndexedValue> {
        final int index;
        final long value;

        IndexedValue(int index, long value) {
            this.index = index;
            this.value = value;
        }

        public int compareTo(IndexedValue o) {
            return Long.compare(o.value, value);
        }
    }

    public void setAttribute(Attribute attribute) throws AttributeNotFoundException {
        throw new AttributeNotFoundException(attribute.getName());
    }

    public AttributeList getAttributes(String[] attributes) {
        AttributeList al = new AttributeList();
        for (String attribute : attributes) {
            try {
                al.add(new Attribute(attribute, getAttribute(attribute)));
            } catch (Exception e) {
                log.error("Unexpected JMX exception", e);
            }
        }
        return al;
    }

    public AttributeList setAttributes(AttributeList attributes) {
        AttributeList al = new AttributeList();
        for (Object attribute : attributes) {
            try {
                Attribute a = (Attribute) attribute;
                al.add(new Attribute(a.getName(), getAttribute(a.getName())));
            } catch (Exception e) {
                log.error("Unexpected JMX exception", e);
            }
        }
        return al;
    }

    public Object invoke(String action, Object[] params, String[] signature) throws ReflectionException {
        if (action.equalsIgnoreCase("reportCounters")) {
            String format = (String) params[0];
            Integer topSize = (Integer) params[1];
            int top = topSize == null ? TOP_COUNT : topSize;
            return reportCounters(format, top);
        }
        throw new ReflectionException(new NoSuchMethodException(action));
    }

    public MBeanInfo getMBeanInfo() {
        return MBEAN_INFO[getType().getFlag()];
    }

    // ========== MBeanRegistration Implementation ==========

    // props may be empty or null
    protected ObjectName constructName(String domain, String keyProperties) throws MalformedObjectNameException {
        return new ObjectName(constructName(domain, getType().getName() + "Stats", keyProperties));
    }

    protected String constructName(String domain, String type, String keyProperties) {
        JMXStatsNameBuilder nb = new JMXStatsNameBuilder(domain);
        // append full key properties first (always)
        nb.appendKeyProperties(getFullKeyProperties());
        for (QDStats child = this, parent; (parent = child.getParent()) != null; child = parent) {
            if (child.isSum()) {
                nb.insertSumModeFlag();
            } else {
                nb.insertId(((JMXStats) child).index);
            }
        }
        nb.append("c", getCollectorFromAncestors());
        nb.appendType(type);
        nb.doneId();
        nb.appendKeyProperties(keyProperties); // inherited key properties (at the end)
        return nb.toString();
    }

    private String getCollector() {
        if (getType() == SType.TICKER)
            return "Ticker";
        if (getType() == SType.STREAM)
            return "Stream";
        if (getType() == SType.HISTORY)
            return "History";
        return null;
    }

    private String getCollectorFromAncestors() {
        String collector = getCollector();
        for (QDStats stats = getParent(); collector == null && stats != null; stats = stats.getParent()) {
            collector = ((JMXStats) stats).getCollector();
        }
        return collector == null ? "Any" : collector;
    }

    private String getCollectorType() {
        return getCollectorFromAncestors() + "-" + getType();
    }

    public ObjectName preRegister(MBeanServer server, ObjectName name) throws Exception {
        if (jmx != null)
            unregisterMBean();
        jmx = new JmxInfo(server, name == null ?
            constructName(server.getDefaultDomain(), null) :
            constructName(name.getDomain(), name.getKeyPropertyListString()));
        registerChildrenRec();
        registerAddedMBeans();
        return jmx.name;
    }

    private void registerChildrenRec() {
        QDStats[] children = getChildren(); // Atomic read.
        for (QDStats child : children) {
            if (!(child instanceof JMXStats))
                continue; // anonymous children are regular QDStats (see QD-445)

            JMXStats jmxChild = (JMXStats) child;
            if (jmxChild.getParent() == this) {
                // register only proper children
                if (jmxChild.shouldRegister()) {
                    registerChildBean(jmxChild);
                } else {
                    jmxChild.registerChildrenRec();
                }
            }
        }
    }

    public void postRegister(Boolean registrationDone) {
        if (registrationDone == null || !registrationDone)
            jmx = null;
    }

    public void preDeregister() {
        unregisterAddedMBeans();
        unregisterChildrenRec();
    }

    private void unregisterChildrenRec() {
        QDStats[] children = getChildren(); // Atomic read.
        for (QDStats child : children) {
            if (!(child instanceof JMXStats))
                continue; // anonymous children are regular QDStats (see QD-445)

            JMXStats jmxChild = (JMXStats) child;
            if (jmxChild.getParent() == this) {
                if (jmxChild.jmx != null) {
                    jmxChild.unregisterMBean();
                } else {
                    jmxChild.unregisterChildrenRec();
                }
            }
        }
    }

    public void postDeregister() {
        jmx = null;
    }

    private void unregisterMBean() {
        try {
            jmx.server.unregisterMBean(jmx.name);
        } catch (Exception e) {
            log.error("Unexpected exception while unregistering JMX bean " + jmx.name, e);
        }
    }

    public String toString() {
        return jmx == null ? super.toString() : jmx.name.toString();
    }

    private static class JmxInfo {
        final MBeanServer server;
        final ObjectName name;

        JmxInfo(MBeanServer server, ObjectName name) {
            this.server = server;
            this.name = name;
        }
    }

    private static class ChildIndex {
        final String collectorType;
        int index;

        ChildIndex(String collectorType) {
            this.collectorType = collectorType;
        }

        String getCollectorType() {
            return collectorType;
        }
    }
}
