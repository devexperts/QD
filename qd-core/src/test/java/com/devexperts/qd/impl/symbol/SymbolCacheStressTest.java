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
package com.devexperts.qd.impl.symbol;

import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;

public class SymbolCacheStressTest {

    public static final int MAX_SYMBOL = 1_000;
    public static final int ITERATIONS = 100_000;
    public static final int CONCURRENCY = 100;

    public static final String[] SYMBOLS = IntStream.range(0, MAX_SYMBOL)
        .mapToObj(String::valueOf)
        .toArray(String[]::new);

    private volatile Exception exception;

    @Ignore("Stress test. To be run manually")
    @Test(timeout = 300_000L)
    public void testStress() throws Exception {
        SymbolCache cache = SymbolCache.newBuilder().withTtl(0).withInitialCapacity(MAX_SYMBOL * 2).build();
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
        CountDownLatch latch = new CountDownLatch(CONCURRENCY);

        executor.submit(() -> runCleanUp(cache, latch));
        for (int i = 0; i < CONCURRENCY - 1; i++) {
            executor.submit(() -> runAcquireRelease(cache, latch));
        }

        latch.await();
        if (exception != null)
            throw exception;

        int size = cache.size();
        cache.cleanUp();
        int cleaned = size - cache.size();
        assertEquals(0, cache.size());
    }

    private void runAcquireRelease(SymbolCache cache, CountDownLatch latch) {
        try {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();

            // First iterations put new symbols into the cache and later iterations retrieve them it
            for (int i = 0; i < ITERATIONS; i++) {
                String symbol = SYMBOLS[rnd.nextInt(SYMBOLS.length)];
                cache.resolveAndAcquire(symbol);
                Thread.yield();
                cache.release(symbol);
                Thread.yield();

                //if (i % 10_000 == 0)
                //    System.out.println(Thread.currentThread().getName() + " " + i);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            exception = e;
        } finally {
            latch.countDown();
        }
    }

    private void runCleanUp(SymbolCache cache, CountDownLatch latch) {
        try {
            // Run cleanUp several times per second until no other tasks remain
            while (latch.getCount() > 1) {
                Thread.sleep(100);
                int size = cache.size();
                cache.cleanUp();
                int cleaned = size - cache.size();

                //System.out.println("CleanUp " + cleaned);
            }
        } catch (Exception e) {
            e.printStackTrace(System.out);
            exception = e;
        } finally {
            latch.countDown();
        }
    }
}
