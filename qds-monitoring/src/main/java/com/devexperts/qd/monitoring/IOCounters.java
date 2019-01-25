/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.monitoring;

import java.text.NumberFormat;
import java.util.*;

import com.devexperts.management.Management;
import com.devexperts.mars.common.MARSMonitoredBean;
import com.devexperts.mars.common.MARSNode;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.util.JMXNameBuilder;
import com.devexperts.util.LogUtil;

import static com.devexperts.qd.monitoring.IOCounter.*;

/**
 * A collection of {@link IOCounter} instances of the same name that report the summary
 * statistics to mars nodes and provides a text report.
 */
class IOCounters {
    private final String name;
    private final Map<MessageConnector, IOCounter> counters = new LinkedHashMap<>();

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

    private Management.Registration registration;
    private MessageConnector registeredConnector;
    private MARSMonitoredBean<?> monitoredConnectorBean;

    private DataScheme scheme;
    private int nRecords;

    private String displayName;
    private String displayAddr;
    private int connectionsCount;
    private long connectionsClosedTotal;
    private long connectionsClosedDelta;

    private final Cur[] cur = new Cur[N_COUNTERS];

    // name == null for "root" IOCounters collection
    IOCounters(String name, MARSNode node) {
        this.name = name;
        this.node = node;
        address = name == null ? null : node.subNode("address", "Address");
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
        for (int i = 0; i < N_COUNTERS; i++)
            cur[i] = new Cur(VALUES[i]);
    }

    boolean isEmpty() {
        return counters.isEmpty();
    }

    IOCounter addConnector(MessageConnector connector, IOCounter prev) {
        // root collection does not register connector MBeans
        if (name != null && counters.isEmpty())
            registerConnector(connector);
        IOCounter counter = new IOCounter(name, connector, prev);
        counter.addDeltaToCur(cur); // clear initial
        counters.put(connector, counter);
        return counter;
    }

    public void removeConnector(MessageConnector connector) {
        counters.remove(connector);
        if (connector == registeredConnector) {
            unregisterConnector();
            if (!counters.isEmpty())
                registerConnector(counters.keySet().iterator().next());
        }
    }

    private void registerConnector(MessageConnector connector) {
        registration = Management.registerMBean(connector, null,
            "com.devexperts.qd.qtp:type=Connector,name=" + JMXNameBuilder.quoteKeyPropertyValue(name));
        registeredConnector = connector;
        monitoredConnectorBean = MARSMonitoredBean.forInstance(node, connector);
    }

    private void unregisterConnector() {
        if (registration != null) {
            registration.unregister();
            registration = null;
            registeredConnector = null;
            monitoredConnectorBean.close();
            monitoredConnectorBean = null;
        }
    }

    String getDisplayName() {
        return displayName;
    }

    String getDisplayAddr() {
        return displayAddr;
    }

    int getConnectionsCount() {
        return connectionsCount;
    }

    void update(Layout layout) {
        updateScheme();
        int n = counters.size();
        displayName = n > 1 ? name + "*" + n : name;
        displayAddr = "";
        int sameAddrCount = 0;
        int otherAddrCount = 0;
        long prevConnectionsClosedTotal = connectionsClosedTotal;
        connectionsCount = 0;
        connectionsClosedTotal = 0;
        for (Cur c : cur)
            c.clear(nRecords);
        for (IOCounter io : counters.values()){
            MessageConnector connector = io.getConnector();
            // update addr
            String addr = LogUtil.hideCredentials(connector.getAddress());
            if (displayAddr.isEmpty()) {
                displayAddr = addr;
                sameAddrCount = 1;
            } else if (addr.equals(displayAddr))
                sameAddrCount++;
            else
                otherAddrCount++;
            // update connection count
            connectionsCount += connector.getConnectionCount();
            connectionsClosedTotal += connector.getClosedConnectionCount();
            // collect all counters in cur
            io.addDeltaToCur(cur);
        }
        if (sameAddrCount > 1 && otherAddrCount > 0)
            displayAddr += "*" + sameAddrCount;
        if (otherAddrCount > 0) {
            displayAddr += ",<other>";
            if (otherAddrCount > 1)
                displayAddr += "*" + otherAddrCount;
        }
        connectionsClosedDelta = connectionsClosedTotal - prevConnectionsClosedTotal;
        if (layout != null) {
            layout.maxNameLen = Math.max(layout.maxNameLen, displayName.length());
            layout.maxAddrLen = Math.max(layout.maxAddrLen, displayAddr.length());
        }
    }

    void report(NumberFormat integerFormat, long elapsedTime, StringBuilder sb) {
        // report address and connection count (root collection does not report address)
        if (name != null)
            address.setValue(displayAddr);
        connections.setIntValue(connectionsCount);
        connectionsClosed.setValue(String.valueOf(connectionsClosedDelta));
        // report monitored props for a registered connector
        if (monitoredConnectorBean != null)
            monitoredConnectorBean.run();
        // compute values and post them to mars
        bytesRead.set(rate(cur[READ_BYTES].totalDelta, elapsedTime));
        bytesWrite.set(rate(cur[WRITE_BYTES].totalDelta, elapsedTime));
        subRecordsRead.set(rate(cur[SUB_READ_RECORDS].totalDelta, elapsedTime));
        subRecordsWrite.set(rate(cur[SUB_WRITE_RECORDS].totalDelta, elapsedTime));
        dataRecordsRead.set(rate(cur[DATA_READ_RECORDS].totalDelta, elapsedTime));
        dataRecordsWrite.set(rate(cur[DATA_WRITE_RECORDS].totalDelta, elapsedTime));
        dataLagRead.set(frac(cur[DATA_READ_LAGS].totalDelta, cur[DATA_READ_RECORDS].totalDelta));
        dataLagWrite.set(frac(cur[DATA_WRITE_LAGS].totalDelta, cur[DATA_WRITE_RECORDS].totalDelta));
        rtt.set(frac(cur[READ_RTTS].totalDelta + cur[WRITE_RTTS].totalDelta,
            cur[DATA_READ_RECORDS].totalDelta + cur[DATA_WRITE_RECORDS].totalDelta +
            cur[SUB_READ_RECORDS].totalDelta + cur[SUB_WRITE_RECORDS].totalDelta));
        // prepare text report if needed
        if (sb == null)
            return; // not needed
        sb.append("Read: ").append(integerFormat.format(bytesRead.v)).append(" Bps");
        optRps(sb, integerFormat, subRecordsRead.v, dataRecordsRead.v, dataLagRead.v);
        sb.append("; Write: ").append(integerFormat.format(bytesWrite.v)).append(" Bps");
        optRps(sb, integerFormat, subRecordsWrite.v, dataRecordsWrite.v, dataLagWrite.v);
        if (rtt.v != 0)
            sb.append("; rtt ").append(integerFormat.format(rtt.v)).append(" us");
        if (scheme != null) {
            int topR = cur[READ_BYTES].findMax();
            int topW = cur[WRITE_BYTES].findMax();
            if (topR >= 0 || topW >= 0) {
                sb.append("; TOP bytes");
                String sep = cur[READ_BYTES].fmtMax(scheme, sb, "read", topR, " ");
                cur[WRITE_BYTES].fmtMax(scheme, sb, "write", topW, sep);
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
        if (subRps != 0)
            sb.append("sub ").append(integerFormat.format(subRps)).append(" rps");
        if (dataRps != 0 || dataLag != 0) {
            if (subRps != 0)
                sb.append(' ');
            sb.append("data ").append(integerFormat.format(dataRps)).append(" rps");
            if (dataLag != 0)
                sb.append(" lag ").append(integerFormat.format(dataLag)).append(" us");
        }
        sb.append(")");
    }

    private void updateScheme() {
        scheme = null;
        for (IOCounter io : counters.values()){
            MessageConnector connector = io.getConnector();
            QDStats stats = connector.getStats();
            if (stats != null) {
                DataScheme otherScheme = stats.getScheme();
                if (otherScheme != null) {
                    if (scheme != null) {
                        if (scheme != otherScheme) {
                            // schemes diverge -- don't use scheme
                            scheme = null;
                            break;
                        }
                    } else
                        scheme = otherScheme;
                }
            }
        }
        nRecords = scheme == null ? 0 : scheme.getRecordCount();
    }

    void updateNames(List<IOCounter> updatedNames) {
        for (IOCounter counter : counters.values()) {
            if (counter.nameChanged())
                updatedNames.add(counter);
        }
    }
}
