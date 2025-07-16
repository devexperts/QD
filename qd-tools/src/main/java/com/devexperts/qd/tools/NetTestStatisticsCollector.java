/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.tools;

import com.devexperts.logging.Logging;
import com.devexperts.mars.common.MARSNode;
import com.devexperts.qd.qtp.MessageConnector;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Collects throughput statistics.
 */
class NetTestStatisticsCollector implements Runnable {
    private static final Logging log = Logging.getLogging(NetTestStatisticsCollector.class);
    private final NumberFormat integerFormat = NumberFormat.getIntegerInstance(Locale.US);

    private final NetTestSide side;
    private final int instanceCount;
    private final MARSNode[] dataRecordsNodes;
    private final MARSNode[] latenciesNodes;
    private final MARSNode sumDataRecordsNodes;
    private final MARSNode sumLatenciesNodes;
    private long lastTime = Long.MIN_VALUE;
    private long lastSumRecords = 0;
    private long lastSumLatency = 0;
    private long[] lastCounts;
    private long[] lastLatencies;
    private final NetTestWorkingThread.Stats stats = new NetTestWorkingThread.Stats();

    NetTestStatisticsCollector(NetTestSide side) {
        this.side = side;
        this.instanceCount = side.config.instanceCount;
        MARSNode connectorsNode = MARSNode.getRoot().subNode(side.getClass().getSimpleName() + "-" + side.config.name,
            "Statistics for " + side.getClass().getSimpleName());
        dataRecordsNodes = new MARSNode[instanceCount];
        latenciesNodes = new MARSNode[instanceCount];
        for (int i = 0; i < dataRecordsNodes.length; i++) {
            MARSNode connector = connectorsNode.subNode(side.getClass().getSimpleName() + " " + (i + 1));
            dataRecordsNodes[i] = connector.subNode("data_records",
                "Number of data records per second");
            latenciesNodes[i] = connector.subNode("data_lag",
                "Average record-weighted lag of data records in us");
        }
        sumDataRecordsNodes = connectorsNode.subNode("data_records",
            "Total number of data records per second");
        sumLatenciesNodes = connectorsNode.subNode("data_lag",
            "Total average record-weighted lag of data records in us");
    }

    public void run() {
        long curTime = System.currentTimeMillis();
        long curSumRecords = 0;
        long curSumLatency = 0;
        long[] curRecords = new long[instanceCount];
        long[] curLatencies = new long[instanceCount];
        for (NetTestWorkingThread workingThread : side.threads) {
            workingThread.getStats(stats);
            curSumLatency += stats.sumLatency;
            curSumRecords += stats.processedRecords;
            curRecords[workingThread.index - 1] = stats.processedRecords;
            curLatencies[workingThread.index - 1] = stats.sumLatency;
        }
        if (lastTime == Long.MIN_VALUE) {
            lastTime = curTime;
            lastSumRecords = curSumRecords;
            lastSumLatency = curSumLatency;
            lastCounts = curRecords;
            lastLatencies = curLatencies;
            return;
        }
        long timeInterval = curTime - lastTime;
        long curRps = (curSumRecords - lastSumRecords) * 1000 / timeInterval;
        long curLatencyUs = Math.round((curSumLatency - lastSumLatency) * 1000.0 / (curSumRecords - lastSumRecords));

        int connected = 0;
        for (MessageConnector connector : side.connectors) {
            connected += connector.getConnectionCount();
        }
        StringBuilder perConnectorStats = new StringBuilder();
        for (int i = 0; i < instanceCount; i++) {
            long rps = (curRecords[i] - lastCounts[i]) * 1000 / timeInterval;
            dataRecordsNodes[i].setDoubleValue(rps);
            
            long latencyUs = Math.round((curLatencies[i] - lastLatencies[i]) * 1000.0 / (curRecords[i] - lastCounts[i]));
            latenciesNodes[i].setDoubleValue(latencyUs);
            
            perConnectorStats.append("\n    ").append(side.config.name).append('.').append(i + 1).append(" data ")
                .append(integerFormat.format(rps)).append(" rps")
                .append(", lag ").append(integerFormat.format(latencyUs)).append(" us");
        }
        sumDataRecordsNodes.setDoubleValue(curRps);
        sumLatenciesNodes.setDoubleValue(curLatencyUs);
        log.info("\b{*" + side.getClass().getSimpleName() + "-" + side.config.name + "*} " +
            "data " + integerFormat.format(curRps) + " rps, " + "lag " + integerFormat.format(curLatencyUs) + " us, " +
            "instances " + instanceCount + ", " +
            "connections " + connected + perConnectorStats);
        lastTime = curTime;
        lastSumRecords = curSumRecords;
        lastSumLatency = curSumLatency;
        lastCounts = curRecords;
        lastLatencies = curLatencies;
    }
}
