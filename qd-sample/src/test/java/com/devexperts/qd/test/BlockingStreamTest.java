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

import com.devexperts.qd.DataListener;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.SubscriptionBuffer;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class BlockingStreamTest {
    private static final int SEED = 20130718;

    private static final int N = 4;
    private static final int REP = 3;
    private static final int THREADS = N * REP;

    private static final int MAX_BUF = 100;
    private static final int PROCESS_PER_ONE = 20000;
    private static final int PROCESS_TOTAL = PROCESS_PER_ONE * N;

    private final DataScheme scheme = new TestDataScheme(SEED);
    private final QDStream stream = QDFactory.getDefaultFactory().createStream(scheme);

    @Test
    public void testBlockingStream() throws InterruptedException {
        // Prepare processing threads
        Processor[] processors = new Processor[THREADS];
        ExceptionHandler eh = new ExceptionHandler(Thread.currentThread());
        SubscriptionBuffer sub = new SubscriptionBuffer();
        new TestSubscriptionProvider(scheme, SEED, N, true).retrieveSubscription(sub);
        for (int i = 0; i < THREADS; i++) {
            int j = i % N;
            processors[i] = new Processor(i, sub.getRecord(j), sub.getCipher(j), sub.getSymbol(j));
            processors[i].setUncaughtExceptionHandler(eh);
        }
        // Start processing threads
        for (int i = 0; i < THREADS; i++)
            processors[i].start();
        // Distribute data
        QDDistributor distributor = stream.distributorBuilder().build();
        Random rnd = new Random(PROCESS_TOTAL);
        int distributed = 0;
        while (distributed < PROCESS_TOTAL && !Thread.interrupted()) {
            int batch = rnd.nextInt(PROCESS_TOTAL - distributed) + 1;
            RecordBuffer buf = RecordBuffer.getInstance();
            for (int i = 0; i < batch; i++) {
                int j = distributed++ % N;
                buf.add(sub.getRecord(j), sub.getCipher(j), sub.getSymbol(j));
            }
            System.out.println("Distributing " + buf.size());
            distributor.processData(buf);
            buf.release();
        }
        // Wait for all processing treads to finish
        for (int i = 0; i < THREADS; i++)
            processors[i].join();
        // Complain if any of them had failed
        if (eh.e != null)
            fail(eh.e.toString());
    }

    private static class ExceptionHandler implements Thread.UncaughtExceptionHandler {
        private final Thread main;
        Throwable e;

        private ExceptionHandler(Thread main) {
            this.main = main;
        }

        public void uncaughtException(Thread t, Throwable e) {
            e.printStackTrace();
            this.e = e;
            main.interrupt();
        }
    }

    private class Processor extends Thread implements DataListener {
        private final int index;
        private final DataRecord record;
        private final int cipher;
        private final String symbol;
        private final QDAgent agent;
        private int processed;
        private boolean available;

        private Processor(int index, DataRecord record, int cipher, String symbol) {
            this.index = index;
            this.record = record;
            this.cipher = cipher;
            this.symbol = symbol;
            agent = stream.agentBuilder().build();
            agent.setMaxBufferSize(MAX_BUF);
            agent.setBufferOverflowStrategy(QDAgent.BufferOverflowStrategy.BLOCK);

            SubscriptionBuffer sub = new SubscriptionBuffer();
            sub.visitRecord(record, cipher, symbol);
            agent.setSubscription(sub);
            agent.setDataListener(this);
        }

        @Override
        public void run() {
            try {
                while (processed < PROCESS_PER_ONE) {
                    awaitAvailable();
                    RecordBuffer buf = RecordBuffer.getInstance();
                    if (agent.retrieveData(buf))
                        dataAvailable(agent);
                    //System.out.println("#" + index + " retrieved " + buf.size());
                    RecordCursor cur;
                    while ((cur = buf.next()) != null) {
                        assertEquals(record, cur.getRecord());
                        assertEquals(cipher, cur.getCipher());
                        assertEquals(symbol, cur.getSymbol());
                        processed++;
                    }
                    buf.release();
                }
            } catch (InterruptedException e) {
                // done
            }
        }

        private synchronized void awaitAvailable() throws InterruptedException {
            while (!available)
                wait();
            available = false;
        }

        public synchronized void dataAvailable(DataProvider provider) {
            available = true;
            notifyAll();
        }
    }
}
