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
package com.devexperts.qd.test;

import java.io.*;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.regex.Pattern;

import com.devexperts.logging.Logging;
import com.devexperts.qd.*;
import com.devexperts.qd.ng.RecordBuffer;
import junit.framework.TestCase;

public class LockTimeoutTest extends TestCase {
    private static final DataScheme SCHEME = new TestDataScheme(20091005);
    private static final int SYM_A = SCHEME.getCodec().encode("A");
    private static final DataRecord RECORD = SCHEME.getRecord(0);

    private static final String LOG_FILE = "LockTimeoutTest.log";
    private static final Pattern LOG_PATTERN =
        Pattern.compile(".*Ticker local lock is taking too long to acquire for setSub operation. Last operation was retData.*");

    public void testLockTooLongWarning() throws IOException {
        Logging.configureLogFile(LOG_FILE);
        QDTicker ticker = QDFactory.getDefaultFactory().createTicker(SCHEME);
        Tweaks.setTickerLockWaitLogInterval(SCHEME, "0.2s");
        final QDAgent agent = ticker.agentBuilder().build();
        QDDistributor dist = ticker.distributorBuilder().build();

        final SubscriptionBuffer sub = new SubscriptionBuffer();
        sub.visitRecord(RECORD, SYM_A, null);
        agent.setSubscription(sub.examiningIterator());

        final CyclicBarrier barrier = new CyclicBarrier(2); // sync two threads
        agent.setDataListener(new DataListener() {
            public void dataAvailable(DataProvider provider) {
                provider.retrieveData(new DataVisitor() {
                    public boolean hasCapacity() {
                        return true;
                    }

                    public void visitRecord(DataRecord record, int cipher, String symbol) {
                        try {
                            // wait for sub changing thread
                            try {
                                barrier.await();
                            } catch (BrokenBarrierException e) {
                                fail(e.toString());
                            }
                            // block for 0.4 sec
                            Thread.sleep(400);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    public void visitIntField(DataIntField field, int value) {
                    }

                    public void visitObjField(DataObjField field, Object value) {
                    }
                });
            }
        });

        // try to change subscription (waits for lock) in the different tread (only after data)
        new Thread(new Runnable() {
            public void run() {
                // wait on barrier
                try {
                    barrier.await();
                } catch (InterruptedException e) {
                    fail(e.toString());
                } catch (BrokenBarrierException e) {
                    Thread.currentThread().interrupt();
                }
                // now try!!!
                agent.setSubscription(sub.examiningIterator());
            }
        }).start();

        // process data (will block local lock in retrieve data)
        RecordBuffer buf = new RecordBuffer();
        buf.add(RECORD, SYM_A, null);
        dist.processData(buf);

        Tweaks.setTickerDefaults(SCHEME);
        Logging.configureLogFile(System.getProperty("log.file"));

        checkLogFile();
        assertTrue(new File(LOG_FILE).delete());
    }

    private void checkLogFile() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(LOG_FILE));
        try {
            String line;
            while ((line = br.readLine()) != null)
                if (LOG_PATTERN.matcher(line).matches())
                    return;
            fail("Required log pattern is not found in " + LOG_FILE);
        } finally {
            br.close();
        }
    }
}
