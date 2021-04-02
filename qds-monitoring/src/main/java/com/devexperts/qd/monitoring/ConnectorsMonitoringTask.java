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
package com.devexperts.qd.monitoring;

import com.devexperts.logging.Logging;
import com.devexperts.mars.common.MARSNode;
import com.devexperts.mars.jvm.CpuCounter;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDLog;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.stats.QDStats;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runnable task that logs statistics and sends them to MARS for a given root {@link QDStats}
 * and a list of {@link MessageConnector} instances every time it is invoked. Connectors can
 * be added on the fly with {@link #addConnector}, roots stats for summaries with
 * {@link #addStats}.
 * <p/>
 * <p>Use this class with {@link QDMonitoring#registerPeriodicTask(long, Runnable)}.
 * <p/>
 * <p>This class is safe for use from multiple threads.
 */
public class ConnectorsMonitoringTask implements Runnable {
    /**
     * The name. It null when was not explicitly specified (legacy code).
     * It is always set when part of some endpoint and is equal to endpoint's name.
     */
    private final String name;

    private final Logging log; // can be null (!!!)

    private final MARSNode connectorsNode;
    private final MARSNode subscriptionNode;
    private final MARSNode storageNode;
    private final MARSNode bufferNode;

    private final MonitoringCounter time = new MonitoringCounter();
    private final IOCounters rootCounters;
    private final Map<MessageConnector, IOCounter> connectorsMap = new HashMap<>();
    private final Map<String, IOCounters> countersByName = new LinkedHashMap<>();
    private final List<QDStats> rootStats = new CopyOnWriteArrayList<>();
    private final CpuCounter cpu = new CpuCounter();

    private final Layout layout = new Layout();
    private final NumberFormat integerFormat = NumberFormat.getIntegerInstance(Locale.US);
    private final NumberFormat percentFormat = NumberFormat.getPercentInstance(Locale.US);

    {
        percentFormat.setMaximumFractionDigits(2);
        percentFormat.setMinimumFractionDigits(2);
    }

    /**
     * Creates connection monitoring task with a default log,
     * no root stats, default root {@link MARSNode#getRoot() MARSNode},
     * and an empty list of connectors.
     */
    public ConnectorsMonitoringTask() {
        this(null, QDLog.log, null, MARSNode.getRoot(), null);
    }

    /**
     * Creates connection monitoring task with a specified root QDStats,
     * default log, default root {@link MARSNode#getRoot() MARSNode},
     * and an empty list of connectors.
     */
    public ConnectorsMonitoringTask(QDStats rootStats) {
        this(null, QDLog.log, rootStats, MARSNode.getRoot(), null);
    }

    /**
     * Creates connection monitoring task with a specified root QDStats and a list of connectors,
     * default log, and default root {@link MARSNode#getRoot() MARSNode},
     */
    public ConnectorsMonitoringTask(QDStats rootStats, List<MessageConnector> connectors) {
        this(null, QDLog.log, rootStats, MARSNode.getRoot(), connectors);
    }

    /**
     * Creates connection monitoring task with a specified log, root QDStats, a list of connectors,
     * and default root {@link MARSNode#getRoot() MARSNode},
     * @deprecated Because {@link QDLog} is deprecated. It is not used by this constructor.
     *             Use other constructors and configuration methods.
     */
    public ConnectorsMonitoringTask(QDLog log, QDStats rootStats, List<MessageConnector> connectors) {
        this(null, QDLog.log, rootStats, MARSNode.getRoot(), connectors);
        QDLog.log.warn("WARNING: Using DEPRECATED ConnectorsMonitoringTask constructor with deprecated QDLog instance");
    }

    /**
     * Creates connection monitoring task with a specified root QDStats and root MARSNode,
     * default log and an empty list of connectors,
     */
    public ConnectorsMonitoringTask(QDStats rootStats, MARSNode rootNode) {
        this(null, QDLog.log, rootStats, rootNode, null);
    }

    /**
     * Creates connection monitoring task with a specified name, log, root QDStat, root MARSNode, and
     * a list of connectors.
     *
     * @param name the name of the endpoint to which this task relates.
     * @param log the log to write result to (can be null -- does not write to log in this case).
     * @param rootStats the root stats to collect common information on storage and subscription from. Can be null.
     * @param rootNode root MARSNode.
     * @param connectors a list of connectors (can be null for empty list of connectors).
     */
    public ConnectorsMonitoringTask(String name, Logging log, QDStats rootStats, MARSNode rootNode,
        List<MessageConnector> connectors)
    {
        this.name = name;
        this.log = log;

        MARSNode node = name == null ?
            rootNode.subNode("qd", "QD Stats") :
            rootNode.subNode("qd-" + name, "QD Stats for " + name + " endpoint");
        node.setValue(QDFactory.getVersion());

        this.connectorsNode = node.subNode("connectors", "All connectors");
        this.subscriptionNode = node.subNode("subscription", "Total subscription size");
        this.storageNode = node.subNode("storage", "Total storage size");
        this.bufferNode = node.subNode("buffer", "Total outgoing buffer size");

        rootCounters = new IOCounters(null, node);

        time.update(System.currentTimeMillis());

        if (connectors != null)
            addConnectors(connectors);
        if (rootStats != null)
            addStats(rootStats);
    }

    /**
     * Releases resources associated with this connectors monitor task.
     */
    public void close() {
        cpu.close();
    }

    public synchronized void addStats(QDStats stats) {
        this.rootStats.add(stats);
    }

    public void addConnector(MessageConnector connector) {
        addConnectors(Collections.singleton(connector));
    }

    public synchronized void addConnectors(Collection<MessageConnector> connectors) {
        removeConnectors(connectors);
        for (MessageConnector connector : connectors) {
            addNamedConnectorImpl(connector.getName(), connector, null);
            rootCounters.addConnector(connector, null);
        }
    }

    private void addNamedConnectorImpl(String name, MessageConnector connector, IOCounter prev) {
        IOCounters ios = countersByName.get(name);
        if (ios == null) {
            ios = new IOCounters(name, connectorsNode.subNode(name, "Connector statistics for " + name));
            countersByName.put(name, ios);
        }
        connectorsMap.put(connector, ios.addConnector(connector, prev));
    }

    public synchronized void removeConnectors(Collection<MessageConnector> connectors) {
        for (MessageConnector connector : connectors) {
            IOCounter counter = connectorsMap.remove(connector);
            if (counter != null) {
                removeNamedConnectorImpl(counter.getName(), connector);
                rootCounters.removeConnector(connector);
            }
        }
    }

    private void removeNamedConnectorImpl(String name, MessageConnector connector) {
        IOCounters ios = countersByName.get(name);
        ios.removeConnector(connector);
        if (ios.isEmpty())
            countersByName.remove(name);
    }

    /**
     * Logs report and published stats to MARS.
     */
    @Override
    public void run() {
        if (log != null)
            log.info("\b" + report());
        else
            reportImpl(null); // don't produce string for log
    }

    /**
     * Produces report string as result and publishes stats to MARS.
     */
    public String report() {
        StringBuilder sb = new StringBuilder();
        reportImpl(sb);
        return sb.toString();
    }

    private synchronized void reportImpl(StringBuilder sb) {
        long elapsedTime = time.update(System.currentTimeMillis());
        if (elapsedTime <= 0)
            elapsedTime = 1; // just in case to avoid division by zero

        long subscription = 0;
        long storage = 0;
        long buffer = 0;

        for (QDStats stats : rootStats) {
            subscription += stats.getOrVoid(QDStats.SType.UNIQUE_SUB).getValue(QDStats.SValue.RID_SIZE);
            storage += stats.getOrVoid(QDStats.SType.STORAGE_DATA).getValue(QDStats.SValue.RID_SIZE);
            buffer += stats.getOrVoid(QDStats.SType.AGENT_DATA).getValue(QDStats.SValue.RID_SIZE);
        }

        subscriptionNode.setDoubleValue(subscription);
        storageNode.setDoubleValue(storage);
        bufferNode.setDoubleValue(buffer);

        updateConnectorNames();
        rootCounters.update(null);
        layout.clear();
        for (IOCounters ios : countersByName.values())
            ios.update(layout);

        if (sb == null) {
            // do not produce string for log but still report everything to MARS
            rootCounters.report(integerFormat, elapsedTime, null);
            for (IOCounters ios : countersByName.values())
                ios.report(integerFormat, elapsedTime, null);
            return;
        }

        if (name != null)
            sb.append("{").append(name).append("} ");

        sb.append("Subscription: ").append(integerFormat.format(subscription)).
            append("; Storage: ").append(integerFormat.format(storage)).
            append("; Buffer: ").append(integerFormat.format(buffer)).
            append("; ");
            rootCounters.report(integerFormat, elapsedTime, sb);
            sb.append("; CPU: ").append(percentFormat.format(cpu.getCpuUsage()));

        for (IOCounters ios : countersByName.values()) {
            sb.append("\n    ");
            padR(sb, ios.getDisplayName(), layout.maxNameLen);
            sb.append(" ");
            padR(sb, ios.getDisplayAddr(), layout.maxAddrLen);
            sb.append(" [").append(ios.getConnectionsCount()).append("] ");
            ios.report(integerFormat, elapsedTime, sb);
        }
    }

    private void updateConnectorNames() {
        List<IOCounter> updatedNames = new ArrayList<>();
        for (IOCounters counters : countersByName.values())
            counters.updateNames(updatedNames);
        for (IOCounter counter : updatedNames) {
            MessageConnector connector = counter.getConnector();
            // remove by old name
            removeNamedConnectorImpl(counter.getName(), connector);
            // add by new name (keeping old counter stats)
            addNamedConnectorImpl(connector.getName(), connector, counter);
        }
    }

    static void padR(StringBuilder sb, String s, int len) {
        sb.append(s);
        for (int i = s.length(); i < len; i++)
            sb.append(' ');
    }
}
