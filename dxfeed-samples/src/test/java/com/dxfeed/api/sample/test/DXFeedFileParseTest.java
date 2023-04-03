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
package com.dxfeed.api.sample.test;

import com.devexperts.logging.Logging;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.Quote;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(TraceRunner.class)
public class DXFeedFileParseTest {
    private static final int DELAY_MS = 500;
    private static final int EXPECTED_NO_OF_QUOTES = 48;

    private static final Logging log = Logging.getLogging(DXFeedFileParseTest.class);

    DXEndpoint endpoint = DXEndpoint.newBuilder()
        .withProperty(DXEndpoint.DXENDPOINT_EVENT_TIME_PROPERTY, "true")
        .withRole(DXEndpoint.Role.STREAM_FEED)
        .build();
    DXFeed feed = endpoint.getFeed();
    ScheduledExecutorService executor;

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        if (executor != null)
            executor.shutdown();
        ThreadCleanCheck.after();
    }

    @Test
    public void testQuotesFromSampleFile() throws InterruptedException {
        check(false);
    }

    // this test is designed to expose a bug in [QD-897]
    // DXEndpoint.awaitNotConnected/closeAndAwaitTermination does not always guarantee that all data was processed
    @Test
    public void testQuotesFromSampleFileWithDelay() throws InterruptedException {
        check(true);
    }

    private void check(boolean delayProcessing) throws InterruptedException {
        if (delayProcessing) {
            // execute after 500 ms delay
            executor = Executors.newSingleThreadScheduledExecutor();
            endpoint.executor(task -> executor.schedule(task, DELAY_MS, TimeUnit.MILLISECONDS));
        }
        // subscribe to a specified event and symbol
        DXFeedSubscription<Quote> sub = feed.createSubscription(Quote.class);

        final List<Quote> quotes = new ArrayList<>();
        sub.addEventListener(quotes::addAll);
        sub.addSymbols("IBM");

        // will verify all generated events, too
        final List<PropertyChangeEvent> propertyChangeEvents = new ArrayList<>();
        endpoint.addStateChangeListener(pce -> {
            log.info("State change: " + pce.getOldValue() + "->" + pce.getNewValue());
            propertyChangeEvents.add(pce);
        });

        // connect endpoint to a file
        File file = new File("files", "demo-sample.data");
        if (!file.exists())
            file = new File("dxfeed-samples", file.getPath());
        assertTrue("File " + file + " is not found", file.exists());
        endpoint.connect("file:" + file + "[speed=max]");

        // wait until file is completely parsed
        log.info("Invoking awaitNotConnected()");
        endpoint.awaitNotConnected();

        // close endpoint when we're done
        // this method will gracefully close endpoint, waiting while data processing completes
        log.info("Invoking closeAndAwaitTermination()");
        endpoint.closeAndAwaitTermination();

        // check that all quotes were read
        assertEquals(EXPECTED_NO_OF_QUOTES, quotes.size());

        // check that all quotes have event time
        assertTrue(quotes.stream().allMatch(q -> q.getEventTime() != 0));

        // check that all state change events were generated in a proper order
        assertTrue("PropertyChangeEvents were processed", propertyChangeEvents.size() >= 1);
        for (PropertyChangeEvent pce : propertyChangeEvents) {
            assertEquals(endpoint, pce.getSource());
            assertEquals("state", pce.getPropertyName());
        }
        assertEquals(DXEndpoint.State.NOT_CONNECTED, propertyChangeEvents.get(0).getOldValue());
        assertEquals(DXEndpoint.State.CLOSED, propertyChangeEvents.get(propertyChangeEvents.size() - 1).getNewValue());
    }
}
