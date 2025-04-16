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
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.QDFactory;
import com.devexperts.qd.stats.QDStats;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

public class AgentBufferRebaseTest extends AbstractCollectorTest {

    private static final int MAGIC = 12345678;

    @Test
    public void testStream() {
        setUp(new TestStream());
        check();
    }

    @Test
    public void testHistory() {
        setUp(new TestHistory());
        check();
    }

    private void check() {
        long seed = System.currentTimeMillis();
        System.out.println("Seed: " + seed);
        Random r = new Random(seed);

        process(agent::addSubscription, SYMBOL);
        int processed = 0;
        int retrieved = 0;

        // distribute more and more records starting from 1
        for (int dc = 1; dc <= 100; dc++) {
            for (int i = 0; i < dc; i++) {
                int time = ++processed;
                process(distributor::process, SYMBOL, time ^ MAGIC, 0, time);
            }

            // process a random no of remaining recs
            final int limit = retrieved + r.nextInt(processed - retrieved) + 1;
            for (int i = retrieved; i < limit; i++) {
                int time = ++retrieved;
                assertRetrieve(SYMBOL, time ^ MAGIC, 0, time);
            }
            assertEquals("retrieved", limit, retrieved);
        }
    }

    public static class TestAgentBuffer extends AgentBuffer {
        public TestAgentBuffer(Agent agent) {
            super(agent);
        }

        @Override
        int getRebaseThreshold() {
            return 100;
        }
    }

    private static class TestStream extends Stream {
        TestStream() {
            super(QDFactory.getDefaultFactory().streamBuilder()
                .withScheme(SCHEME)
                .withStats(QDStats.VOID));
        }

        @Override
        AgentBuffer createAgentBuffer(Agent agent) {
            return new TestAgentBuffer(agent);
        }
    }

    private static class TestHistory extends History {
        TestHistory() {
            super(QDFactory.getDefaultFactory().historyBuilder()
                .withScheme(SCHEME)
                .withStats(QDStats.VOID));
        }

        @Override
        AgentBuffer createAgentBuffer(Agent agent) {
            return new TestAgentBuffer(agent);
        }
    }
}
