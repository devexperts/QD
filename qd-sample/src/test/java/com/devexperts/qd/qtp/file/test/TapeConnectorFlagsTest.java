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

import com.devexperts.qd.ng.EventFlag;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.Order;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
//@Parameterized.UseParametersRunnerFactory(TraceRunnerWithParametersFactory.class)
public class TapeConnectorFlagsTest {

    private static final String SYMBOL = "Order";
    private static final List<Order> EVENTS = Arrays.asList(
        createOrder(123456789012L, 2, EventFlag.SNAPSHOT_BEGIN.flag()),
        createOrder(123456789013L, 1, 0),
        createOrder(123456789014L, 0, EventFlag.SNAPSHOT_SNIP.flag() | EventFlag.SNAPSHOT_END.flag()));

    private final String fileFormat;
    private final String timeFormat;

    private Path tapeFile;
    private Path timeFile;

    public TapeConnectorFlagsTest(String fileFormat, String timeFormat) {
        this.fileFormat = fileFormat;
        this.timeFormat = timeFormat;
    }

    @Parameterized.Parameters(name = "fileFormat={0}, timeFormat={1}")
    public static Iterable<Object[]> parameters() {
        ArrayList<Object[]> parameters = new ArrayList<>();
        String[] fileFormats = {"text", "binary"};
        String[] timeFormats = {"long", "text", "field"}; // FIXME: field doesn't work
        for (String fileFormat : fileFormats) {
            for (String timeFormat : timeFormats) {
                parameters.add(new Object[] {fileFormat, timeFormat});
            }
        }
        return parameters;
    }

    // FIXME: tape connector cannot use absolute paths on windows
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder(new File("./target"));

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        tapeFile = tempFolder.getRoot().toPath().resolve("TapeConnectorTest.qds");
        timeFile = tempFolder.getRoot().toPath().resolve("TapeConnectorTest.time");
    }

    @After
    public void tearDown() throws IOException {
        ThreadCleanCheck.after();
    }

    @Test
    public void testEventFlagsEnabled() throws InterruptedException {
        doTestEventFlags(true);
    }

    @Test
    public void testEventFlagsDisabled() throws InterruptedException {
        doTestEventFlags(false);
    }

    private void doTestEventFlags(boolean flagsEnabled) throws InterruptedException {
        String opt = flagsEnabled ? ",opt=hs" : "";
        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withProperty(DXEndpoint.DXFEED_WILDCARD_ENABLE_PROPERTY, "true")
            .withProperty(DXEndpoint.DXENDPOINT_EVENT_TIME_PROPERTY, "true")
            .withProperty(DXEndpoint.DXSCHEME_NANO_TIME_PROPERTY, "true") // enable nanoTime
            .withRole(DXEndpoint.Role.PUBLISHER)
            .build()
            .connect("tape:" + tapeFile + "[time=" + timeFormat + ",format=" + fileFormat + opt + "]");
        endpoint.getPublisher().publishEvents(EVENTS);
        endpoint.awaitProcessed();
        endpoint.closeAndAwaitTermination();

        endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.STREAM_FEED)
            .withProperty(DXEndpoint.DXENDPOINT_EVENT_TIME_PROPERTY, "true") // enable eventTime
            .withProperty(DXEndpoint.DXSCHEME_NANO_TIME_PROPERTY, "true") // enable nanoTime
            .build();
        DXFeedSubscription<Order> sub = endpoint.getFeed().createSubscription(Order.class);
        List<Order> events = new ArrayList<>();
        sub.addEventListener(events::addAll);
        sub.addSymbols(SYMBOL);
        endpoint.connect("file:" + tapeFile).awaitNotConnected();
        endpoint.closeAndAwaitTermination();
        System.out.println("events = " + events);

        assertEquals(EVENTS.size(), events.size());
        for (int i = 0; i < EVENTS.size(); i++) {
            assertEquals("TimeNanos", EVENTS.get(i).getTimeNanos(), events.get(i).getTimeNanos());
            assertEquals("EventTime", EVENTS.get(i).getEventTime(), events.get(i).getEventTime());
            assertEquals("Flags", flagsEnabled ? EVENTS.get(i).getEventFlags() : 0, events.get(i).getEventFlags());
        }
    }

    private static Order createOrder(long time, int index, int flags) {
        Order t = new Order(SYMBOL);
        t.setTimeNanos(time);
        t.setEventTime(time);
        t.setEventFlags(flags);
        t.setIndex(index);
        return t;
    }
}
