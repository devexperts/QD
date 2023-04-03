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
package com.devexperts.qd.qtp.file.test;

import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunnerWithParametersFactory;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.market.Trade;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TraceRunnerWithParametersFactory.class)
public class TapeConnectorTest {
    private static final String TAPE_FILE_PREFIX = "TapeConnectorTest";
    private static final Path TAPE_FILE = Paths.get(TAPE_FILE_PREFIX + ".qds");
    private static final Path TIME_FILE = Paths.get(TAPE_FILE_PREFIX + ".time");
    private static final String SYMBOL = "Trade";
    private static final List<Trade> TRADES = Arrays.asList(
        createTrade(123456789012L), createTrade(123456789013L), createTrade(123456789014L));

    private final String fileFormat;
    private final String timeFormat;

    public TapeConnectorTest(String fileFormat, String timeFormat) {
        this.fileFormat = fileFormat;
        this.timeFormat = timeFormat;
    }

    @Parameterized.Parameters(name="fileFormat={0}, timeFormat={1}")
    public static Iterable<Object[]> parameters() {
        ArrayList<Object[]> parameters = new ArrayList<>();
        String[] fileFormats = {"text", "binary"};
        String[] timeFormats = {"long", "text", "field"};
        for (String fileFormat : fileFormats) {
            for (String timeFormat : timeFormats) {
                parameters.add(new Object[] {fileFormat, timeFormat});
            }
        }
        return parameters;
    }

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(TAPE_FILE);
        Files.deleteIfExists(TIME_FILE);
        ThreadCleanCheck.after();
    }

    @Ignore("this test fails consistently. fix it or drop it")
    @Test
    public void testWithEventAndNanoTimes() throws Exception {
        // 1. Create endpoint with PUBLISHER role and connect to tape file
        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.PUBLISHER)
            .withProperty(DXEndpoint.DXFEED_WILDCARD_ENABLE_PROPERTY, "true")
            .withProperty(DXEndpoint.DXENDPOINT_EVENT_TIME_PROPERTY, "true") // enable eventTime
            .withProperty(DXEndpoint.DXSCHEME_NANO_TIME_PROPERTY, "true") // enable nanoTime
            .build();
        DXPublisher publisher = endpoint.getPublisher();
        endpoint.connect("tape:" + TAPE_FILE + "[format=" + fileFormat + ",time=" + timeFormat + "]");
        // 2. Publish events and close endpoint
        publisher.publishEvents(TRADES);
        endpoint.awaitProcessed();
        endpoint.closeAndAwaitTermination();
        // 3. Read published events
        endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.STREAM_FEED)
            .withProperty(DXEndpoint.DXENDPOINT_EVENT_TIME_PROPERTY, "true") // enable eventTime
            .withProperty(DXEndpoint.DXSCHEME_NANO_TIME_PROPERTY, "true") // enable nanoTime
            .build();
        DXFeedSubscription<Trade> sub = endpoint.getFeed().createSubscription(Trade.class);
        List<Trade> events = new ArrayList<>();
        sub.addEventListener(events::addAll);
        sub.addSymbols(SYMBOL);
        endpoint.connect("file:" + TAPE_FILE).awaitNotConnected();
        endpoint.closeAndAwaitTermination();
        // 4. Check that events are the same as published
        assertEquals(TRADES.size(), events.size());
        for (int i = 0; i < TRADES.size(); i++) {
            assertEquals("TimeNanos", TRADES.get(i).getTimeNanos(), events.get(i).getTimeNanos());
            assertEquals("EventTime", TRADES.get(i).getEventTime(), events.get(i).getEventTime());
        }
    }

    private static Trade createTrade(long time) {
        Trade t = new Trade(SYMBOL);
        t.setTimeNanos(time);
        t.setEventTime(time);
        return t;
    }
}
