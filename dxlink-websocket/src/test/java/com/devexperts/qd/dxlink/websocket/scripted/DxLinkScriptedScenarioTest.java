/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.dxlink.websocket.scripted;

import com.devexperts.qd.qtp.AbstractMessageConnector;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageAdapterMBean;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.util.TimePeriod;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.impl.DXEndpointImpl;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.TimeAndSale;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.Assert.fail;

/**
 * Drives the dxLink client through its handshake-and-subscribe wire conversation against
 * {@link ScriptedDxLinkServer}, verifying that every outbound JSON message arrives in the
 * expected order and that the script-driven server can complete a full SETUP → AUTH_STATE →
 * CHANNEL_REQUEST → FEED_SETUP → FEED_SUBSCRIPTION round-trip.
 */
public class DxLinkScriptedScenarioTest {
    private static final String EXPERIMENTAL_FLAG = "dxfeed.experimental.dxlink.enable";

    private String savedExperimentalFlag;
    private ScriptedDxLinkServer server;
    private DXEndpoint endpoint;

    @Before
    public void setUp() {
        savedExperimentalFlag = System.getProperty(EXPERIMENTAL_FLAG);
        System.setProperty(EXPERIMENTAL_FLAG, "true");
    }

    @After
    public void tearDown() {
        if (endpoint != null)
            endpoint.close();
        if (server != null)
            server.close();
        if (savedExperimentalFlag == null)
            System.clearProperty(EXPERIMENTAL_FLAG);
        else
            System.setProperty(EXPERIMENTAL_FLAG, savedExperimentalFlag);
    }

    @Test
    public void testFullSubscriptionHandshake() throws Exception {
        server = ScriptedDxLinkServer.builder()
            .ignoreTypes("KEEPALIVE")
            .expectType("SETUP", "\"version\":\"0.1\"", "\"agent\":{")
            .send("{\"type\":\"SETUP\",\"channel\":0,\"version\":\"0.1-test\","
                + "\"keepaliveTimeout\":60.0,\"acceptKeepaliveTimeout\":60.0}")
            .send("{\"type\":\"AUTH_STATE\",\"channel\":0,\"state\":\"AUTHORIZED\"}")
            .expectType("CHANNEL_REQUEST", "\"channel\":1", "\"contract\":\"TICKER\"")
            .send("{\"type\":\"CHANNEL_OPENED\",\"channel\":1,\"service\":\"FEED\","
                + "\"parameters\":{\"contract\":\"TICKER\"}}")
            .send("{\"type\":\"FEED_CONFIG\",\"channel\":1,\"aggregationPeriod\":1.0,"
                + "\"dataFormat\":\"COMPACT\",\"eventFields\":{\"Quote\":[\"eventSymbol\",\"bidPrice\",\"askPrice\"]}}")
            .expectType("FEED_SETUP")
            .expectType("FEED_SUBSCRIPTION", "\"symbol\":\"AAPL\"", "\"type\":\"Quote\"")
            .start();

        endpoint = DXEndpoint.create(DXEndpoint.Role.FEED).executor(Executors.newSingleThreadExecutor());
        DXFeedSubscription<Quote> subscription = endpoint.getFeed().createSubscription(Quote.class);
        subscription.addSymbols("AAPL");
        endpoint.connect("dxlink:" + server.url());

        server.awaitScriptCompleted(10_000);
    }

    /**
     * Verifies that {@code AggregationPeriodInfo} reflects the server's per-channel
     * {@code aggregationPeriod} replies, and that both control entry points — the per-adapter
     * {@link MessageAdapterMBean#setRequestedAggregationPeriod} JMX attribute and the
     * {@link DXEndpoint#setRequestedAggregationPeriod} API — drive the on-wire request and
     * reset symmetrically across TICKER (Quote) and STREAM (TimeAndSale) channels.
     */
    @Test
    public void testRequestedAggregationPeriodViaAdapterAndEndpoint() throws Exception {
        server = ScriptedDxLinkServer.builder()
            .ignoreTypes("KEEPALIVE")
            .expectType("SETUP")
            .send("{\"type\":\"SETUP\",\"channel\":0,\"version\":\"0.1-test\","
                + "\"keepaliveTimeout\":60.0,\"acceptKeepaliveTimeout\":60.0}")
            .send("{\"type\":\"AUTH_STATE\",\"channel\":0,\"state\":\"AUTHORIZED\"}")
            // initial handshake: TICKER ch1 (Quote), STREAM ch2 (TimeAndSale); server defaults 3s / 10s
            .expectType("CHANNEL_REQUEST", "\"channel\":1", "\"contract\":\"TICKER\"")
            .send("{\"type\":\"CHANNEL_OPENED\",\"channel\":1,\"service\":\"FEED\","
                + "\"parameters\":{\"contract\":\"TICKER\"}}")
            .send("{\"type\":\"FEED_CONFIG\",\"channel\":1,\"aggregationPeriod\":3.0,"
                + "\"dataFormat\":\"COMPACT\",\"eventFields\":{\"Quote\":[\"eventSymbol\",\"bidPrice\",\"askPrice\"]}}")
            .expectType("FEED_SETUP", "\"channel\":1")
            .expectType("FEED_SUBSCRIPTION", "\"channel\":1", "\"type\":\"Quote\"")
            .expectType("CHANNEL_REQUEST", "\"channel\":3", "\"contract\":\"STREAM\"")
            .send("{\"type\":\"CHANNEL_OPENED\",\"channel\":3,\"service\":\"FEED\","
                + "\"parameters\":{\"contract\":\"STREAM\"}}")
            .send("{\"type\":\"FEED_CONFIG\",\"channel\":3,\"aggregationPeriod\":10.0,"
                + "\"dataFormat\":\"COMPACT\",\"eventFields\":{\"TimeAndSale\":[\"eventSymbol\",\"price\"]}}")
            .expectType("FEED_SETUP", "\"channel\":3")
            .expectType("FEED_SUBSCRIPTION", "\"channel\":3", "\"type\":\"TimeAndSale\"")
            // Adapter JMX setRequestedAggregationPeriod("5s") → FEED_SETUP=5 on both channels
            .expectType("FEED_SETUP", "\"channel\":1", "\"acceptAggregationPeriod\":5.0")
            .expectType("FEED_SETUP", "\"channel\":3", "\"acceptAggregationPeriod\":5.0")
            .send("{\"type\":\"FEED_CONFIG\",\"channel\":1,\"aggregationPeriod\":5.0,"
                + "\"dataFormat\":\"COMPACT\",\"eventFields\":{\"Quote\":[\"eventSymbol\",\"bidPrice\",\"askPrice\"]}}")
            .send("{\"type\":\"FEED_CONFIG\",\"channel\":3,\"aggregationPeriod\":5.0,"
                + "\"dataFormat\":\"COMPACT\",\"eventFields\":{\"TimeAndSale\":[\"eventSymbol\",\"price\"]}}")
            // Adapter JMX reset → FEED_SETUP=NaN on both, server restores defaults
            .expectType("FEED_SETUP", "\"channel\":1", "\"acceptAggregationPeriod\":\"NaN\"")
            .expectType("FEED_SETUP", "\"channel\":3", "\"acceptAggregationPeriod\":\"NaN\"")
            .send("{\"type\":\"FEED_CONFIG\",\"channel\":1,\"aggregationPeriod\":3.0,"
                + "\"dataFormat\":\"COMPACT\",\"eventFields\":{\"Quote\":[\"eventSymbol\",\"bidPrice\",\"askPrice\"]}}")
            .send("{\"type\":\"FEED_CONFIG\",\"channel\":3,\"aggregationPeriod\":10.0,"
                + "\"dataFormat\":\"COMPACT\",\"eventFields\":{\"TimeAndSale\":[\"eventSymbol\",\"price\"]}}")
            // DXEndpoint.setRequestedAggregationPeriod("7s") → FEED_SETUP=7 on both
            .expectType("FEED_SETUP", "\"channel\":1", "\"acceptAggregationPeriod\":7.0")
            .expectType("FEED_SETUP", "\"channel\":3", "\"acceptAggregationPeriod\":7.0")
            .send("{\"type\":\"FEED_CONFIG\",\"channel\":1,\"aggregationPeriod\":7.0,"
                + "\"dataFormat\":\"COMPACT\",\"eventFields\":{\"Quote\":[\"eventSymbol\",\"bidPrice\",\"askPrice\"]}}")
            .send("{\"type\":\"FEED_CONFIG\",\"channel\":3,\"aggregationPeriod\":7.0,"
                + "\"dataFormat\":\"COMPACT\",\"eventFields\":{\"TimeAndSale\":[\"eventSymbol\",\"price\"]}}")
            // DXEndpoint reset → FEED_SETUP=NaN on both
            .expectType("FEED_SETUP", "\"channel\":1", "\"acceptAggregationPeriod\":\"NaN\"")
            .expectType("FEED_SETUP", "\"channel\":3", "\"acceptAggregationPeriod\":\"NaN\"")
            .send("{\"type\":\"FEED_CONFIG\",\"channel\":1,\"aggregationPeriod\":3.0,"
                + "\"dataFormat\":\"COMPACT\",\"eventFields\":{\"Quote\":[\"eventSymbol\",\"bidPrice\",\"askPrice\"]}}")
            .send("{\"type\":\"FEED_CONFIG\",\"channel\":3,\"aggregationPeriod\":10.0,"
                + "\"dataFormat\":\"COMPACT\",\"eventFields\":{\"TimeAndSale\":[\"eventSymbol\",\"price\"]}}")
            .start();

        endpoint = DXEndpoint.create(DXEndpoint.Role.FEED).executor(Executors.newSingleThreadExecutor());
        endpoint.getFeed().createSubscription(Quote.class).addSymbols("AAPL");
        endpoint.getFeed().createSubscription(TimeAndSale.class).addSymbols("AAPL");
        endpoint.connect("dxlink:" + server.url());

        awaitAggregationPeriodInfo(endpoint, "3s", "10s");

        MessageAdapterMBean adapter = adapterMBean(endpoint);
        adapter.setRequestedAggregationPeriod("5s");
        awaitAggregationPeriodInfo(endpoint, "5s", "5s");

        adapter.setRequestedAggregationPeriod("undefined");
        awaitAggregationPeriodInfo(endpoint, "3s", "10s");

        endpoint.setRequestedAggregationPeriod(TimePeriod.valueOf("7s"));
        awaitAggregationPeriodInfo(endpoint, "7s", "7s");

        endpoint.setRequestedAggregationPeriod(null);
        awaitAggregationPeriodInfo(endpoint, "3s", "10s");

        server.awaitScriptCompleted(10_000);
    }

    private void awaitAggregationPeriodInfo(DXEndpoint endpoint, String min, String max) {
        long expectedMin = TimePeriod.valueOf(min).getTime();
        long expectedMax = TimePeriod.valueOf(max).getTime();
        long deadline = System.currentTimeMillis() + 5_000;
        long lastMin = Long.MIN_VALUE;
        long lastMax = Long.MIN_VALUE;
        while (System.currentTimeMillis() < deadline) {
            lastMin = endpoint.getAggregationPeriodInfo().getMin();
            lastMax = endpoint.getAggregationPeriodInfo().getMax();
            if (lastMin == expectedMin && lastMax == expectedMax)
                return;
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted while awaiting AggregationPeriodInfo");
            }
        }
        fail("AggregationPeriodInfo did not reach {" + min + ", " + max + "} within 5s, last={"
            + lastMin + "ms, " + lastMax + "ms}; server received=" + server.received());
    }

    private static MessageAdapterMBean adapterMBean(DXEndpoint endpoint) throws Exception {
        List<MessageConnector> connectors = ((DXEndpointImpl) endpoint).getQDEndpoint().getConnectors();
        if (connectors.isEmpty())
            throw new IllegalStateException("no connectors on endpoint");
        MessageConnector connector = connectors.get(0);
        Method getter = AbstractMessageConnector.class.getDeclaredMethod("getMessageAdapters");
        getter.setAccessible(true);
        long deadline = System.currentTimeMillis() + 5_000;
        while (System.currentTimeMillis() < deadline) {
            @SuppressWarnings("unchecked")
            List<MessageAdapter> adapters = (List<MessageAdapter>) getter.invoke(connector);
            if (!adapters.isEmpty())
                return adapters.get(0);
            Thread.sleep(25);
        }
        throw new IllegalStateException("connector did not produce a MessageAdapter within 5s");
    }
}
