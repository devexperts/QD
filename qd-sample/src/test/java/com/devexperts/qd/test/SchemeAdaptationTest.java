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
package com.devexperts.qd.test;

import com.devexperts.io.IOUtil;
import com.devexperts.logging.Logging;
import com.devexperts.mars.common.MARSEndpoint;
import com.devexperts.qd.DataBuffer;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataListener;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SubscriptionBuffer;
import com.devexperts.qd.SubscriptionListener;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.SubscriptionVisitor;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.kit.ByteArrayField;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DecimalField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.PlainIntField;
import com.devexperts.qd.kit.PlainObjField;
import com.devexperts.qd.kit.StringField;
import com.devexperts.qd.monitoring.ConnectorsMonitoringTask;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.socket.ClientSocketConnector;
import com.devexperts.qd.qtp.socket.ServerSocketConnector;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.Decimal;
import com.devexperts.test.ThreadCleanCheck;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.Point;
import java.io.IOException;
import java.util.Collections;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SchemeAdaptationTest {
    private static final int SYMBOL_COUNT = 8;
    private static final int GEN_RECORDS = 1000;
    private static final int GEN_FIELDS = 6 * GEN_RECORDS;
    private static final int GEN_BLOCK_SIZE = 100;
    private static final int PORT = 10789;

    private static final String I_CHECK_FLD = "MyRecord.FldC";
    private static final String S_CHECK_FLD = "MyRecord.FldF";

    private static final String D2I_CHECK_FLD = "MyRecord.FldK";
    private static final String I2D_CHECK_FLD = "MyRecord.FldL";

    private static final String O2B_CHECK_FLD = "MyRecord.FldM";
    private static final String B2O_CHECK_FLD = "MyRecord.FldN";

    private static final DataRecord SRC_REC = new DefaultRecord(15, "MyRecord", false, new DataIntField[] {
        new PlainIntField(0, "MyRecord.FldA"),
        new DecimalField(1, D2I_CHECK_FLD),
        new CompactIntField(2, "MyRecord.FldB"),
        new DecimalField(3, I_CHECK_FLD),
        new CompactIntField(4, "MyRecord.FldD"),
        new CompactIntField(5, I2D_CHECK_FLD),
    }, new DataObjField[] {
        new PlainObjField(0, O2B_CHECK_FLD),
        new ByteArrayField(1, B2O_CHECK_FLD),
        new StringField(2, "MyRecord.FldE"),
        new StringField(3, S_CHECK_FLD)
    });

    private static final DataRecord DST_REC = new DefaultRecord(23, "MyRecord", false, new DataIntField[] {
        new DecimalField(0, I2D_CHECK_FLD),
        new CompactIntField(1, "MyRecord.FldB"),
        new DecimalField(2, I_CHECK_FLD),
        new CompactIntField(3, "MyRecord.FldH"),
        new CompactIntField(4, "MyRecord.FldD"),
        new PlainIntField(5, D2I_CHECK_FLD),
        new CompactIntField(6, "MyRecord.FldG")
    }, new DataObjField[] {
        new StringField(0, S_CHECK_FLD),
        new StringField(1, "MyRecord.FldI"),
        new ByteArrayField(2, O2B_CHECK_FLD),
        new StringField(3, "MyRecord.FldJ"),
        new PlainObjField(4, B2O_CHECK_FLD)
    });

    private static final DataScheme SRC_SCHEME = new TestDataScheme(100, 7543, TestDataScheme.Type.SIMPLE, SRC_REC);
    private static final DataScheme DST_SCHEME = new TestDataScheme(100, 3255, TestDataScheme.Type.SIMPLE, DST_REC);
    private static final SymbolCodec CODEC = SRC_SCHEME.getCodec();

    private static final Logging log = Logging.getLogging(SchemeAdaptationTest.class);

    private final String[] symbols = { "A", "GE", "IBM", "MSFT", "OGGZY", ".IBMGQ", "SIXCHR", "$NIKKEI" };
    private final int[] ciphers = new int[SYMBOL_COUNT];

    {
        for (int i = 0; i < SYMBOL_COUNT; i++) {
            ciphers[i] = CODEC.encode(symbols[i]);
            if (ciphers[i] != 0)
                symbols[i] = null;
        }
    }

    private int genHash = 0;

    private interface Task {
        void start();
        void join() throws InterruptedException;
        void stop();
    }

    private class Server implements Task, Runnable, SubscriptionListener, SubscriptionVisitor {
        QDCollector collector = QDFactory.getDefaultFactory().createStream(SRC_SCHEME);
        QDDistributor distributor = collector.distributorBuilder().build();
        MessageConnector connector = new ServerSocketConnector(new AgentAdapter.Factory(collector), PORT);
        Thread thread = new Thread(this, "Server");
        Random r = new Random(23424);
        int subReceivedCount;
        boolean[] subReceived = new boolean[SYMBOL_COUNT];

        Server() {}

        @Override
        public void start() {
            connector.start();
            thread.start();
            distributor.getAddedSubscriptionProvider().setSubscriptionListener(this);
        }

        @Override
        public void subscriptionAvailable(SubscriptionProvider provider) {
            provider.retrieveSubscription(this);
        }

        @Override
        public boolean hasCapacity() {
            return true;
        }

        @Override
        public void visitRecord(DataRecord record, int cipher, String symbol, long time) {
            if (record == SRC_REC)
                synchronized (this) {
                    for (int i = 0; i < SYMBOL_COUNT; i++)
                        if (cipher == ciphers[i] && symbol == symbols[i])
                            if (!subReceived[i]) {
                                subReceived[i] = true;
                                subReceivedCount++;
                                notifyAll();
                            }
                }
        }

        @Override
        public void run() {
            try {
                synchronized (this) {
                    while (subReceivedCount < SYMBOL_COUNT)
                        wait();
                }
                DataBuffer buf = new DataBuffer();
                int generatedRecordsCount = 0;
                int lastPercent = 0;
                while (generatedRecordsCount < GEN_RECORDS) {
                    int cnt = r.nextInt(Math.min(GEN_RECORDS - generatedRecordsCount, GEN_BLOCK_SIZE)) + 1;
                    for (int i = 0; i < cnt; i++) {
                        int j = r.nextInt(SYMBOL_COUNT);
                        buf.visitRecord(SRC_REC, ciphers[j], symbols[j]);
                        genHash *= 13;
                        for (int k = 0; k < SRC_REC.getIntFieldCount(); k++) {
                            DataIntField fld = SRC_REC.getIntField(k);
                            int value = r.nextInt(100000);
                            if (fld.getName().equals(I_CHECK_FLD))
                                genHash += value;
                            if (fld.getName().equals(D2I_CHECK_FLD)) {
                                genHash += 2 * value;
                                value = Decimal.compose(value);
                            }
                            if (fld.getName().equals(I2D_CHECK_FLD)) {
                                genHash += 3 * value;
                            }
                            buf.visitIntField(fld, value);
                        }
                        for (int k = 0; k < SRC_REC.getObjFieldCount(); k++) {
                            DataObjField fld = SRC_REC.getObjField(k);
                            Object value = new Point(r.nextInt(1000), r.nextInt(1000));
                            if (fld.getName().equals(S_CHECK_FLD)) {
                                value = value.toString();
                                genHash += 5 * value.hashCode();
                            }
                            if (fld.getName().equals(O2B_CHECK_FLD)) {
                                genHash += 7 * value.hashCode();
                            }
                            if (fld.getName().equals(B2O_CHECK_FLD)) {
                                genHash += 11 * value.hashCode();
                                value = IOUtil.objectToBytes(value);
                            }
                            buf.visitObjField(fld, value);
                        }
                    }
                    distributor.processData(buf);
                    Thread.sleep(10);
                    generatedRecordsCount += cnt;
                    int newPercent = generatedRecordsCount * 10 / GEN_RECORDS;
                    if (newPercent != lastPercent) {
                        System.out.println(newPercent * 10 + "%");
                        lastPercent = newPercent;
                    }
                }
            } catch (InterruptedException e) {
                // quit
            } catch (IOException e) {
                fail(e.toString());
            }
        }

        @Override
        public void join() throws InterruptedException {
            thread.join();
        }

        @Override
        public void stop() {
            connector.stop();
        }
    }

    private class Client implements Task, Runnable, DataListener, DataVisitor {
        QDStats rootStats = new QDStats(QDStats.SType.ANY, DST_SCHEME);
        QDCollector collector = QDFactory.getDefaultFactory().createStream(DST_SCHEME, rootStats);
        QDAgent agent = collector.agentBuilder().build();
        MessageConnector connector = new ClientSocketConnector(
            new DistributorAdapter.Factory(collector), "localhost", PORT);
        Thread thread = new Thread(this, "Client");
        MARSEndpoint marsEndpoint;
        ConnectorsMonitoringTask monitoring;
        boolean broken;
        int receivedRecs;
        int receivedFlds;
        int receivedHash;

        Client() {}

        @Override
        public void start() {
            connector.setStats(rootStats.create(QDStats.SType.CLIENT_SOCKET_CONNECTOR));
            marsEndpoint = MARSEndpoint.newBuilder().acquire();
            monitoring = new ConnectorsMonitoringTask(null, log, rootStats, marsEndpoint.getRoot(),
                Collections.singletonList(connector));
            connector.start();
            thread.start();
        }

        @Override
        public void dataAvailable(DataProvider provider) {
            provider.retrieveData(this);
        }

        @Override
        public boolean hasCapacity() {
            return true;
        }

        @Override
        public void visitRecord(DataRecord record, int cipher, String symbol) {
            if (record != DST_REC)
                synchronized (this) {
                    broken = true;
                    notifyAll();
                }
            else
                synchronized (this) {
                    receivedRecs++;
                    receivedHash *= 13;
                    notifyAll();
                }
        }

        @Override
        public void visitIntField(DataIntField field, int value) {
            if (field.getName().equals(I_CHECK_FLD))
                synchronized (this) {
                    receivedFlds++;
                    receivedHash += value;
                    notifyAll();
                }
            if (field.getName().equals(D2I_CHECK_FLD))
                synchronized (this) {
                    receivedFlds++;
                    receivedHash += 2 * value;
                    notifyAll();
                }
            if (field.getName().equals(I2D_CHECK_FLD))
                synchronized (this) {
                    receivedFlds++;
                    receivedHash += 3 * (int) Decimal.toDouble(value);
                    notifyAll();
                }
        }

        @Override
        public void visitObjField(DataObjField field, Object value) {
            if (field.getName().equals(S_CHECK_FLD))
                synchronized (this) {
                    receivedFlds++;
                    receivedHash += 5 * value.hashCode();
                    notifyAll();
                }
            if (field.getName().equals(O2B_CHECK_FLD))
                synchronized (this) {
                    receivedFlds++;
                    try {
                        receivedHash += 7 * IOUtil.bytesToObject((byte[]) value).hashCode();
                    } catch (IOException e) {
                        fail(e.toString());
                    }
                    notifyAll();
                }
            if (field.getName().equals(B2O_CHECK_FLD))
                synchronized (this) {
                    receivedFlds++;
                    receivedHash += 11 * value.hashCode();
                    notifyAll();
                }
        }

        @Override
        public void run() {
            try {
                SubscriptionBuffer buf = new SubscriptionBuffer();
                for (int i = 0; i < SYMBOL_COUNT; i++)
                    buf.visitRecord(DST_REC, ciphers[i], symbols[i]);
                agent.setSubscription(buf);
                agent.setDataListener(this);
                synchronized (this) {
                    while (!broken && receivedFlds < GEN_FIELDS)
                        wait();
                }
            } catch (InterruptedException e) {
                // quit
            }
        }

        @Override
        public void join() throws InterruptedException {
            thread.join();
            assertFalse(broken);
            assertEquals(receivedRecs, GEN_RECORDS);
            assertEquals(receivedFlds, GEN_FIELDS);
            assertEquals(receivedHash, genHash);

            String rep = monitoring.report();
            assertTrue(rep, rep.matches(
                "Subscription: 8; Storage: 0; Buffer: 0; Dropped: 0; Read: ([^;]+); Write: ([^;]+); (.*); CPU: \\d+\\.\\d\\d%\n" +
                "    ClientSocket-Distributor localhost:\\d+ \\[1\\] Read: \\1; Write: \\2; \\3"));
        }

        @Override
        public void stop() {
            connector.stop();
            monitoring.close();
            marsEndpoint.release();
        }
    }

    @Test
    public void testSchemeAdaptation() throws InterruptedException {
        Task srv = new Server();
        Task cli = new Client();
        srv.start();
        cli.start();
        srv.join();
        cli.join();
        srv.stop();
        cli.stop();
    }

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() throws Exception {
        ThreadCleanCheck.after();
    }
}
