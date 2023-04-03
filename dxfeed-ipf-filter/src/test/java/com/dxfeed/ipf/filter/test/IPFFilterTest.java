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
package com.dxfeed.ipf.filter.test;

import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.Quote;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(TraceRunner.class)
public class IPFFilterTest {
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final int RANDOM_PORT_0 = 10000 + new Random().nextInt(10000); // port randomization

    private final int sequence = SEQUENCE.getAndIncrement();
    private final int port = RANDOM_PORT_0 + sequence;

    private final List<File> files = new ArrayList<>();

    private final BlockingQueue<String> addedQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Quote> eventQueue = new LinkedBlockingQueue<>();

    private DXEndpoint publisher;
    private DXEndpoint feed;

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() throws Exception {
        feed.closeAndAwaitTermination();
        publisher.closeAndAwaitTermination();
        for (File file : files) {
            assertTrue(file.delete());
        }
        ThreadCleanCheck.after();
    }

    @Test
    public void testDynamicAgentIPFFilter() throws InterruptedException, IOException {
        // We also test various special symbol in the file name.
        File ipfFile = new File("IPFFilterTest),@-tmp-" + sequence + ".ipf");
        checkSimple("ipf[" + escape(ipfFile) + ",update=0.1s]@:" + port,
            "localhost:" + port, false, ipfFile);
    }

    @Test
    public void testCompositeDynamicAgentIPFFilter() throws InterruptedException, IOException {
        // We also test various special symbol in the file name.
        File ipfFile = new File("IPFFilterTest=-tmp-" + sequence + ".ipf");
        checkSimple("((A,B,C)&ipf[" + escape(ipfFile) + ",update=0.1s]@:" + port + ")",
            "localhost:" + port, false, ipfFile);
    }

    @Test
    public void testDynamicDistributorIPFFilter() throws InterruptedException, IOException {
        File ipfFile = new File("IPFFilterTest)-tmp-" + sequence + ".ipf");
        checkSimple(":" + port,
            "ipf[" + escape(ipfFile) + ",update=0.1s]@localhost:" + port, true, ipfFile);
    }

    private String escape(File file) {
        return file.toString().replaceAll("[),@]", "\\\\$0");
    }

    private void checkSimple(String pubAddress, String feedAddress, boolean distributorSideFilter, File ipfFile)
        throws IOException, InterruptedException
    {
        files.add(ipfFile);
        IPFWriter writer = new IPFWriter(ipfFile);
        writer.writeIPFFile("A");
        createPublisher(pubAddress);
        createFeed(feedAddress);
        // check that only A's subscription comes in (per initial filter);
        assertEquals("A", addedQueue.poll(10, TimeUnit.SECONDS));
        assertEquals(0, addedQueue.size());
        // check that generated A's Quote gets back
        assertEquals("A", eventQueue.poll(10, TimeUnit.SECONDS).getEventSymbol());
        assertEquals(0, eventQueue.size());
        // make sure that there's still no more subscription
        assertEquals(0, addedQueue.size());
        // ------------------------------------------------------------
        // now change filter & check that B's subscription comes in
        writer.writeIPFFile("A", "B");
        if (distributorSideFilter) {
            // connection reset and subscription for both event will arrive back again
            assertEquals(new HashSet<>(Arrays.asList("A", "B")),
                new HashSet<>(Arrays.asList(
                    addedQueue.poll(10, TimeUnit.SECONDS),
                    addedQueue.poll(10, TimeUnit.SECONDS))));
        } else {
            // connection does not reset and just one arrives
            assertEquals("B", addedQueue.poll(10, TimeUnit.SECONDS));
        }
        assertEquals(0, addedQueue.size());
        // check that generated B's Quote gets back
        assertEquals("B", eventQueue.poll(10, TimeUnit.SECONDS).getEventSymbol());
        assertEquals(0, eventQueue.size());
        // make sure that there's still no more subscription
        assertEquals(0, addedQueue.size());
        // ------------------------------------------------------------
        // now change filter AGAIN to make sure that new filter is properly listened to
        // and check that C's subscription comes in
        writer.writeIPFFile("A", "B", "C");
        if (distributorSideFilter) {
            // connection reset and subscription for all events will arrive back again
            assertEquals(new HashSet<>(Arrays.asList("A", "B", "C")),
                new HashSet<>(Arrays.asList(
                    addedQueue.poll(10, TimeUnit.SECONDS),
                    addedQueue.poll(10, TimeUnit.SECONDS),
                    addedQueue.poll(10, TimeUnit.SECONDS))));
        } else {
            // connection does not reset and just one arrives
            assertEquals("C", addedQueue.poll(10, TimeUnit.SECONDS));
        }
        assertEquals(0, addedQueue.size());
        // check that generated C's Quote gets back
        assertEquals("C", eventQueue.poll(10, TimeUnit.SECONDS).getEventSymbol());
        assertEquals(0, eventQueue.size());
        // make sure that there's still no more subscription
        assertEquals(0, addedQueue.size());
    }

    @Test
    public void testMultipleIPFFiles() throws IOException, InterruptedException {
        File projectB = new File("IPFFilterTest-projectB.ipf");
        File projectC = new File("IPFFilterTest-projectC.ipf");
        files.add(projectB);
        files.add(projectC);
        IPFWriter projectBWriter = new IPFWriter(projectB);
        projectBWriter.writeIPFFile();
        IPFWriter projectCWriter = new IPFWriter(projectC);
        projectCWriter.writeIPFFile("A");
        createPublisher("(!:Book*&(ipf[" + projectC + ",update=0.1s],ipf[" + projectB + ",update=0.1s])@:" + port + ")");
        String feedAddress = "localhost:" + port;
        createFeed(feedAddress);
        // check that only A's subscription comes in (per initial filter);
        assertEquals("A", addedQueue.poll(10, TimeUnit.SECONDS));
        assertEquals(0, addedQueue.size());
        // check that generated A's Quote gets back
        assertEquals("A", eventQueue.poll(10, TimeUnit.SECONDS).getEventSymbol());
        assertEquals(0, eventQueue.size());
        // make sure that there's still no more subscription
        assertEquals(0, addedQueue.size());
        // ------------------------------------------------------------
        // now change 'projectB' filter & check that B's subscription comes in
        projectBWriter.writeIPFFile("B");
        // connection does not reset and just one arrives
        assertEquals("B", addedQueue.poll(10, TimeUnit.SECONDS));
        assertEquals(0, addedQueue.size());
        // check that generated B's Quote gets back
        assertEquals("B", eventQueue.poll(10, TimeUnit.SECONDS).getEventSymbol());
        assertEquals(0, eventQueue.size());
        // make sure that there's still no more subscription
        assertEquals(0, addedQueue.size());
        // ------------------------------------------------------------
        // now change 'projectC' filter & check that C's subscription comes in
        projectCWriter.writeIPFFile("A", "C");
        assertEquals("C", addedQueue.poll(10, TimeUnit.SECONDS));
        assertEquals(0, addedQueue.size());
        // check that generated C's Quote gets back
        assertEquals("C", eventQueue.poll(10, TimeUnit.SECONDS).getEventSymbol());
        assertEquals(0, eventQueue.size());
        // make sure that there's still no more subscription
        assertEquals(0, addedQueue.size());
        // =============================================================
        // Now reconnect and make sure that subscription to all 3 symbols comes in
        feed.disconnect();
        feed.connect(feedAddress);
        Set<String> aSet = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            aSet.add(addedQueue.poll(10, TimeUnit.SECONDS));
        }
        assertEquals(3, aSet.size());
    }

    private void createPublisher(String address) {
        publisher = DXEndpoint.newBuilder().withName("publisher").withRole(DXEndpoint.Role.PUBLISHER).build();
        publisher.connect(address);
        publisher.getPublisher().getSubscription(Quote.class).addChangeListener(symbols -> {
            for (Object objSymbol : symbols) {
                String symbol = (String) objSymbol;
                addedQueue.add(symbol);
                Quote quote = new Quote(symbol);
                quote.setBidSize(symbol.hashCode());
                publisher.getPublisher().publishEvents(Collections.singletonList(quote));
            }
        });
    }

    private void createFeed(String address) {
        feed = DXEndpoint.newBuilder().withName("feed").withRole(DXEndpoint.Role.FEED).build();
        feed.connect(address);
        DXFeedSubscription<Quote> sub = feed.getFeed().createSubscription(Quote.class);
        sub.addEventListener(eventQueue::addAll);
        sub.addSymbols("A", "B", "C");
    }
}
