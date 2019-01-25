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
package com.devexperts.logging.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.*;

/**
 * Tests {@link com.devexperts.logging.LogFormatter} in multithreaded environment.
 */
public class MultithreadedLogFormatterTest extends StandardLogFormatterTest {
    public static final int THREADS_NUMBER = 15;
    public static final int REPEATS_NUMBER = 200;
    public static final int TIMEOUT_SECONDS = 60;

    protected CountDownLatch startSignal;
    protected CountDownLatch doneSignal;
    protected List<WorkingThread> thread_list;

    public MultithreadedLogFormatterTest() {
    }

    public MultithreadedLogFormatterTest(String name) {
        super(name);
    }


    protected void setUp() throws Exception {
        super.setUp();
        startSignal = new CountDownLatch(1);
        doneSignal = new CountDownLatch(THREADS_NUMBER);
        thread_list = new ArrayList<WorkingThread>(THREADS_NUMBER);
        for (int thread_number = 0; thread_number < THREADS_NUMBER; thread_number++) {
            final WorkingThread thread = new WorkingThread(thread_number);
            thread_list.add(thread);
        }
    }

    public void testMultithreading() throws InterruptedException {
        for (final WorkingThread thread : thread_list) {
            thread.start();
        }
        // let all threads proceed
        startSignal.countDown();

        // wait for all to finish
        final boolean result = doneSignal.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("Thread completion timeout expired. Probably deadlock exists.", result);

        for (final WorkingThread thread : thread_list) {
            if (thread.error != null) {
                throw new AssertionFailedError("Exception is thrown during thread execution: " + thread.error);
            }
        }
    }

    public static Test suite() {
        TestSuite suite = new TestSuite();
        suite.addTest(new MultithreadedLogFormatterTest("testMultithreading"));
        return suite;
    }

    protected class WorkingThread extends Thread {
        public volatile Throwable error;

        public WorkingThread(final Integer thread_number) {
            super(Integer.toString(thread_number));
        }

        public void run() {
            try {
                startSignal.await();
                try {
                    for (int iter_number = 0; iter_number < REPEATS_NUMBER; iter_number++) {
                        testFormatting();
                        testIncorrectPattern();
                        final String name = getName() + iter_number * 1000;
                        checkResultMatches(name, "_" + name);
                    }
                } catch (Throwable e) {
                    error = e;
                }
            } catch (InterruptedException ie) {
            }

            doneSignal.countDown();
        }
    }
}
