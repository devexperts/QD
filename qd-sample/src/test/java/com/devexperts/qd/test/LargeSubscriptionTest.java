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

import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import com.devexperts.qd.*;
import com.devexperts.qd.kit.*;
import com.devexperts.qd.ng.RecordBuffer;
import junit.framework.TestCase;

/**
 * Large subscription performance unit test.
 */
public class LargeSubscriptionTest extends TestCase {
    public LargeSubscriptionTest(String s) {
        super(s);
    }

    private static final SymbolCodec codec = PentaCodec.INSTANCE;
    private static final DataRecord record = new DefaultRecord(0, "Haba", false, null, null);
    private static final DataScheme scheme = new DefaultScheme(codec, record);

    private static RecordBuffer sub(int size, boolean encodeable) {
        Random rnd = new Random();
        BitSet sub = new BitSet(1 << 24);
        char[] c = new char[6];
        RecordBuffer sb = new RecordBuffer();
        while (sb.size() < size) {
            int r = rnd.nextInt() & 0xFFFFFF;
            if (sub.get(r))
                continue;
            sub.set(r);
            for (int i = 0; i < c.length; i++)
                c[c.length - 1 - i] = (char) ((encodeable ? 'A' : 'a') + ((r >> (i << 2)) & 0x0F));
            int cipher = codec.encode(c, 0, c.length);
            String symbol = cipher == 0 ? new String(c) : null;
            sb.add(record, cipher, symbol);
        }
        return sb;
    }

    public void testLargeSubscription() {
        int size = 100_000;
        long threshold = 1_000;

        // measure until get a good result: warm-up & test environment instability may affect a couple of iterations
        long minTime = 0;
        for (int i = 0; i < 10; i++) {
            long time = measureSubscribeAndClose(size);
            if (i == 0 || minTime > time)
                minTime = time;
            if (minTime < threshold)
                break;
        }
        assertTrue("subscribe & close exceeds " + threshold + " ms: " + minTime, minTime < threshold);
    }

    private long measureSubscribeAndClose(int subSize) {
        QDTicker ticker = QDFactory.getDefaultFactory().tickerBuilder().withScheme(scheme).build();
        QDAgent agent = ticker.agentBuilder().build();
        RecordBuffer sub = sub(subSize, true);
        long time1 = System.nanoTime();
        agent.setSubscription(sub);
        long time2 = System.nanoTime();
        agent.close();
        long time3 = System.nanoTime();
        long subscribeMillis = TimeUnit.NANOSECONDS.toMillis(time2 - time1);
        long closeMillis = TimeUnit.NANOSECONDS.toMillis(time3 - time2);
        System.out.println("Size " + subSize + ", subscribe " + subscribeMillis + " ms, close " + closeMillis + " ms");
        return closeMillis + subscribeMillis;
    }
}
