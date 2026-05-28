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
package com.devexperts.qd.dxlink.websocket.application;

import com.devexperts.qd.dxlink.websocket.application.DxLinkJsonMessageFactory.FeedSetupJsonMessage;
import com.devexperts.qd.dxlink.websocket.application.DxLinkJsonMessageFactory.FeedSubscriptionJsonMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

/**
 * Verifies the exact on-wire JSON produced by {@link DxLinkJsonMessageFactory} for every message
 * type, including the streaming FEED_SETUP-with-fields and FEED_SUBSCRIPTION builders. Each test
 * asserts byte-for-byte equality with a literal expected string — this is the regression suite for
 * the wire format and for the lifecycle of {@link DxLinkJsonMessageFactory.JsonMessage}.
 */
public class DxLinkJsonMessageFactoryTest {
    private static final ByteBufAllocator ALLOC = UnpooledByteBufAllocator.DEFAULT;

    private final DxLinkJsonMessageFactory factory = new DxLinkJsonMessageFactory(ALLOC);

    // ---- one-shot messages

    @Test
    public void testCreateSetup_minimal() throws IOException {
        ByteBuf buf = factory.createSetup(0, "0.1", null, null, null);
        assertJson("{\"type\":\"SETUP\",\"channel\":0,\"version\":\"0.1\"}", buf);
    }

    @Test
    public void testCreateSetup_full() throws IOException {
        Map<String, String> agent = new LinkedHashMap<>();
        agent.put("name", "dxlink-java");
        agent.put("version", "1.0");
        ByteBuf buf = factory.createSetup(0, "0.1", 30_000L, 60_000L, agent);
        assertJson("{\"type\":\"SETUP\",\"channel\":0,\"version\":\"0.1\""
            + ",\"keepaliveTimeout\":30.0,\"acceptKeepaliveTimeout\":60.0"
            + ",\"agent\":{\"name\":\"dxlink-java\",\"version\":\"1.0\"}}", buf);
    }

    @Test
    public void testCreateAuth() throws IOException {
        ByteBuf buf = factory.createAuth(0, "secret");
        assertJson("{\"type\":\"AUTH\",\"channel\":0,\"token\":\"secret\"}", buf);
    }

    @Test
    public void testCreateKeepalive() throws IOException {
        ByteBuf buf = factory.createKeepalive(0);
        assertJson("{\"type\":\"KEEPALIVE\",\"channel\":0}", buf);
    }

    @Test
    public void testCreateChannelRequest() throws IOException {
        ByteBuf buf = factory.createChannelRequest(1, "FEED", "TICKER");
        assertJson("{\"type\":\"CHANNEL_REQUEST\",\"channel\":1,\"service\":\"FEED\""
            + ",\"parameters\":{\"contract\":\"TICKER\"}}", buf);
    }

    // ---- one-shot FEED_SETUP (no fields)

    @Test
    public void testCreateFeedSetup_noPeriod() throws IOException {
        ByteBuf buf = factory.createFeedSetup(1, null);
        assertJson("{\"type\":\"FEED_SETUP\",\"channel\":1,\"acceptAggregationPeriod\":\"NaN\"}", buf);
    }

    @Test
    public void testCreateFeedSetup_withPeriod() throws IOException {
        ByteBuf buf = factory.createFeedSetup(3, 1000L);
        assertJson("{\"type\":\"FEED_SETUP\",\"channel\":3,\"acceptAggregationPeriod\":1.0}", buf);
    }

    @Test
    public void testCreateFeedSetup_subSecondPeriod() throws IOException {
        ByteBuf buf = factory.createFeedSetup(5, 100L);
        assertJson("{\"type\":\"FEED_SETUP\",\"channel\":5,\"acceptAggregationPeriod\":0.1}", buf);
    }

    // ---- streaming FEED_SETUP with fields

    @Test
    public void testFeedSetupWithFields_emptyAfterOpen() throws IOException {
        FeedSetupJsonMessage m = factory.openFeedSetupWithFields(1, 1000L, "COMPACT");
        assertJson("{\"type\":\"FEED_SETUP\",\"channel\":1"
            + ",\"acceptAggregationPeriod\":1.0,\"acceptDataFormat\":\"COMPACT\"}", m.close());
    }

    @Test
    public void testFeedSetupWithFields_nullOptionals() throws IOException {
        FeedSetupJsonMessage m = factory.openFeedSetupWithFields(3, null, null);
        assertJson("{\"type\":\"FEED_SETUP\",\"channel\":3,\"acceptAggregationPeriod\":\"NaN\"}", m.close());
    }

    @Test
    public void testFeedSetupWithFields_singleEntry() throws IOException {
        FeedSetupJsonMessage m = factory.openFeedSetupWithFields(1, 1000L, "COMPACT");
        m.appendAcceptEventFields("Quote", Arrays.asList("eventSymbol", "bidPrice", "askPrice"));
        assertJson("{\"type\":\"FEED_SETUP\",\"channel\":1"
            + ",\"acceptAggregationPeriod\":1.0,\"acceptDataFormat\":\"COMPACT\""
            + ",\"acceptEventFields\":{\"Quote\":[\"eventSymbol\",\"bidPrice\",\"askPrice\"]}}", m.close());
    }

    @Test
    public void testFeedSetupWithFields_multipleEntries() throws IOException {
        FeedSetupJsonMessage m = factory.openFeedSetupWithFields(5, 1000L, "COMPACT");
        m.appendAcceptEventFields("Quote", Arrays.asList("eventSymbol", "bidPrice", "askPrice"));
        m.appendAcceptEventFields("Trade", Arrays.asList("eventSymbol", "price", "size"));
        m.appendAcceptEventFields("Summary", Collections.singletonList("eventSymbol"));
        assertJson("{\"type\":\"FEED_SETUP\",\"channel\":5"
            + ",\"acceptAggregationPeriod\":1.0,\"acceptDataFormat\":\"COMPACT\""
            + ",\"acceptEventFields\":{"
            + "\"Quote\":[\"eventSymbol\",\"bidPrice\",\"askPrice\"],"
            + "\"Trade\":[\"eventSymbol\",\"price\",\"size\"],"
            + "\"Summary\":[\"eventSymbol\"]}}", m.close());
    }

    @Test
    public void testFeedSetupWithFields_emptyFieldList() throws IOException {
        FeedSetupJsonMessage m = factory.openFeedSetupWithFields(3, 1000L, "COMPACT");
        m.appendAcceptEventFields("Quote", Collections.<String>emptyList());
        m.appendAcceptEventFields("Trade", Arrays.asList("eventSymbol", "price"));
        assertJson("{\"type\":\"FEED_SETUP\",\"channel\":3"
            + ",\"acceptAggregationPeriod\":1.0,\"acceptDataFormat\":\"COMPACT\""
            + ",\"acceptEventFields\":{\"Quote\":[],\"Trade\":[\"eventSymbol\",\"price\"]}}", m.close());
    }

    // ---- streaming FEED_SUBSCRIPTION

    @Test
    public void testFeedSubscription_emptyAdd() throws IOException {
        FeedSubscriptionJsonMessage m = factory.openFeedSubscription(1, false);
        assertJson("{\"type\":\"FEED_SUBSCRIPTION\",\"channel\":1,\"reset\":false,\"add\":[]}", m.close());
    }

    @Test
    public void testFeedSubscription_emptyRemove() throws IOException {
        FeedSubscriptionJsonMessage m = factory.openFeedSubscription(3, true);
        assertJson("{\"type\":\"FEED_SUBSCRIPTION\",\"channel\":3,\"reset\":false,\"remove\":[]}", m.close());
    }

    @Test
    public void testFeedSubscription_addSingleItem_typeSymbolOnly() throws IOException {
        FeedSubscriptionJsonMessage m = factory.openFeedSubscription(1, false);
        m.appendSubscription(sub("Quote", "AAPL"));
        assertJson("{\"type\":\"FEED_SUBSCRIPTION\",\"channel\":1,\"reset\":false"
            + ",\"add\":[{\"type\":\"Quote\",\"symbol\":\"AAPL\"}]}", m.close());
    }

    @Test
    public void testFeedSubscription_addSingleItem_withSource() throws IOException {
        FeedSubscriptionJsonMessage m = factory.openFeedSubscription(1, false);
        m.appendSubscription(sub("Order", "AAPL", "NTV"));
        assertJson("{\"type\":\"FEED_SUBSCRIPTION\",\"channel\":1,\"reset\":false"
            + ",\"add\":[{\"type\":\"Order\",\"symbol\":\"AAPL\",\"source\":\"NTV\"}]}", m.close());
    }

    @Test
    public void testFeedSubscription_addSingleItem_withFromTime() throws IOException {
        FeedSubscriptionJsonMessage m = factory.openFeedSubscription(5, false);
        m.appendSubscription(sub("Candle", "AAPL{=d}", 1_700_000_000_000L));
        assertJson("{\"type\":\"FEED_SUBSCRIPTION\",\"channel\":5,\"reset\":false"
            + ",\"add\":[{\"type\":\"Candle\",\"symbol\":\"AAPL{=d}\",\"fromTime\":1700000000000}]}",
            m.close());
    }

    @Test
    public void testFeedSubscription_addMultipleItems_mixed() throws IOException {
        FeedSubscriptionJsonMessage m = factory.openFeedSubscription(3, false);
        m.appendSubscription(sub("Quote", "IBM"));
        m.appendSubscription(sub("Order", "AAPL", "NTV"));
        m.appendSubscription(sub("Candle", "AAPL{=d}", 1_700_000_000_000L));
        m.appendSubscription(sub("Trade", "MSFT"));
        assertJson("{\"type\":\"FEED_SUBSCRIPTION\",\"channel\":3,\"reset\":false"
            + ",\"add\":[{\"type\":\"Quote\",\"symbol\":\"IBM\"}"
            + ",{\"type\":\"Order\",\"symbol\":\"AAPL\",\"source\":\"NTV\"}"
            + ",{\"type\":\"Candle\",\"symbol\":\"AAPL{=d}\",\"fromTime\":1700000000000}"
            + ",{\"type\":\"Trade\",\"symbol\":\"MSFT\"}]}", m.close());
    }

    @Test
    public void testFeedSubscription_removeSingleItem() throws IOException {
        FeedSubscriptionJsonMessage m = factory.openFeedSubscription(1, true);
        m.appendSubscription(sub("Quote", "AAPL"));
        assertJson("{\"type\":\"FEED_SUBSCRIPTION\",\"channel\":1,\"reset\":false"
            + ",\"remove\":[{\"type\":\"Quote\",\"symbol\":\"AAPL\"}]}", m.close());
    }

    @Test
    public void testFeedSubscription_removeMultipleItems() throws IOException {
        FeedSubscriptionJsonMessage m = factory.openFeedSubscription(5, true);
        m.appendSubscription(sub("Order", "AAPL", "NTV"));
        m.appendSubscription(sub("Candle", "AAPL{=d}", 100L));
        assertJson("{\"type\":\"FEED_SUBSCRIPTION\",\"channel\":5,\"reset\":false"
            + ",\"remove\":[{\"type\":\"Order\",\"symbol\":\"AAPL\",\"source\":\"NTV\"}"
            + ",{\"type\":\"Candle\",\"symbol\":\"AAPL{=d}\",\"fromTime\":100}]}", m.close());
    }

    // ---- liveness of readableBytes() during streaming

    @Test
    public void testReadableBytes_growsAfterEachAppend() throws IOException {
        FeedSubscriptionJsonMessage m = factory.openFeedSubscription(1, false);
        int afterOpen = m.readableBytes();
        m.appendSubscription(sub("Quote", "AAPL"));
        int afterOne = m.readableBytes();
        m.appendSubscription(sub("Trade", "IBM"));
        int afterTwo = m.readableBytes();
        ByteBuf buf = m.close();
        int finalBytes = buf.readableBytes();
        buf.release();
        // Each append must add at least one item; close must add the trailing ']}'.
        assertNotEquals("appendSubscription did not advance writerIndex", afterOpen, afterOne);
        assertNotEquals("second appendSubscription did not advance writerIndex", afterOne, afterTwo);
        assertNotEquals("close did not add trailing tokens", afterTwo, finalBytes);
    }

    // ---- lifecycle

    @Test
    public void testAbort_releasesBuffer() throws IOException {
        FeedSubscriptionJsonMessage m = factory.openFeedSubscription(1, false);
        ByteBuf buf = m.buf;
        m.appendSubscription(sub("Quote", "AAPL"));
        m.abort();
        assertEquals("abort() must release the buffer", 0, buf.refCnt());
    }

    @Test
    public void testAbort_idempotent() throws IOException {
        FeedSubscriptionJsonMessage m = factory.openFeedSubscription(1, false);
        ByteBuf buf = m.buf;
        m.abort();
        m.abort(); // must not double-release
        assertEquals(0, buf.refCnt());
    }

    @Test
    public void testAbortAfterClose_isNoOp() throws IOException {
        FeedSubscriptionJsonMessage m = factory.openFeedSubscription(1, false);
        ByteBuf buf = m.close();
        // Ownership now with caller; the message must not release the buffer on abort().
        assertEquals(1, buf.refCnt());
        m.abort();
        assertEquals("abort() after close() must not affect the transferred buffer", 1, buf.refCnt());
        buf.release();
    }

    @Test
    public void testDoubleClose_throws() throws IOException {
        FeedSubscriptionJsonMessage m = factory.openFeedSubscription(1, false);
        ByteBuf buf = m.close();
        try {
            m.close();
            fail("second close() must throw");
        } catch (IllegalStateException expected) {
            // ok
        } finally {
            buf.release();
        }
    }

    // ---- helpers

    private static DxLinkSubscription sub(String type, String symbol) {
        return DxLinkSubscription.createSubscription(type, symbol, null);
    }

    private static DxLinkSubscription sub(String type, String symbol, String source) {
        return DxLinkSubscription.createSubscription(type, symbol, source);
    }

    private static DxLinkSubscription sub(String type, String symbol, long fromTime) {
        return DxLinkSubscription.createTimeSeriesSubscription(type, symbol, fromTime);
    }

    private static void assertJson(String expected, ByteBuf buf) {
        try {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.getBytes(buf.readerIndex(), bytes);
            assertEquals(expected, new String(bytes, StandardCharsets.UTF_8));
        } finally {
            buf.release();
        }
    }
}
