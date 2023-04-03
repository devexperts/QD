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
package com.devexperts.qd.test;

import com.devexperts.qd.DataBuffer;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataListener;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDErrorHandler;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.MarshalledObjField;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.util.SymbolObjectMap;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Tests correct processing of subscription lists in the presence of multiple agents which
 * are subscribed on varying number of symbols on 2 different records.
 */
public class MultiAgentTest extends QDTestBase {
    private static final int RECORDS = 2;
    private static final int KEYS = 100; // total number of keys
    private static final int SUB_KEYS = 80; // total number of keys some agent subscribes to
    private static final int AGENTS = 200;
    private static final double VOID_AGENTS_FRACTION = 0.2;
    private static final int BUFFER = 2000;
    private static final int REPEAT = 10;

    private static final DataRecord INT_RECORD = new DefaultRecord(0, "IntRecord", true,
        new DataIntField[] {
            new CompactIntField(0, "IntRecord.dummy"),
            new CompactIntField(1, "IntRecord.sequence"),
            new CompactIntField(2, "IntRecord.value")
        }, new DataObjField[0]);

    private static final DataRecord OBJ_RECORD = new DefaultRecord(1, "ObjRecord", true,
        new DataIntField[] {
            new CompactIntField(0, "ObjRecord.dummy"),
            new CompactIntField(1, "ObjRecord.sequence")
        }, new DataObjField[] {
            new MarshalledObjField(0, "ObjRecord.value")
        });

    private static final PentaCodec CODEC = new PentaCodec();

    private static final DataScheme SCHEME = new DefaultScheme(CODEC, new DataRecord[] {
        INT_RECORD, OBJ_RECORD
    });

    public MultiAgentTest(String matrixType) {
        super(matrixType);
    }

    @Test
    public void testTestTickerPlain() {
        assumeTrue(isMatrix() || isHash());
        runTest(qdf.createTicker(SCHEME), false, false);
    }

    @Test
    public void testTestTickerWithVoidListener() {
        assumeTrue(isMatrix() || isHash());
        runTest(qdf.createTicker(SCHEME), false, true);
    }

    @Test
    public void testTickerInterleaves() {
        assumeTrue(isMatrix());
        for (int interleave = 1; interleave <= 4; interleave++) {
            QDTicker ticker = qdf.createTicker(SCHEME);
            Tweaks.setTickerInterleave(SCHEME, interleave);
            runTest(ticker, false, false);
        }
        Tweaks.setTickerDefaults(SCHEME);
    }

    @Test
    public void testTickerStress() {
        assumeTrue(isMatrix());
        QDTicker ticker = qdf.createTicker(SCHEME);
        Tweaks.setTickerStress(SCHEME);
        runTest(ticker, false, false);
        Tweaks.setTickerDefaults(SCHEME);
    }

    @Test
    public void testTickerStoreEverything() {
        assumeTrue(isMatrix() || isStriped());
        QDTicker ticker = qdf.createTicker(SCHEME);
        ticker.setStoreEverything(true);
        runTest(ticker, false, false);
    }

    @Test
    public void testStream() {
        assumeTrue(isMatrix() || isStriped());
        runTest(qdf.createStream(SCHEME), false, false);
    }

    // Does not really work with striped collector -- '*' subscription gets multiplied on N by StripedStream
    @Test
    public void testStreamWildcard() {
        assumeTrue(isMatrix());
        QDStream stream = qdf.createStream(SCHEME);
        stream.setEnableWildcards(true);
        runTest(stream, true, false);
    }

    @Test
    public void testHistory() {
        assumeTrue(isMatrix() || isStriped());
        runTest(qdf.createHistory(SCHEME), false, false);
    }

    private void runTest(QDCollector collector, boolean useWildcard, boolean withVoidListeners) {
        collector.setErrorHandler(new QDErrorHandler() {
            public void handleDataError(DataProvider provider, Throwable t) {
                throw new RuntimeException(t);
            }

            public void handleSubscriptionError(SubscriptionProvider provider, Throwable t) {
                throw new RuntimeException(t);
            }
        });
        Random r = new Random(20081111);

        // Generate keys
        DataRecord[] record = new DataRecord[KEYS];
        String[] symbol = new String[KEYS];
        int[] cipher = new int[KEYS];
        Set<String>[] used = (Set<String>[]) new Set[RECORDS];
        final SymbolObjectMap<Integer>[] keys = (SymbolObjectMap<java.lang.Integer>[]) new SymbolObjectMap[RECORDS];
        for (int i = 0; i < RECORDS; i++) {
            used[i] = new HashSet<String>();
            keys[i] = SymbolObjectMap.createInstance();
        }
        for (int i = 0; i < KEYS; i++) {
            int j = r.nextInt(RECORDS);
            Set<String> u = used[j];
            String s;
            do {
                int len = r.nextInt(8) + 1;
                StringBuilder sb = new StringBuilder();
                for (int k = 0; k < len; k++) {
                    sb.append((char) ('A' + r.nextInt('Z' - 'A')));
                }
                s = sb.toString();
            } while (!u.add(s));
            if (useWildcard && i < RECORDS) {
                s = CODEC.decode(CODEC.getWildcardCipher());
                j = i;
            }
            record[i] = SCHEME.getRecord(j);
            symbol[i] = s;
            cipher[i] = CODEC.encode(s);
            if (cipher[i] != 0)
                symbol[i] = null;
            keys[j].put(cipher[i], symbol[i], i);
        }

        // build sub map
        int[] asubc = new int[AGENTS];
        for (int i = 0; i < AGENTS; i++) {
            asubc[i] = SUB_KEYS * i / (AGENTS - 1);
        }
        shuffle(asubc, r, asubc.length);

        final boolean[][] sub = new boolean[AGENTS][KEYS];
        final boolean[][] wsub = new boolean[AGENTS][RECORDS];
        for (int i = 0; i < AGENTS; i++) {
            Arrays.fill(sub[i], 0, asubc[i], true);
            shuffle(sub[i], r, SUB_KEYS);
            if (useWildcard)
                for (int j = 0; j < RECORDS; j++) {
                    if (sub[i][j])
                        wsub[i][j] = true;
                }
        }

        // create agents & subscribe
        QDAgent[] agent = new QDAgent[AGENTS];
        int[] ksubc = new int[KEYS];
        final long[][] maxseen = new long[AGENTS][KEYS];
        RecordBuffer buf = new RecordBuffer();
        for (int i = 0; i < AGENTS; i++) {
            final int ai = i;
            agent[i] = collector.agentBuilder().build();
            agent[i].setDataListener(new MyDataListener(keys, sub, ai, wsub, maxseen, agent[i]));
            for (int j = 0; j < KEYS; j++) {
                if (sub[i][j]) {
                    ksubc[j]++;
                    buf.add(record[j], cipher[j], symbol[j]);
                }
            }
            agent[i].addSubscription(buf);
            if (withVoidListeners && r.nextDouble() < VOID_AGENTS_FRACTION) {
                // mirror this sub with a VOID agent
                buf.rewind();
                QDAgent voidAgent = collector.agentBuilder().build();
                voidAgent.setRecordListener(RecordListener.VOID);
                voidAgent.addSubscription(buf);
            }
            buf.clear();
        }

        // check "diversity" of subscription (make sure our random is good enough)
        if (!useWildcard) {
            int cipherSubc = 0;
            int cipherTotal = 0;
            int symbolSubc = 0;
            int symbolTotal = 0;
            for (int i = 0; i < KEYS; i++) {
                if (cipher[i] != 0) {
                    cipherTotal++;
                    if (ksubc[i] > 0)
                        cipherSubc++;
                } else {
                    symbolTotal++;
                    if (ksubc[i] > 0)
                        symbolSubc++;
                }
            }
            assertTrue("cipher " + cipherSubc + " of " + cipherTotal, cipherSubc > 0 && cipherSubc < cipherTotal);
            assertTrue("symbol " + symbolSubc + " of " + symbolTotal, symbolSubc > 0 && symbolSubc < symbolTotal);
        }

        // create distributor, check subs
        QDDistributor dist = collector.distributorBuilder().build();
        dist.getAddedSubscriptionProvider().retrieveSubscription(buf);
        RecordCursor cur;
        boolean[] ksubseen = new boolean[KEYS];
        while ((cur = buf.next()) != null) {
            int key = keys[cur.getRecord().getId()].get(cur.getCipher(), cur.getSymbol());
            assertTrue(ksubc[key] > 0);
            assertFalse(ksubseen[key]);
            ksubseen[key] = true;
        }
        buf.clear();
        for (int i = 0; i < KEYS; i++) {
            if (ksubc[i] > 0)
                assertTrue(ksubseen[i]);
        }

        // process data
        // flip between RecordBuffer and [legacy] DataBuffer to cover all code paths
        long[] lastdist = new long[KEYS];
        int time = 1;
        DataBuffer dbuf = new DataBuffer();
        for (int rep = 0; rep < REPEAT; rep++) {
            boolean legacy = rep % 2 == 1;
            for (int k = 0; k < BUFFER; k++) {
                int i = r.nextInt(KEYS);
                DataRecord rec = record[i];
                if (legacy) {
                    dbuf.visitRecord(rec, cipher[i], symbol[i]);
                    dbuf.visitIntField(rec.getIntField(0), 0);
                    dbuf.visitIntField(rec.getIntField(1), time);
                    for (int j = 2; j < rec.getIntFieldCount(); j++)
                        dbuf.visitIntField(rec.getIntField(j), intValue(time, j));
                    for (int j = 0; j < rec.getObjFieldCount(); j++)
                        dbuf.visitObjField(rec.getObjField(j), null);
                } else {
                    cur = buf.add(rec, cipher[i], symbol[i]);
                    cur.setTime(time);
                    for (int j = 2; j < rec.getIntFieldCount(); j++)
                        cur.setInt(j, intValue(time, j));
                }
                lastdist[i] = time;
                time++;
            }
            if (legacy) {
                dist.processData(dbuf);
                dbuf.clear();
            } else {
                dist.processData(buf);
                buf.clear();
            }
            for (int i = 0; i < AGENTS; i++) {
                for (int j = 0; j < KEYS; j++) {
                    int rid = record[j].getId();
                    if (sub[i][j] || wsub[i][rid])
                        assertEquals(lastdist[j], maxseen[i][j]);
                }
            }
        }
    }

    private static int intValue(long time, int j) {
        return (int) (time * time + j);
    }

    private void shuffle(int[] a, Random r, int len) {
        for (int i = 0; i < len - 1; i++) {
            int j = i + r.nextInt(len - i);
            int t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    void shuffle(boolean[] a, Random r, int len) {
        for (int i = 0; i < len - 1; i++) {
            int j = i + r.nextInt(len - i);
            boolean t = a[i];
            a[i] = a[j];
            a[j] = t;
        }
    }

    private static class MyDataListener implements DataListener {
        private final SymbolObjectMap<Integer>[] keys;
        private final boolean[][] sub;
        private final int ai;
        private final boolean[][] wsub;
        private final long[][] maxseen;
        private final QDAgent qdAgent;

        public MyDataListener(SymbolObjectMap<Integer>[] keys, boolean[][] sub, int ai, boolean[][] wsub,
            long[][] maxseen, QDAgent qdAgent)
        {
            this.keys = keys;
            this.sub = sub;
            this.ai = ai;
            this.wsub = wsub;
            this.maxseen = maxseen;
            this.qdAgent = qdAgent;
        }

        public void dataAvailable(DataProvider provider) {
            assertSame(qdAgent, provider);
            RecordBuffer tmp = RecordBuffer.getInstance();
            provider.retrieveData(tmp);
            RecordCursor cur;
            while ((cur = tmp.next()) != null) {
                int rid = cur.getRecord().getId();
                int key = keys[rid].get(cur.getCipher(), cur.getSymbol());
                assertTrue(sub[ai][key] || wsub[ai][rid]);
                // no collector is allowed to produce dupes.
                assertTrue(maxseen[ai][key] != cur.getTime());
                maxseen[ai][key] = Math.max(maxseen[ai][key], cur.getTime());
                for (int j = 2; j < cur.getIntCount(); j++)
                    assertEquals(intValue(cur.getTime(), j), cur.getInt(j));
            }
            tmp.release();
        }
    }
}
