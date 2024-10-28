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
package com.devexperts.qd.monitoring;

import com.devexperts.logging.Logging;
import com.devexperts.management.Management;
import com.devexperts.mars.common.MARSEndpoint;
import com.devexperts.mars.common.MARSNode;
import com.devexperts.mars.common.MARSScheduler;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.stats.JMXStats;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.util.JMXNameBuilder;
import com.devexperts.util.TimePeriod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class MonitoringEndpoint implements MonitoringEndpointMXBean {
    /**
     * Defines property for endpoint name that is used to distinguish multiple endpoints
     * in the same JVM in logs and in other diagnostic means.
     * Use {@link Builder#withProperty(String, String)} method.
     */
    public static final String NAME_PROPERTY = QDEndpoint.NAME_PROPERTY;

    /**
     * Defines time interval for reporting of various monitoring parameters.
     * Use {@link Builder#withProperty(String, String)} method.
     */
    public static final String MONITORING_STAT_PROPERTY = "monitoring.stat";

    private static final Logging log = Logging.getLogging(MonitoringEndpoint.class);
    
    public static Builder newBuilder() {
        return new Builder();
    }

    // ---------------------- static data ----------------------

    private static final Map<Builder, MonitoringEndpoint> INSTANCES = new HashMap<>();

    // ---------------------- instance ----------------------

    private final Builder builder;
    private final String name;
    private final DataScheme scheme; // may be null !!!!
    private final List<Runnable> monitoringTasks = new ArrayList<>();
    private final JMXStats.RootRegistration statsRegistration;
    private final JMXEndpoint jmxEndpoint;
    private final MARSEndpoint marsEndpoint;
    private final long statPeriodMillis;
    private final ConnectorsMonitoringTask cmt;
    private final Management.Registration selfRegistration;
    private int refCounter;
    private final Map<MessageConnector, Management.Registration> jmxConnectors = new IdentityHashMap<>();

    MonitoringEndpoint(Builder builder, String name, DataScheme scheme,
        JMXStats.RootRegistration registration,
        JMXEndpoint jmxEndpoint, MARSEndpoint marsEndpoint, long configuredStartPeriodMillis)
    {
        this.builder = builder;
        this.name = name;
        this.scheme = scheme;
        this.statsRegistration = registration;
        this.jmxEndpoint = jmxEndpoint;
        this.marsEndpoint = marsEndpoint;
        // use "mars.delay" as a default for stat period if one was not explicitly configured
        this.statPeriodMillis = configuredStartPeriodMillis == 0 ?
            MARSScheduler.MARS_DELAY * 1000L : // use mars delay (in seconds!) by default
            configuredStartPeriodMillis; // or explicitly configured period
        cmt = new ConnectorsMonitoringTask(name,
            configuredStartPeriodMillis != 0 ? log : null, // use log only when start period was explicitly configured
            registration.getRootStats(), marsEndpoint.getRoot(), null);
        registerMonitoringTask(cmt);

        selfRegistration = Management.registerMBean(this, MonitoringEndpointMXBean.class,
            "com.devexperts.qd.monitoring:type=MonitoringEndpoint,name=" + JMXNameBuilder.quoteKeyPropertyValue(name));
    }

    @Override
    public boolean isLogStripedConnectors() {
        return cmt.isLogStripedConnectors();
    }

    @Override
    public void setLogStripedConnectors(boolean flag) {
        cmt.setLogStripedConnectors(flag);
    }

    // SYNC(INSTANCES)
    void acquire() {
        if (refCounter++ > 0)
            return;
        for (Runnable task : monitoringTasks) {
            scheduleTask(task);
        }
    }

    // SYNC(INSTANCES)
    private void scheduleTask(Runnable task) {
        MARSScheduler.schedule(task, statPeriodMillis, TimeUnit.MILLISECONDS);
    }

    public void release() {
        synchronized (INSTANCES) {
            if (--refCounter > 0)
                return;
            INSTANCES.remove(builder);
            statsRegistration.unregister();
            selfRegistration.unregister();
            for (Runnable task : monitoringTasks) {
                MARSScheduler.cancel(task);
            }
            jmxConnectors.values().forEach(Management.Registration::unregister);
            jmxConnectors.clear();
            cmt.close();
            marsEndpoint.release();
            jmxEndpoint.release();
        }
    }

    public String getName() {
        return name;
    }

    public Map<String, String> getDescriptorProperties() {
        Map<String, String> result = new LinkedHashMap<>();
        String marsRootName = marsEndpoint.getMarsRootName();
        if (!marsRootName.isEmpty())
            result.put(MARSNode.MARS_ROOT_PROPERTY, marsRootName);
        return result;
    }

    public DataScheme getScheme() {
        return scheme;
    }

    public QDStats getRootStats() {
        return statsRegistration.getRootStats();
    }

    public synchronized void registerMonitoringTask(Runnable task) {
        synchronized (INSTANCES) {
            monitoringTasks.add(task);
            if (refCounter > 0)
                scheduleTask(task);
        }
    }

    public synchronized void unregisterMonitoringTask(Runnable task) {
        synchronized (INSTANCES) {
            monitoringTasks.remove(task);
            if (refCounter > 0)
                MARSScheduler.cancel(task);
        }
    }

    public void addConnectors(Collection<MessageConnector> connectors) {
        for (MessageConnector connector : connectors) {
            jmxConnectors.put(connector, registerConnector(connector));
        }
        cmt.addConnectors(connectors);
    }

    public void removeConnectors(Collection<MessageConnector> connectors) {
        for (MessageConnector connector : connectors) {
            Management.Registration registration = jmxConnectors.remove(connector);
            if (registration != null)
                registration.unregister();
        }
        cmt.removeConnectors(connectors);
    }

    private Management.Registration registerConnector(MessageConnector connector) {
        int index = 0;
        Management.Registration registration = Management.registerMBean(
            connector, null, getConnectorJmxName(connector.getName()));
        while (registration.hasExisted()) {
            // Add index to the connector's name if there is already a connector with the same name
            registration = Management.registerMBean(
                connector, null, getConnectorJmxName(connector.getName() + "-" + (++index)));
        }
        return registration;
    }

    private static String getConnectorJmxName(String name) {
        return "com.devexperts.qd.qtp:type=Connector,name=" + JMXNameBuilder.quoteKeyPropertyValue(name);
    }

    Consumer<String> droppedLogAccept() {
        return cmt::droppedLogAccept;
    }

    public static class Builder {
        private static final AtomicInteger INSTANCES_NUMERATOR = new AtomicInteger();
        private static final Set<String> SUPPORTED_PROPERTIES = new LinkedHashSet<>(Arrays.<String>asList(
            NAME_PROPERTY,
            MONITORING_STAT_PROPERTY
        ));

        private final JMXEndpoint.Builder jmxEndpointBuilder = JMXEndpoint.newBuilder();
        private final MARSEndpoint.Builder marsEndpointBuilder = MARSEndpoint.newBuilder();
        private DataScheme scheme;
        private final Properties props = new Properties();

        Builder() {}

        public final Builder withScheme(DataScheme scheme) {
            if (scheme == null)
                throw new NullPointerException();
            this.scheme = scheme;
            return this;
        }

        /**
         * Sets the specified property. Unsupported properties are ignored.
         * @see #supportsProperty(String)
         */
        public final Builder withProperty(String key, String value) {
            if (key == null || value == null)
                throw new NullPointerException();
            if (supportsProperty(key))
                props.setProperty(key, value);
            return this;
        }

        /**
         * Sets all supported properties from the provided properties object.
         */
        public final Builder withProperties(Properties props) {
            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                withProperty((String) entry.getKey(), (String) entry.getValue());
            }
            return this;
        }

        /**
         * Returns true if the corresponding property key is supported.
         * @see #withProperty(String, String)
         */
        public boolean supportsProperty(String key) {
            return
                jmxEndpointBuilder.supportsProperty(key) ||
                marsEndpointBuilder.supportsProperty(key) ||
                SUPPORTED_PROPERTIES.contains(key);
        }

        private String getOrCreateName() {
            String name = props.getProperty(NAME_PROPERTY);
            if (name != null)
                return name;
            int number = INSTANCES_NUMERATOR.getAndIncrement();
            return "monitoring" + (number == 0 ? "" : "-" + number);
        }

        public MonitoringEndpoint acquire() {
            synchronized (INSTANCES) {
                MonitoringEndpoint endpoint = INSTANCES.get(this);
                if (endpoint == null)
                    INSTANCES.put(this, endpoint = build());
                endpoint.acquire();
                return endpoint;
            }
        }

        // SYNC(INSTANCES)
        private MonitoringEndpoint build() {
            // pass properties to JMXEndpoint and build JMXEndpoint
            JMXEndpoint jmxEndpoint = jmxEndpointBuilder.withProperties(props).acquire();
            // pass properties to MARSEndpoint and build MARSEndpoint
            MARSEndpoint marsEndpoint = marsEndpointBuilder.withProperties(props).acquire();
            // log our own properties
            String name = getOrCreateName();
            for (String key : SUPPORTED_PROPERTIES) {
                if (props.containsKey(key) && !key.equals(NAME_PROPERTY)) {
                    log.info(name + " MonitoringEndpoint with " + key + "=" + props.getProperty(key));
                }
            }
            String statProp = props.getProperty(MONITORING_STAT_PROPERTY);
            long statPeriodMillis = statProp != null ? TimePeriod.valueOf(statProp).getTime() : 0;
            MonitoringEndpoint endpoint = new MonitoringEndpoint(this, name, scheme,
                JMXStats.createRoot(name, scheme), jmxEndpoint, marsEndpoint, statPeriodMillis);
            return endpoint;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Builder)) return false;
            Builder builder = (Builder) o;
            if (!props.equals(builder.props)) return false;
            if (scheme != null ? !scheme.equals(builder.scheme) : builder.scheme != null) return false;
            return true;
        }

        @Override
        public int hashCode() {
            int result = scheme != null ? scheme.hashCode() : 0;
            result = 31 * result + props.hashCode();
            return result;
        }
    }
}
