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
import com.devexperts.mars.common.MARSNode;
import com.devexperts.mars.jvm.CpuCounter;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimeFormat;

import java.text.NumberFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Runnable task that logs statistics and sends them to MARS for a given root {@link QDStats}
 * and a list of {@link MessageConnector} instances every time it is invoked. Connectors can
 * be added on the fly with {@link #addConnectors}, roots stats for summaries with
 * {@link #addStats}.
 * <p>This class is safe for use from multiple threads.
 */
public class ConnectorsMonitoringTask implements Runnable {

    // Flag indicating whether to log additional stats for striped connectors
    private static final boolean DEFAULT_LOG_STRIPED_CONNECTORS =
        SystemProperties.getBooleanProperty("com.devexperts.qd.logStripedConnectors", false);

    /**
     * The name which is null when was not explicitly specified (by legacy code).
     * It is always set when part of some endpoint and is equal to endpoint's name.
     */
    private final String name;

    private final Logging log; // can be null (!!!)

    private final MARSNode connectorsNode;
    private final MARSNode subscriptionNode;
    private final MARSNode storageNode;
    private final MARSNode bufferNode;
    private final MARSNode droppedNode;
    private final MARSNode droppedLogNode;
    private final Queue<String> queueDroppedLog = new ConcurrentLinkedQueue<>();
    private long prevDropped;
    private boolean closed;

    private final MonitoringCounter time = new MonitoringCounter();

    private final List<QDStats> rootStats = new CopyOnWriteArrayList<>();
    private final List<MessageConnector> connectors = new CopyOnWriteArrayList<>();

    private final CpuCounter cpu = new CpuCounter();
    private final IOCounters rootCounters;
    private final Map<String, IOCounters> countersByName = new TreeMap<>();
    private final Map<IOCounterKey, IOCounter> snapshot = new HashMap<>();

    private final NumberFormat integerFormat = NumberFormat.getIntegerInstance(Locale.US);
    private final NumberFormat percentFormat = NumberFormat.getPercentInstance(Locale.US);

    {
        percentFormat.setMaximumFractionDigits(2);
        percentFormat.setMinimumFractionDigits(2);
    }

    private boolean logStripedConnectors = DEFAULT_LOG_STRIPED_CONNECTORS;

    /**
     * Creates a connection monitoring task with a default log,
     * no root stats, default root {@link MARSNode#getRoot() MARSNode},
     * and an empty list of connectors.
     */
    public ConnectorsMonitoringTask() {
        this(null, Logging.getLogging(ConnectorsMonitoringTask.class), null, MARSNode.getRoot(), null);
    }

    /**
     * Creates a connection monitoring task with a specified root QDStats,
     * default log, default root {@link MARSNode#getRoot() MARSNode},
     * and an empty list of connectors.
     */
    public ConnectorsMonitoringTask(QDStats rootStats) {
        this(null, Logging.getLogging(ConnectorsMonitoringTask.class), rootStats, MARSNode.getRoot(), null);
    }

    /**
     * Creates a connection monitoring task with a specified root QDStats and a list of connectors,
     * default log, and default root {@link MARSNode#getRoot() MARSNode}.
     */
    public ConnectorsMonitoringTask(QDStats rootStats, List<MessageConnector> connectors) {
        this(null, Logging.getLogging(ConnectorsMonitoringTask.class), rootStats, MARSNode.getRoot(), connectors);
    }

    /**
     * Creates a connection monitoring task with a specified root QDStats and root MARSNode,
     * default log and an empty list of connectors.
     */
    public ConnectorsMonitoringTask(QDStats rootStats, MARSNode rootNode) {
        this(null, Logging.getLogging(ConnectorsMonitoringTask.class), rootStats, rootNode, null);
    }

    /**
     * Creates a connection monitoring task with a specified name, log, root QDStat, root MARSNode, and
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
        this.droppedNode = node.subNode("dropped", "Total dropped records");
        this.droppedLogNode = node.subNode("dropped_log", "Last message about dropped records");

        rootCounters = new IOCounters(null, node);

        time.update(System.currentTimeMillis());

        if (connectors != null)
            addConnectors(connectors);
        if (rootStats != null)
            addStats(rootStats);

        if (log != null) {
            log.info("Log striped connectors: " + this.logStripedConnectors);
        }
    }

    /**
     * Releases resources associated with this connectors monitor task.
     */
    public void close() {
        closed = true;
        cpu.close();
        queueDroppedLog.clear();
    }

    public synchronized void addStats(QDStats stats) {
        this.rootStats.add(stats);
    }

    public synchronized void addConnectors(Collection<MessageConnector> connectors) {
        this.connectors.addAll(connectors);
    }

    public synchronized void removeConnectors(Collection<MessageConnector> connectors) {
        this.connectors.removeAll(connectors);
    }

    public boolean isLogStripedConnectors() {
        return logStripedConnectors;
    }

    public void setLogStripedConnectors(boolean logStripedConnectors) {
        if (this.logStripedConnectors != logStripedConnectors) {
            this.logStripedConnectors = logStripedConnectors;
            if (log != null) {
                log.info("Log striped connectors: " + this.logStripedConnectors);
            }
        }
    }

    /**
     * Logs report and published stats to MARS.
     */
    @Override
    public void run() {
        if (log != null) {
            log.info("\b" + report());
        } else {
            reportImpl(null); // don't produce string for log
        }
    }

    /**
     * Produces report string as result and publishes stats to MARS.
     */
    public String report() {
        StringBuilder sb = new StringBuilder();
        reportImpl(sb);
        return sb.toString();
    }

    private long elapsedTime() {
        long elapsedTime = time.update(System.currentTimeMillis());
        // just in case to avoid division by zero
        return (elapsedTime <= 0) ? 1 : elapsedTime;
    }

    private synchronized void reportImpl(StringBuilder buff) {
        long elapsedTime = elapsedTime();

        long subscription = 0;
        long storage = 0;
        long buffer = 0;
        long totalDroppedRecords = 0;

        for (QDStats stats : rootStats) {
            subscription += stats.getOrVoid(QDStats.SType.UNIQUE_SUB).getValue(QDStats.SValue.RID_SIZE);
            storage += stats.getOrVoid(QDStats.SType.STORAGE_DATA).getValue(QDStats.SValue.RID_SIZE);
            buffer += stats.getOrVoid(QDStats.SType.AGENT_DATA).getValue(QDStats.SValue.RID_SIZE);
            totalDroppedRecords += stats.getOrVoid(QDStats.SType.DROPPED_DATA).getValue(QDStats.SValue.RID_SIZE);
        }
        long droppedByPeriod = totalDroppedRecords - prevDropped;
        prevDropped = totalDroppedRecords;

        subscriptionNode.setDoubleValue(subscription);
        storageNode.setDoubleValue(storage);
        bufferNode.setDoubleValue(buffer);
        droppedNode.setDoubleValue(droppedByPeriod);
        for (String s; (s = queueDroppedLog.poll()) != null; ) {
            droppedLogNode.setValue(TimeFormat.GMT.withTimeZone().format(System.currentTimeMillis()) + " " + s);
        }

        rootCounters.beforeAggregate();
        snapshot.values().forEach(IOCounter::resetUnused);
        countersByName.values().forEach(IOCounters::beforeAggregate);

        for (MessageConnector connector : connectors) {
            List<QDStats> stripedQdStats = getStripedConnections(
                connector.getStats().getOrVoid(QDStats.SType.CONNECTIONS));
            // Connector key without stripes
            IOCounterKey key = new IOCounterKey(connector, null);
            IOCounters counters = countersByName.computeIfAbsent(key.name, this::createSumStats);

            if (stripedQdStats.isEmpty()) {
                // Connectors are not closed, so it is safe to reuse their QD stats
                IOCounter stats = snapshot.computeIfAbsent(key, k -> new IOCounter(connector, connector.getStats()));
                stats.collect();

                rootCounters.aggregate(stats);
                counters.aggregate(stats);
            } else {
                // Striped connectors are always aggregated per stripe
                for (QDStats qdStats : stripedQdStats) {
                    // Connector+Stripe key
                    IOCounterKey stripeKey = new IOCounterKey(connector, getStripeName(qdStats));
                    IOCounter stats = snapshot.computeIfAbsent(stripeKey, k -> new IOCounter(connector, qdStats));

                    // Striped connections can be reopened (or re-striped)
                    // and therefore have new QD stats with the same key, so need to update them if changed
                    if (stats.getStats() != qdStats) {
                        stats = new IOCounter(connector, qdStats);
                        snapshot.put(stripeKey, stats);
                    }
                    stats.collect();

                    rootCounters.aggregate(stats);
                    counters.aggregate(stats);
                    countersByName.computeIfAbsent(stripeKey.name, this::createStripeSumStats).aggregate(stats);
                }
            }
        }

        // Clean unused IOCounters and IOCounter values to avoid memory leak
        // (this can happen on connector's removal or reconfiguration, e.g. re-striping)
        rootCounters.afterAggregate();
        countersByName.values().removeIf(IOCounters::afterAggregate);
        snapshot.values().removeIf(IOCounter::isUnused);

        // Do not report stripe stats if "per-stripe" logging is disabled.
        List<IOCounters> reportedCounters = countersByName.values().stream()
            .filter(c -> logStripedConnectors || !c.isStripeNode())
            .collect(Collectors.toList());

        if (buff == null) {
            // Do not produce string for log but still report everything to MARS
            rootCounters.report(integerFormat, elapsedTime, null);
            for (IOCounters ios : reportedCounters) {
                ios.report(integerFormat, elapsedTime, null);
            }
            return;
        }

        if (name != null)
            buff.append("{").append(name).append("} ");

        buff.append("Subscription: ").append(integerFormat.format(subscription))
            .append("; Storage: ").append(integerFormat.format(storage))
            .append("; Buffer: ").append(integerFormat.format(buffer))
            .append("; Dropped: ").append(integerFormat.format(droppedByPeriod))
            .append("; ");
        rootCounters.report(integerFormat, elapsedTime, buff);
        buff.append("; CPU: ").append(percentFormat.format(cpu.getCpuUsage()));

        // Calculate string lengths for formatting
        int maxNameLen = 0;
        int maxAddressLen = 0;
        for (IOCounters ios : reportedCounters) {
            maxNameLen = Math.max(maxNameLen, ios.displayName.length());
            maxAddressLen = Math.max(maxAddressLen, ios.displayAddress.length());
        }

        for (IOCounters ios : reportedCounters) {
            buff.append("\n    ");
            padRight(buff, ios.displayName, maxNameLen);
            buff.append(" ");
            padRight(buff, ios.displayAddress, maxAddressLen);
            buff.append(" [").append(ios.connectionsCount).append("] ");
            ios.report(integerFormat, elapsedTime, buff);
        }
    }

    /*
     * Depends on striped connections having "stripe=..." property at the start.
     */
    private List<QDStats> getStripedConnections(QDStats stats) {
        return stats.getAll(QDStats.SType.CONNECTION).stream()
            .filter(s -> s.getKeyProperties().startsWith("stripe="))
            .collect(Collectors.toList());
    }

    private String getStripeName(QDStats stats) {
        String striperProperty = stats.getKeyProperties().substring("stripe=".length());
        int to = striperProperty.indexOf(',');
        return striperProperty.substring(0, (to > 0) ? to : striperProperty.length());
    }

    private IOCounters createSumStats(String name) {
        return new IOCounters(name, connectorsNode);
    }

    private IOCounters createStripeSumStats(String name) {
        return new IOCounters(name, connectorsNode, true);
    }

    private static void padRight(StringBuilder sb, String s, int len) {
        sb.append(s);
        for (int i = s.length(); i < len; i++) {
            sb.append(' ');
        }
    }

    void droppedLogAccept(String message) {
        if (!closed)
            queueDroppedLog.add(message);
    }
}
