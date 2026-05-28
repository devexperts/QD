/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.impl.matrix.MatrixFactory;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.util.TimePeriod;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Concurrent stress test for the QDTicker collector with sticky subscription enabled.
 * Combines rotating background agents with multiple worker threads performing
 * subscription churn and agent close, aiming to exercise concurrent code paths
 * around sticky-add, chain repair and matrix rehash.
 *
 * Used for manual start-up only, 3 min long.
 */
@Ignore
public class TickerStickyStressTest {
    private static final int SYMBOLS_POOL = 1_000_000;
    private static final int SYMBOLS_INITIAL = 1_000;
    private static final int SYMBOLS_GROW_STEP = 2_000;
    private static final long SYMBOLS_GROW_INTERVAL_MS = 300;  // 300 ms
    private static final long STICKY_PERIOD_MS = 1_000;  // 1 s
    private static final long TEST_DURATION_MS = 180_000; // 3 min
    private static final int WORKER_THREADS = 7;
    private static final int ROTATING_BG_COUNT = 4;
    private static final long BG_ROTATION_INTERVAL_MS = STICKY_PERIOD_MS / 2; // 500 ms
    private static final long QUIET_WINDOW_MS = STICKY_PERIOD_MS;             // 1 s
    private static final long BURST_WINDOW_MS = 1_000;
    private static final long GLOBAL_QUIET_INTERVAL_MS = STICKY_PERIOD_MS * 4;  // every 4 s
    private static final long GLOBAL_QUIET_DURATION_MS = STICKY_PERIOD_MS * 2;  // pause 2 s

    @BeforeClass
    public static void setupSystemProperties() {
        System.setProperty("com.devexperts.qd.impl.matrix.Collector.SubscriptionBucket", "16");
        System.setProperty("com.devexperts.qd.impl.StickyScheduleMinDelay", "1");
        System.setProperty("com.devexperts.qd.impl.StickySubscriptionLogInterval", "1s");
    }

    @Test
    public void reproduceMatrix() throws InterruptedException {
        runStress(new MatrixFactory());
    }

    private void runStress(QDFactory factory) throws InterruptedException {
        Random rnd = new Random(System.currentTimeMillis());

        DataScheme scheme = new TestDataScheme(1, rnd.nextInt(), TestDataScheme.Type.SIMPLE);
        DataRecord record = scheme.getRecord(0);
        SymbolCodec codec = scheme.getCodec();
        int intFieldCount = record.getIntFieldCount();

        String[] symbols = new String[SYMBOLS_POOL];
        int[] ciphers = new int[SYMBOLS_POOL];
        for (int i = 0; i < SYMBOLS_POOL; i++) {
            symbols[i] = "SYM" + i;
            ciphers[i] = codec.encode(symbols[i]);
        }
        AtomicInteger symbolsActive = new AtomicInteger(SYMBOLS_INITIAL);

        QDTicker ticker = factory.tickerBuilder()
            .withScheme(scheme)
            .withStickySubscriptionPeriod(TimePeriod.valueOf(STICKY_PERIOD_MS))
            .build();
        QDDistributor distributor = ticker.distributorBuilder().build();

        AtomicReference<Throwable> failure = new AtomicReference<>();
        long deadline = getCurrentTimeInMillis() + TEST_DURATION_MS;

        AtomicBoolean globalQuiet = new AtomicBoolean(false);
        CountDownLatch done = new CountDownLatch(3 + WORKER_THREADS);
        ConcurrentLinkedDeque<QDAgent> pendingClose = new ConcurrentLinkedDeque<>();

        // Rotating background: holds 4 agents at any time, each subscribed to ~50% of
        // symbols. Periodically replaces one agent with a fresh one. This:
        //  - Keeps chain depth >= 1 for ~half of the symbols at any moment.
        //  - For ~half of the symbols, occasionally there's no subscriber → sticky fires.
        //  - When a background is rotated out, it goes through agent close — flood of
        //    sticky-add for entries where it was the last subscriber.
        QDAgent[] bgSlots = new QDAgent[ROTATING_BG_COUNT];
        for (int i = 0; i < ROTATING_BG_COUNT; i++) {
            bgSlots[i] = newBackgroundAgent(ticker, record, ciphers, symbols, symbolsActive.get(), rnd);
        }
        Thread bgRotator = new Thread(() -> {
            try {
                for (int i = 0; getCurrentTimeInMillis() < deadline && failure.get() == null; i = (i + 1) % bgSlots.length) {
                    sleep(BG_ROTATION_INTERVAL_MS);
                    awaitNotQuiet(globalQuiet, deadline, failure);
                    QDAgent newAgent = newBackgroundAgent(ticker, record, ciphers, symbols, symbolsActive.get(), rnd);
                    QDAgent oldAgent = bgSlots[i];
                    bgSlots[i] = newAgent;
                    oldAgent.close();
                }
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            } finally {
                done.countDown();
            }
        }, "bg-rotator");
        bgRotator.start();

        // Workers: bursts of agent create / setSubscription / close,
        // separated by quiet windows.
        for (int t = 0; t < WORKER_THREADS; t++) {
            final int seed = t + 7;
            Thread worker = new Thread(() -> {
                Random random = new Random(seed * 42);
                try {
                    while (getCurrentTimeInMillis() < deadline && failure.get() == null) {
                        awaitNotQuiet(globalQuiet, deadline, failure);
                        burst(random, ticker, record, ciphers, symbols, symbolsActive,
                            pendingClose, deadline, failure);
                        sleep(QUIET_WINDOW_MS);
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            }, "worker-" + t);
            worker.start();
        }

        // Global quiet driver, gives change for DxTimer work
        Thread quietDriver = new Thread(() -> {
            try {
                while (getCurrentTimeInMillis() < deadline && failure.get() == null) {
                    sleep(GLOBAL_QUIET_INTERVAL_MS);
                    globalQuiet.set(true);
                    sleep(GLOBAL_QUIET_DURATION_MS);
                    globalQuiet.set(false);
                }
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            } finally {
                globalQuiet.set(false);
                done.countDown();
            }
        }, "quiet-driver");
        quietDriver.start();

        // Data feeder: keeps pushing data through distributor — adds another global-lock contender.
        // increases the symbol pool to launch rehash
        Thread feeder = new Thread(() -> {
            Random random = new Random();
            try {
                long currentTime;
                long prevTime = getCurrentTimeInMillis();
                while ((currentTime = getCurrentTimeInMillis()) < deadline && failure.get() == null) {
                    awaitNotQuiet(globalQuiet, deadline, failure);
                    RecordBuffer data = new RecordBuffer();
                    int active = symbolsActive.get();
                    int count = 1 + random.nextInt(20);
                    for (int i = 0; i < count; i++) {
                        int idx = random.nextInt(active);
                        RecordCursor c = data.add(record, ciphers[idx], symbols[idx]);
                        for (int f = 0; f < intFieldCount; f++) {
                            c.setInt(f, random.nextInt());
                        }
                    }
                    distributor.process(data);
                    sleep(1);

                    // expand symbols pool
                    if (currentTime - prevTime > SYMBOLS_GROW_INTERVAL_MS) {
                        symbolsActive.set(Math.min(SYMBOLS_POOL, active + SYMBOLS_GROW_STEP));
                        prevTime = currentTime;
                    }
                }
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            } finally {
                done.countDown();
            }
        }, "feeder");
        feeder.start();

        done.await(TEST_DURATION_MS + 10_000L, TimeUnit.MILLISECONDS);

        // Drain any agents still pending close.
        pendingClose.forEach(qdAgent -> {
            try {
                qdAgent.close();
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            }
        });

        // Close remaining background agents.
        Arrays.stream(bgSlots).forEach(slot -> {
            try {
                slot.close();
            } catch (Throwable e) {
                failure.compareAndSet(null, e);
            }
        });

        distributor.close();
        ticker.close();

        Throwable t = failure.get();
        if (t != null) {
            throw new AssertionError("Reproduced failure: " + t, t);
        }
    }

    private static QDAgent newBackgroundAgent(
        QDTicker ticker, DataRecord record, int[] ciphers, String[] symbols, int active, Random rnd)
    {
        QDAgent agent = ticker.agentBuilder().build();
        RecordBuffer sub = new RecordBuffer(RecordMode.SUBSCRIPTION);
        for (int s = 0; s < active; s++) {
            if (rnd.nextBoolean()) {
                sub.add(record, ciphers[s], symbols[s]);
            }
        }
        agent.addSubscription(sub);
        return agent;
    }

    private static void burst(Random rnd, QDTicker ticker, DataRecord record, int[] ciphers, String[] symbols,
        AtomicInteger symbolsActive, ConcurrentLinkedDeque<QDAgent> pendingClose, long deadline,
        AtomicReference<Throwable> failure)
    {
        long burstEnd = Math.min(getCurrentTimeInMillis() + BURST_WINDOW_MS, deadline);
        while (getCurrentTimeInMillis() < burstEnd && failure.get() == null) {
            QDAgent agent = ticker.agentBuilder().build();

            RecordBuffer sub = new RecordBuffer(RecordMode.SUBSCRIPTION);
            int active = symbolsActive.get();
            int count = 1 + rnd.nextInt(Math.max(1, active / 2));
            for (int i = 0; i < count; i++) {
                int idx = rnd.nextInt(active);
                sub.add(record, ciphers[idx], symbols[idx]);
            }
            agent.setSubscription(sub);

            if (rnd.nextInt(3) == 0) {
                pendingClose.add(agent);
            } else {
                agent.close();
            }

            if (rnd.nextInt(8) == 0) {
                pendingClose.forEach(QDAgent::close);
            }
        }
    }

    private static long getCurrentTimeInMillis() {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime());
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void awaitNotQuiet(AtomicBoolean globalQuiet, long deadline, AtomicReference<Throwable> failure) {
        while (globalQuiet.get() && getCurrentTimeInMillis() < deadline && failure.get() == null) {
            sleep(50);
        }
    }
}
