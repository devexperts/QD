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
package com.devexperts.util.test;

import com.devexperts.test.isolated.Isolated;
import com.devexperts.test.isolated.IsolatedRunner;
import com.devexperts.util.Indexer;
import com.devexperts.util.IndexerFunction;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

@RunWith(IsolatedRunner.class)
@Isolated({"com.devexperts.util"})
public class IndexerInitTest {

    @Test
    public void testIndexerInitialization() throws InterruptedException {
        CountDownLatch startFlag = new CountDownLatch(2);
        CountDownLatch finishFlag = new CountDownLatch(2);
        Thread t1 = new Thread(() -> {
            waitStart(startFlag);
            IndexerFunction<String, String> indexerFunction = IndexerFunction.DEFAULT;
            finishFlag.countDown();
        });
        Thread t2 = new Thread(() -> {
            waitStart(startFlag);
            Indexer indexer = Indexer.DEFAULT;
            finishFlag.countDown();
        });
        t1.start();
        t2.start();

        assertTrue("Initialization threads timed out", finishFlag.await(1, TimeUnit.MINUTES));
    }

    static void waitStart(CountDownLatch startFlag) {
        startFlag.countDown();
        try {
            if (!startFlag.await(1, TimeUnit.MINUTES))
                throw new RuntimeException("Waiting for a start flag timed out");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
