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
package com.devexperts.qd.test;

import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.monitoring.ConnectorsMonitoringTask;
import com.devexperts.qd.qtp.AbstractMessageConnector;
import com.devexperts.qd.qtp.MessageConnectorState;
import com.devexperts.qd.stats.QDStats;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertTrue;

public class ConnectorMonitoringPerRecordTest {

    private static final DataScheme SCHEME = QDFactory.getDefaultScheme();
    private static final int QUOTE = SCHEME.findRecordByName("Quote").getId();
    private static final int TRADE = SCHEME.findRecordByName("Trade").getId();
    private static final int SUMMARY = SCHEME.findRecordByName("Summary").getId();

    @Test
    public void testRidStats() {
        QDStats stats = new QDStats();
        stats.initRoot(QDStats.SType.CONNECTION, SCHEME);

        ConnectorsMonitoringTask task = new ConnectorsMonitoringTask(stats);
        DummyMessageConnector connector = new DummyMessageConnector();
        connector.setStats(stats);

        task.addConnectors(Collections.singletonList(connector));

        stats.updateIOReadRecordBytes(QUOTE, 80_000);
        stats.updateIOReadRecordBytes(TRADE, 10_000);
        stats.updateIOReadRecordBytes(SUMMARY, 10_000);
        stats.updateIOReadBytes(100_000);

        String stats1 = task.report();
        assertTrue(stats1, stats1.contains("TOP bytes read Quote: 80%"));

        stats.updateIOReadRecordBytes(QUOTE, 20_000);
        stats.updateIOReadRecordBytes(TRADE, 30_000);
        stats.updateIOReadRecordBytes(SUMMARY, 50_000);
        stats.updateIOReadBytes(100_000);

        String stats2 = task.report();
        assertTrue(stats2, stats2.contains("TOP bytes read Summary: 50%"));
    }

    private static class DummyApplicationConnectionFactory extends ApplicationConnectionFactory {
        @Override
        public ApplicationConnection<?> createConnection(TransportConnection transportConnection) throws IOException {
            return null;
        }

        @Override
        public String toString() {
            return "Dummy";
        }
    }

    private static class DummyMessageConnector extends AbstractMessageConnector {
        protected DummyMessageConnector() {
            super(new DummyApplicationConnectionFactory());
        }

        @Override
        public String getAddress() {
            return "dummy";
        }

        @Override
        public void start() {
        }

        @Override
        public boolean isActive() {
            return true;
        }

        @Override
        public MessageConnectorState getState() {
            return MessageConnectorState.CONNECTED;
        }

        @Override
        public int getConnectionCount() {
            return 1;
        }

        @Override
        protected Joinable stopImpl() {
            return null;
        }
    }
}
