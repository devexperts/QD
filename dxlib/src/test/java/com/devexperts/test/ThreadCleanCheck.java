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
package com.devexperts.test;

import com.devexperts.logging.Logging;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nonnull;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * Testing utility class to be used in tests to verify that all threads created by the test have terminated
 * when test finished.
 */
public class ThreadCleanCheck {
    private static final Logging log = Logging.getLogging(ThreadCleanCheck.class);
    private static final long WAIT_TIME = 60_000;
    private static final ThreadLocal<Data> BEFORE = new ThreadLocal<>();

    private static class Data {
        final String description;
        final Set<Thread> threads;

        Data(String description, Set<Thread> threads) {
            this.description = description;
            this.threads = threads;
        }
    }

    public static void before() {
        before("test");
    }

    public static void before(String description) {
        Set<Thread> threads = getThreads();
        log.info("======== Running " + description + " (" + threads.size() + " threads before) ========");
        BEFORE.set(new Data(description, threads));
    }

    public static void after() {
        Data before = BEFORE.get();
        log.info("======== Done " + before.description + " ========");
        assertNotNull(before);
        // wait some time for threads to (maybe) shutdown
        long curTime = System.currentTimeMillis();
        long limitTime = curTime + WAIT_TIME;
        while (true) {
            Set<Thread> leaked = getThreads();
            leaked.removeAll(before.threads);
            if (leaked.isEmpty())
                return; // Ok -- nothing leaked
            // see how much to wait
            long remTime = limitTime - curTime;
            if (remTime <= 0) {
                // No more time left to wait
                for (Thread thread : leaked) {
                    Throwable t = new Throwable("Stack trace");
                    t.setStackTrace(thread.getStackTrace());
                    log.error("-------- Leaked thread " + thread, t);
                }
                fail("Leaked threads");
            }
            // join first one
            try {
                leaked.iterator().next().join(remTime);
            } catch (InterruptedException e) {
                fail("Wait interrupted");
            }
            curTime = System.currentTimeMillis();
        }
    }

    /**
     * Returns a set of all threads.
     */
    @Nonnull
    public static Set<Thread> getThreads() {
        Thread[] a = new Thread[Thread.activeCount()];
        int n = Thread.enumerate(a);
        LinkedHashSet<Thread> result = new LinkedHashSet<>(Arrays.asList(a).subList(0, n));
        return result;
    }
}
