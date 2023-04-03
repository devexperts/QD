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
package com.devexperts.qd.tools.test;

import com.devexperts.mars.common.MARSNode;
import com.devexperts.qd.tools.Tools;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.event.market.Quote;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.Assert.assertTrue;

public class CompareTest {
    private static final String SYMBOL = "IBM";
    private static final String SYMBOL_A = "IBM&A";

    private final int randomPortOffset = 10000 + new Random().nextInt(10000); // port randomization

    DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.PUBLISHER);
    Thread publishThread;
    Thread toolThread;
    volatile boolean toolOk = true;

    @Before
    public void setUp() throws Exception {
        // produce quotes constantly
        endpoint.connect(":" + randomPortOffset);
        publishThread = new Thread("Publish") {
            @Override
            public void run() {
                double price = 100.0;
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Quote quote = new Quote(SYMBOL);
                        quote.setBidPrice(price);
                        quote.setAskPrice(price + 1);
                        Quote quoteA = new Quote(SYMBOL_A);
                        quoteA.setBidPrice(price);
                        quoteA.setAskPrice(price + 1);
                        endpoint.getPublisher().publishEvents(Arrays.asList(quote, quoteA));
                        Thread.sleep(10);
                        price += 0.01;
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        };
        publishThread.start();
    }

    @After
    public void tearDown() throws Exception {
        toolThread.interrupt();
        toolThread.join();
        assertTrue(toolOk);
        publishThread.interrupt();
        publishThread.join();
        endpoint.close();
    }

    @Test
    public void testCompareOtherAddress() throws InterruptedException {
        // start tool
        toolThread = new Thread("Compare") {
            @Override
            public void run() {
                toolOk = Tools.invoke("compare",
                    "localhost:" + randomPortOffset,
                    "Quote", SYMBOL,
                    "--names", "a/b",
                    "-A", "localhost:" + randomPortOffset,
                    "-s", "0.1s",
                    "-c", "stream");
            }
        };
        toolThread.start();
        waitMatched("a/b");
    }

    @Test
    public void testCompareOtherCollector() throws InterruptedException {
        // start tool
        toolThread = new Thread("Compare") {
            @Override
            public void run() {
                toolOk = Tools.invoke("compare",
                    "localhost:" + randomPortOffset,
                    "Quote", SYMBOL,
                    "--names", "c/d",
                    "-C", "ticker",
                    "-s", "0.1s",
                    "-c", "stream");
            }
        };
        toolThread.start();
        waitMatched("c/d");
    }

    @Test
    public void testCompareOtherRecord() throws InterruptedException {
        // start tool
        toolThread = new Thread("Compare") {
            @Override
            public void run() {
                toolOk = Tools.invoke("compare",
                    "localhost:" + randomPortOffset,
                    "Quote", SYMBOL,
                    "--names", "e/f",
                    "-R", "Quote&A",
                    "--fields", "BidPrice,AskPrice",
                    "-s", "0.1s",
                    "-c", "stream");
            }
        };
        toolThread.start();
        waitMatched("e/f");
    }

    private void waitMatched(String name) throws InterruptedException {
        // wait for comparison results from MARS
        MARSNode matched = MARSNode.getRoot().subNode(name + ".matched");
        // wait for at most 10 seconds
        boolean success = false;
        for (int i = 0; toolOk && i < 1000; i++) {
            String s = matched.getValue();
            if (s != null && !s.isEmpty() && Integer.parseInt(s) > 0) {
                success = true;
                break; // success
            }
            Thread.sleep(10);
        }
        assertTrue(success);
    }
}
