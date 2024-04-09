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

import com.devexperts.mars.common.MARSMonitoredBean;
import com.devexperts.mars.common.MARSNode;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.util.LogUtil;

import java.text.NumberFormat;
import java.util.Arrays;

import static com.devexperts.qd.monitoring.IOCounter.COUNTER_COUNT;
import static com.devexperts.qd.monitoring.IOCounter.DATA_READ_LAGS;
import static com.devexperts.qd.monitoring.IOCounter.DATA_READ_RECORDS;
import static com.devexperts.qd.monitoring.IOCounter.DATA_WRITE_LAGS;
import static com.devexperts.qd.monitoring.IOCounter.DATA_WRITE_RECORDS;
import static com.devexperts.qd.monitoring.IOCounter.READ_BYTES;
import static com.devexperts.qd.monitoring.IOCounter.READ_RTTS;
import static com.devexperts.qd.monitoring.IOCounter.SUB_READ_RECORDS;
import static com.devexperts.qd.monitoring.IOCounter.SUB_WRITE_RECORDS;
import static com.devexperts.qd.monitoring.IOCounter.VALUES;
import static com.devexperts.qd.monitoring.IOCounter.WRITE_BYTES;
import static com.devexperts.qd.monitoring.IOCounter.WRITE_RTTS;

/**
 * A collection of {@link IOCounter} instances of the same name that report the summary
 * statistics to MARS nodes and provides a text report.
 */
class IOCounters {

    private final String name;
    private final boolean stripeNode;

    // Publication to MARS
    private final MARSNode node;
    private final MARSNode address;
    private final MARSNode connections;
    private final MARSNode connectionsClosed;

    private final VNode bytesRead;
    private final VNode bytesWrite;
    private final VNode subRecordsRead;
    private final VNode subRecordsWrite;
    private final VNode dataRecordsRead;
    private final VNode dataRecordsWrite;
    private final VNode dataLagRead;
    private final VNode dataLagWrite;
    private final VNode rtt;

    // Publication to MARS
    private MessageConnector registeredConnector;
    private MARSMonitoredBean<?> monitoredConnectorBean;

    // Statistics
    private final SumDeltaCounter[] totals;
    String displayName;
    String displayAddress;

    int connectionsCount;
    long oldConnectionsClosedTotal;
    long connectionsClosedTotal;
    long connectionsClosedDelta;

    // Temp variables for aggregation calculation
    private DataScheme scheme;
    private boolean isSchemeDiverge;
    private int recordCount;
    private boolean unused;
    private MessageConnector prevConnector;
    // Variables to calculate name and address, see calculateNameAndAddress() method
    private int sameAddressCount;
    private int otherAddressCount;

    // name == null for "root" IOCounters collection
    IOCounters(String name, MARSNode node) {
        this(name, node, false);
    }

    IOCounters(String name, MARSNode rootNode, boolean stripeNode) {
        this.name = name;
        this.node = (name == null) ? rootNode : rootNode.subNode(name, "Connector statistics for " + name);
        this.stripeNode = stripeNode;

        address = (name == null) ? null : node.subNode("address", "Address");
        connections = node.subNode("connections", "Number of connections");
        connectionsClosed = node.subNode("connections_closed", "Number of closed connections per reporting interval");
        bytesRead = new VNode(node, "bytes_read", "Number of bytes read per second");
        bytesWrite = new VNode(node, "bytes_write", "Number of bytes written per second");
        subRecordsRead = new VNode(node, "sub_records_read", "Number of subscription records read per second");
        subRecordsWrite = new VNode(node, "sub_records_write", "Number of subscription records written per second");
        dataRecordsRead = new VNode(node, "data_records_read", "Number of data records read per second");
        dataRecordsWrite = new VNode(node, "data_records_write", "Number of data records written per second");
        dataLagRead = new VNode(node, "data_lag_read", "Average record-weighted lag of read data records in us");
        dataLagWrite = new VNode(node, "data_lag_write", "Average record-weighted lag of written data records in us");
        rtt = new VNode(node, "rtt", "Average record-weighted connection round-trip time in us");

        this.totals = new SumDeltaCounter[COUNTER_COUNT];
        for (int i = 0; i < COUNTER_COUNT; i++) {
            totals[i] = new SumDeltaCounter(VALUES[i]);
        }
        beforeAggregate();
    }

    boolean isStripeNode() {
        return stripeNode;
    }

    void beforeAggregate() {
        unused = true;
        prevConnector = null;

        scheme = null;
        isSchemeDiverge = false;
        recordCount = 0;

        clearAddress();
        oldConnectionsClosedTotal = connectionsClosedTotal;
        connectionsCount = 0;
        connectionsClosedTotal = 0;

        // Clear totals
        Arrays.stream(totals).forEach(SumDeltaCounter::clear);
    }

    /*
     * This method relies on external sequential ordering of stats for striped connector.
     */
    void aggregate(IOCounter stats) {
        unused = false;
        MessageConnector connector = stats.getConnector();

        if (name != null && prevConnector == null && !stripeNode) {
            // Allow to remove previously registered connector by the same name if it was removed
            if (registeredConnector != null && registeredConnector != connector) {
                unregisterConnector();
            }
            if (registeredConnector == null) {
                registeredConnector = connector;
                monitoredConnectorBean = MARSMonitoredBean.forInstance(node, connector);
            }
        }

        DataScheme statsScheme = (connector.getStats() != null) ? connector.getStats().getScheme() : null;
        if (!isSchemeDiverge && statsScheme != null && statsScheme != scheme) {
            if (scheme == null) {
                scheme = statsScheme;
                recordCount = scheme.getRecordCount();
            } else {
                isSchemeDiverge = true;
                recordCount = 0;
            }
            // Clear record totals
            Arrays.stream(totals).forEach(d -> d.clearRid(recordCount));
        }

        if (stripeNode) {
            // Connection count for stripe node
            connectionsCount += stats.getStats().getAll(QDStats.SType.CONNECTION).size();
        } else if (connector != prevConnector) {
            // Use connection count for sum node, but only once!
            connectionsCount += connector.getConnectionCount();
            connectionsClosedTotal += connector.getClosedConnectionCount();
            aggregateAddress(connector);
        }

        // Aggregate deltas
        stats.aggregate(totals);

        prevConnector = connector;
    }

    boolean afterAggregate() {
        calculateNameAndAddress();
        connectionsClosedDelta = (connectionsClosedTotal - oldConnectionsClosedTotal);

        if (unused) {
            unregisterConnector();
        }
        return unused;
    }

    private void clearAddress() {
        displayAddress = "";
        sameAddressCount = 0;
        otherAddressCount = 0;
    }

    private void aggregateAddress(MessageConnector connector) {
        String address = LogUtil.hideCredentials(connector.getAddress());
        if (displayAddress.isEmpty()) {
            displayAddress = address;
            sameAddressCount = 1;
        } else if (address.equals(displayAddress)) {
            sameAddressCount++;
        } else {
            otherAddressCount++;
        }
    }

    /**
     * Calculates the connector name (e.g. "opra*6") and address (e.g. "localhost:6048*4,&lt;other&gt;*2").
     */
    private void calculateNameAndAddress() {
        int totalAddressCount = sameAddressCount + otherAddressCount;
        displayName = (totalAddressCount > 1) ? name + "*" + totalAddressCount : name;

        if (sameAddressCount > 1 && otherAddressCount > 0) {
            displayAddress += "*" + sameAddressCount;
        }
        if (otherAddressCount > 0) {
            displayAddress += ",<other>";
            if (otherAddressCount > 1)
                displayAddress += "*" + otherAddressCount;
        }
    }

    private void unregisterConnector() {
        if (registeredConnector != null) {
            registeredConnector = null;
            monitoredConnectorBean.close();
            monitoredConnectorBean = null;
        }
    }

    void report(NumberFormat format, long elapsedTime, StringBuilder buff) {
        // Report address and connection count (the root collection does not report address)
        if (name != null)
            address.setValue(displayAddress);
        connections.setIntValue(connectionsCount);
        connectionsClosed.setValue(String.valueOf(connectionsClosedDelta));

        // Report monitored props for a registered connector
        if (monitoredConnectorBean != null)
            monitoredConnectorBean.run();

        // Compute values and post them to MARS
        bytesRead.set(rate(totals[READ_BYTES].totalDelta, elapsedTime));
        bytesWrite.set(rate(totals[WRITE_BYTES].totalDelta, elapsedTime));
        subRecordsRead.set(rate(totals[SUB_READ_RECORDS].totalDelta, elapsedTime));
        subRecordsWrite.set(rate(totals[SUB_WRITE_RECORDS].totalDelta, elapsedTime));
        dataRecordsRead.set(rate(totals[DATA_READ_RECORDS].totalDelta, elapsedTime));
        dataRecordsWrite.set(rate(totals[DATA_WRITE_RECORDS].totalDelta, elapsedTime));
        dataLagRead.set(frac(totals[DATA_READ_LAGS].totalDelta, totals[DATA_READ_RECORDS].totalDelta));
        dataLagWrite.set(frac(totals[DATA_WRITE_LAGS].totalDelta, totals[DATA_WRITE_RECORDS].totalDelta));
        rtt.set(frac(totals[READ_RTTS].totalDelta + totals[WRITE_RTTS].totalDelta,
            totals[DATA_READ_RECORDS].totalDelta + totals[DATA_WRITE_RECORDS].totalDelta +
                totals[SUB_READ_RECORDS].totalDelta + totals[SUB_WRITE_RECORDS].totalDelta));

        // Prepare a text report if needed
        if (buff == null)
            return;

        buff.append("Read: ").append(format.format(bytesRead.v)).append(" Bps");
        optRps(buff, format, subRecordsRead.v, dataRecordsRead.v, dataLagRead.v);
        buff.append("; Write: ").append(format.format(bytesWrite.v)).append(" Bps");
        optRps(buff, format, subRecordsWrite.v, dataRecordsWrite.v, dataLagWrite.v);
        if (rtt.v != 0)
            buff.append("; rtt ").append(format.format(rtt.v)).append(" us");
        if (scheme != null) {
            int topR = totals[READ_BYTES].findMaxRecord();
            int topW = totals[WRITE_BYTES].findMaxRecord();
            if (topR >= 0 || topW >= 0) {
                buff.append("; TOP bytes");
                String sep = totals[READ_BYTES].formatMax(scheme, buff, "read", topR, " ");
                totals[WRITE_BYTES].formatMax(scheme, buff, "write", topW, sep);
            }
        }
    }

    private static long rate(long delta, long elapsedTime) {
        return elapsedTime == 0 ? 0 : delta * 1000 / elapsedTime;
    }

    private long frac(long a, long b) {
        return b == 0 ? 0 : a / b;
    }

    private void optRps(StringBuilder sb, NumberFormat integerFormat, long subRps, long dataRps, long dataLag) {
        if (subRps == 0 && dataRps == 0 && dataLag == 0)
            return;
        sb.append(" (");
        if (subRps != 0) {
            sb.append("sub ").append(integerFormat.format(subRps)).append(" rps");
        }
        if (dataRps != 0 || dataLag != 0) {
            if (subRps != 0)
                sb.append(' ');
            sb.append("data ").append(integerFormat.format(dataRps)).append(" rps");
            if (dataLag != 0)
                sb.append(" lag ").append(integerFormat.format(dataLag)).append(" us");
        }
        sb.append(")");
    }
}
