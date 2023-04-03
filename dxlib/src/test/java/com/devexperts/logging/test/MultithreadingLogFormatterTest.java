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
package com.devexperts.logging.test;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests {@link com.devexperts.logging.LogFormatter} in multithreading environment.
 */
public class MultithreadingLogFormatterTest extends StandardLogFormatterTest {
    public static final int THREADS_NUMBER = 15;
    public static final int REPEATS_NUMBER = 200;
    public static final int TIMEOUT_SECONDS = 60;

    protected CountDownLatch startSignal;
    protected CountDownLatch doneSignal;
    protected List<WorkingThread> threadList;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        startSignal = new CountDownLatch(1);
        doneSignal = new CountDownLatch(THREADS_NUMBER);
        threadList = new ArrayList<WorkingThread>(THREADS_NUMBER);
        for (int threadNumber = 0; threadNumber < THREADS_NUMBER; threadNumber++) {
            final WorkingThread thread = new WorkingThread(threadNumber);
            threadList.add(thread);
        }
    }

    @Test
    public void testMultithreading() throws InterruptedException {
        for (final WorkingThread thread : threadList) {
            thread.start();
        }
        // let all threads proceed
        startSignal.countDown();

        // wait for all to finish
        final boolean result = doneSignal.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("Thread completion timeout expired. Probably deadlock exists.", result);

        for (final WorkingThread thread : threadList) {
            if (thread.error != null) {
                fail("Exception is thrown during thread execution: " + thread.error);
            }
        }
    }

    protected class WorkingThread extends Thread {
        public volatile Throwable error;

        public WorkingThread(final Integer threadNumber) {
            super(Integer.toString(threadNumber));
        }

        public void run() {
            try {
                startSignal.await();
                try {
                    for (int iterNumber = 0; iterNumber < REPEATS_NUMBER; iterNumber++) {
                        testFormatting();
                        testIncorrectPattern();
                        final String name = getName() + iterNumber * 1000;
                        checkResultMatches(name, "_" + name);
                    }
                } catch (Throwable e) {
                    error = e;
                }
            } catch (InterruptedException ignored) {
            }

            doneSignal.countDown();
        }
    }
}
