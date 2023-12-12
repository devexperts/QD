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
package com.devexperts.qd.monitoring;

import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.impl.matrix.Collector;
import com.devexperts.qd.impl.matrix.management.CollectorCounters;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.SystemProperties;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * An extension to {@link QDEndpoint} that is monitored via logs, MARS and JMX.
 */
public class MonitoredQDEndpoint extends QDEndpoint {
    private static final boolean LOG_COLLECTOR_COUNTERS =
        SystemProperties.getBooleanProperty(MonitoredQDEndpoint.class, "logCollectorCounters", false);

    private final MonitoringEndpoint monitoringEndpoint;

    private Runnable logConnectorCountersTask; // only when LOG_COLLECTOR_COUNTERS
    private EnumMap<QDContract, CollectorCounters> lastCounters; // only when LOG_COLLECTOR_COUNTERS

    protected MonitoredQDEndpoint(MonitoringEndpoint monitoringEndpoint, List<QDCollector.Factory> collectors,
        boolean withEventTimeSequence, boolean storeEverything)
    {
        super(monitoringEndpoint.getName(), monitoringEndpoint.getScheme(), monitoringEndpoint.getRootStats(),
            collectors, withEventTimeSequence, storeEverything);
        this.monitoringEndpoint = monitoringEndpoint;
        if (LOG_COLLECTOR_COUNTERS) {
            lastCounters = new EnumMap<>(QDContract.class);
            logConnectorCountersTask = new Runnable() {
                @Override
                public void run() {
                    logCollectorCounters();
                }
            };
            monitoringEndpoint.registerMonitoringTask(logConnectorCountersTask);
        }
    }

    @Override
    public Map<String, String> getDescriptorProperties() {
        return monitoringEndpoint.getDescriptorProperties();
    }

    @Override
    protected void addConnectorsImpl(Collection<MessageConnector> connectors) {
        super.addConnectorsImpl(connectors);
        monitoringEndpoint.addConnectors(connectors);
    }

    @Override
    protected void cleanupConnectorsImpl(Collection<MessageConnector> connectors) {
        monitoringEndpoint.removeConnectors(connectors);
        super.cleanupConnectorsImpl(connectors);
    }

    @Override
    protected void closeImpl() {
        super.closeImpl();
        if (logConnectorCountersTask != null)
            monitoringEndpoint.unregisterMonitoringTask(logConnectorCountersTask);
        monitoringEndpoint.release();
    }

    @Override
    public void registerMonitoringTask(Runnable task) {
        monitoringEndpoint.registerMonitoringTask(task);
    }

    private void logCollectorCounters() {
        for (QDContract contract : QDContract.values()) {
            QDCollector collector = getCollector(contract);
            if (!(collector instanceof Collector))
                continue;
            CollectorCounters counters = ((Collector) collector).getCountersSinceStart();
            CollectorCounters delta = counters.since(lastCounters.get(contract));
            lastCounters.put(contract, counters);
            String text = delta.textReport().replaceAll("\\r?\\n", "\n    ");
            log.info("\b{" + collector.toString() + "} " + text);
        }
    }

    @ServiceProvider
    public static class BuilderImpl extends QDEndpoint.Builder {
        private MonitoringEndpoint.Builder monitoringEndpointBuilder = MonitoringEndpoint.newBuilder();

        @Override
        public boolean supportsProperty(String key) {
            return super.supportsProperty(key) || monitoringEndpointBuilder.supportsProperty(key);
        }

        @Override
        public QDEndpoint build() {
            MonitoredQDEndpoint endpoint = new MonitoredQDEndpoint(
                monitoringEndpointBuilder.withScheme(getSchemeOrDefault()).withProperties(props).acquire(),
                collectors, withEventTimeSequence, storeEverything);
            subscribe(endpoint);
            return endpoint;
        }
    }
}
