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
package com.dxfeed.webservice.comet;

import com.devexperts.annotation.Description;
import com.devexperts.logging.Logging;
import com.devexperts.management.Management;
import com.devexperts.mars.common.MARSNode;
import com.devexperts.qd.monitoring.MonitoringEndpoint;
import org.cometd.bayeux.server.BayeuxServer;
import org.cometd.bayeux.server.ServerSession;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CometDMonitoring implements CometDMonitoringMXBean {

    public static final String STATS_ATTR = "sessionStats";
    public static final String TMP_STATS_ATTR = "tmpSessionStats";

    private static final Logging log = Logging.getLogging(CometDMonitoring.class);

    private String name;
    private BayeuxServer server;
    private volatile boolean detailed;

    private MonitoringEndpoint monitoring;
    private MARSNode sessionsNode;
    private MARSNode readNode;
    private MARSNode writeNode;

    private long startTime;
    private long lastTime;
    private int numSessions;
    private SessionStats totals = new SessionStats();
    private SessionStats currentTotals = new SessionStats();

    public void init(String name, BayeuxServer server, boolean detailed) {
        this.name = name;
        this.server = server;
        this.detailed = detailed;
        startTime = lastTime = System.currentTimeMillis();

        // Initialize MARS nodes
        MARSNode rootNode = MARSNode.getRoot().subNode(name);
        sessionsNode = rootNode.subNode("sessions", "Number of sessions");
        readNode = rootNode.subNode("read", "Number of received packets");
        writeNode = rootNode.subNode("write", "Number of sent packets");

        //FIXME Initialize Jetty CometD JMX
        // Due to different classloaders javax.management.InstanceAlreadyExistsException is thrown
        // when several cometd servers are registered from different WARs.

        // Initialize JMX
        Management.registerMBean(this, CometDMonitoringMXBean.class,
            "com.devexperts.qd.monitoring:type=CometD,name=" + name);

        // Initialize monitoring
        monitoring = MonitoringEndpoint.newBuilder()
            .withProperty(MonitoringEndpoint.NAME_PROPERTY, name)
            .acquire();

        monitoring.registerMonitoringTask(() -> {
            long currentTime = System.currentTimeMillis();
            double period = (currentTime - lastTime) / 1000.0;

            StringBuilder buff = new StringBuilder();
            currentTotals.clear();
            collectStatistics(currentTotals, buff, period, isDetailed(), currentTime);

            numSessions = currentTotals.numSessions;
            sessionsNode.setIntValue(numSessions);
            readNode.setDoubleValue(SessionStats.getRated(totals.read, period));
            writeNode.setDoubleValue(SessionStats.getRated(totals.write, period));

            totals.accumulate(currentTotals, true);

            String message = totals.getTotalRated(numSessions, period) + buff.toString();
            log.info("\b{" + name + "} (total) " + message);

            totals.clear();
            totals.accumulate(currentTotals, false);
            lastTime = currentTime;
        });
    }

    public void destroy() {
        monitoring.release();
    }

    @Override
    public int getNumSessions() {
        return numSessions;
    }

    @Override
    public boolean isDetailed() {
        return detailed;
    }

    @Override
    public void setDetailed(boolean detailed) {
        this.detailed = detailed;
    }

    @Override
    public String dumpAllSessionsAverage() {
        return dumpSessionsAverage("id", Integer.MAX_VALUE);
    }

    @Override
    public String dumpSessionsAverage(String sortColumn, int limit) {
        long currentTime = System.currentTimeMillis();
        double period = (currentTime - startTime) / 1000.0;

        SessionStats currentTotals = new SessionStats();
        StringBuilder buff = new StringBuilder();

        collectStatistics().stream()
            .sorted(SessionStats.getComparator(sortColumn))
            .limit(limit)
            .forEach(stats -> {
                currentTotals.accumulate(stats, true);
                stats.dumpStats(buff, period, currentTime);
            });

        String message = "sort: " + sortColumn + "; limit: " + ((limit != Integer.MAX_VALUE) ? limit : "max") + "; ";
        message += currentTotals.getTotalRated(currentTotals.numSessions, period) + buff.toString();
        log.info("\bDump Sessions Average {" + name + "} " + message);
        return message;
    }

    @Override
    public String dumpAllSessionsTotal() {
        return dumpSessionsTotal("id", Integer.MAX_VALUE);
    }

    @Override
    public String dumpSessionsTotal(String sortColumn, int limit) {
        long currentTime = System.currentTimeMillis();
        double period = 1.0;

        SessionStats currentTotals = new SessionStats();
        StringBuilder buff = new StringBuilder();

        collectStatistics().stream()
            .sorted(SessionStats.getComparator(sortColumn))
            .limit(limit)
            .forEach(stats -> {
                currentTotals.accumulate(stats, true);
                stats.dumpStats(buff, period, currentTime);
            });

        String message = "sort: " + sortColumn + "; limit: " + ((limit != Integer.MAX_VALUE) ? limit : "max") + "; ";
        message += currentTotals.getTotalRated(1, period) + buff.toString();
        // Total sum is calculated - replace units
        message = message.replaceAll("mps", "msg").replaceAll("pps", "pkt");

        log.info("\bDump Sessions Total {" + name + "} " + message);
        return message;
    }

    @Override
    public void terminateSession(@Description("Session ID") String sessionId) {
        ServerSession session = server.getSession(sessionId);
        SessionStats stats = (SessionStats) session.getAttribute(STATS_ATTR);
        if (stats == null) {
            log.warn("Terminate session '" + sessionId + "': failed, session not found");
            return;
        }

        log.info("Terminate session '" + sessionId + "': ok");
        session.disconnect();
    }

    // Utility methods

    protected void collectStatistics(SessionStats totalStats, StringBuilder buff,
        double period, boolean printSessions, long currentTime)
    {
        server.getSessions().forEach(session -> {
            SessionStats stats = (SessionStats) session.getAttribute(STATS_ATTR);
            if (stats == null)
                return;

            totalStats.accumulate(stats, true);

            if (printSessions) {
                SessionStats sessionTemp = (SessionStats) session.getAttribute(TMP_STATS_ATTR);
                if (sessionTemp == null)
                    return;

                sessionTemp.accumulate(stats, true);
                sessionTemp.dumpStats(buff, period, currentTime);
                sessionTemp.clear();
                sessionTemp.accumulate(stats, false);
            }
        });
    }

    protected List<SessionStats> collectStatistics() {
        return server.getSessions().stream()
            .map(session -> (SessionStats) session.getAttribute(STATS_ATTR))
            .filter(Objects::nonNull)
            .map(SessionStats::clone)
            .collect(Collectors.toList());
    }
}
