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
package com.devexperts.qd.qtp.test;

import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.ChannelShaper;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageDescriptor;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.test.TestDataScheme;
import com.devexperts.test.TraceRunner;
import com.devexperts.util.TimePeriod;
import com.devexperts.util.TimePeriodInfo;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * Tests server-side aggregation period negotiation in {@link AgentAdapter}.
 *
 * <p>Flow: client sends {@code requestedAggregationPeriod} property inside DESCRIBE_PROTOCOL →
 * server clamps to {@code [min, max]} bounds → server responds with {@code aggregationPeriodInfo}.
 */
@RunWith(TraceRunner.class)
public class AggregationPeriodNegotiationTest {

    private static final TestDataScheme SCHEME = new TestDataScheme();

    // ==================== Clamping ====================

    @Test
    public void testClampToMin() {
        AgentAdapter server = createServer("0.5s", null, null); // min=0.5s
        process(server, "0.1s");                                // client requests 0.1s
        assertPeriod(500, server);                              // clamped up to 0.5s
    }

    @Test
    public void testClampToMax() {
        AgentAdapter server = createServer(null, "5s", null);   // max=5s
        process(server, "10s");                                 // client requests 10s
        assertPeriod(5000, server);                             // clamped down to 5s
    }

    @Test
    public void testWithinRange() {
        AgentAdapter server = createServer("1s", "5s", null);   // [1s, 5s]
        process(server, "2s");                                  // client requests 2s
        assertPeriod(2000, server);                             // passes through
    }

    @Test
    public void testUnlimitedRequestClampedTo24h() {
        AgentAdapter server = createServer(null, null, null);   // default bounds
        process(server, "inf");                                 // client requests infinity
        assertPeriod(ChannelShaper.AGGREGATION_PERIOD_MAX_VALUE, server); // clamped to 24h
    }

    // ==================== Default / reset ====================

    @Test
    public void testServerDefaultWhenNoRequest() {
        AgentAdapter server = createServer(null, null, "2s");   // default=2s
        process(server, null);                                  // client sends no request
        assertPeriod(2000, server);                             // server applies its default
    }

    @Test
    public void testClientResetsToDefault() {
        AgentAdapter server = createServer("0.5s", "10s", "3s"); // [0.5s, 10s], default=3s

        process(server, "1s");        // client requests 1s
        assertPeriod(1000, server);

        process(server, null);        // client resets (no property)
        assertPeriod(3000, server);   // falls back to server default
    }

    // ==================== Descriptor property flow ====================

    /**
     * Verifies the full lifecycle of the requestedAggregationPeriod property in the descriptor:
     * <ol>
     *   <li>null → property absent in descriptor → server uses default</li>
     *   <li>"1s" → property present → server applies 1s and responds with info</li>
     *   <li>null → property absent again → server falls back to default</li>
     * </ol>
     */
    @Test
    public void testRequestedPeriodPropertyLifecycle() {
        AgentAdapter server = createServer(null, null, "5s");   // default=5s

        // Step 1: null → not transferred → server uses default
        ProtocolDescriptor desc1 = clientDescriptor(null);
        MessageDescriptor msg1 = desc1.getReceive(MessageType.TICKER_DATA);
        assertNull("null request should not produce property",
            msg1.getProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY));

        server.processDescribeProtocol(desc1, false);
        assertPeriod(5000, server);   // default applied

        // Step 2: "1s" → transferred → server applies and responds
        ProtocolDescriptor desc2 = clientDescriptor("1s");
        MessageDescriptor msg2 = desc2.getReceive(MessageType.TICKER_DATA);
        assertEquals("1s", msg2.getProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY));

        server.processDescribeProtocol(desc2, false);
        assertPeriod(1000, server);

        // Verify server response contains aggregationPeriodInfo
        ProtocolDescriptor response = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        server.prepareProtocolDescriptor(response);
        MessageDescriptor tickerSend = response.getSend(MessageType.TICKER_DATA);
        assertNotNull("server should send TICKER_DATA", tickerSend);
        assertEquals(TimePeriodInfo.valueOf(1000, 1000).toString(),
            tickerSend.getProperty(ProtocolDescriptor.AGGREGATION_PERIOD_INFO_PROPERTY));

        // Step 3: null → not transferred → server falls back to default
        ProtocolDescriptor desc3 = clientDescriptor(null);
        server.processDescribeProtocol(desc3, false);
        assertPeriod(5000, server);   // default again
    }

    /**
     * Verifies that resetting requestedAggregationPeriod works through the protocol accumulation path.
     *
     * <p>In production, the parser accumulates descriptors via {@link ProtocolDescriptor#newPeerProtocolDescriptor}
     * + {@code mergeOrAdd(putAll)}. Since {@code putAll} cannot remove keys, the only way to clear a
     * previously-set property is to send it as empty string. This test simulates the accumulation path
     * and verifies that empty string correctly resets to server default.
     */
    @Test
    public void testResetThroughAccumulatedDescriptor() {
        AgentAdapter server = createServer(null, null, "5s"); // default=5s

        // Step 1: client sends "1s" → server applies 1s
        ProtocolDescriptor desc1 = clientDescriptor("1s");
        server.processDescribeProtocol(desc1, false);
        assertPeriod(1000, server);

        // Step 2: simulate parser accumulation — new descriptor inherits old state
        ProtocolDescriptor accumulated = ProtocolDescriptor.newPeerProtocolDescriptor(desc1);
        // Merge a new descriptor with empty string (the fix: DistributorAdapter now sends "")
        MessageDescriptor resetMsg = accumulated.newMessageDescriptor(MessageType.TICKER_DATA);
        resetMsg.setProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY, "");
        accumulated.addReceive(resetMsg);

        server.processDescribeProtocol(accumulated, false);
        assertPeriod(5000, server); // reset to server default
    }

    /**
     * Server changes bounds at runtime → active period re-resolved without reconnection.
     */
    @Test
    public void testServerBoundsChangeReResolution() {
        AgentAdapter server = createServer(null, "10s", null);  // max=10s
        process(server, "8s");
        assertPeriod(8000, server);

        server.setMaxAggregationPeriod(TimePeriod.valueOf("5s")); // shrink max to 5s
        assertPeriod(5000, server);   // re-clamped without re-request
    }

    /**
     * Defaults must be pushed into shapers at construction / {@link AgentAdapter#setDefaultAggregationPeriod}
     * time, even when DESCRIBE_PROTOCOL never arrives. {@code synchronizeAggregationPeriods()} actively
     * writes the default into each shaper.
     */
    @Test
    public void testDefaultsPushedWithoutDescribeProtocol() {
        AgentAdapter server = createServer(null, null, "3s");   // default=3s
        // No processDescribeProtocol call: the default must already be visible via info.
        assertPeriod(3000, server);
    }

    /**
     * A malformed {@code requestedAggregationPeriod} from the client must not tear down the connection.
     * Server logs a warning and falls back to its default for that contract.
     */
    @Test
    public void testMalformedRequestedPeriodFallsBackToDefault() {
        AgentAdapter server = createServer(null, null, "2s");   // default=2s
        process(server, "not-a-period");
        // Parse failed → treated as "no request" → server default applies.
        assertPeriod(2000, server);
    }

    // ==================== Interop & round-trip (NEW5) ====================

    /**
     * Full AgentAdapter ↔ DistributorAdapter round-trip: client sends request, server applies
     * and returns its info via prepareProtocolDescriptor, distributor parses the info back into
     * its own {@code aggregationPeriodInfo}.
     */
    @Test
    public void testRoundTripThroughDistributorAdapter() {
        AgentAdapter server = createServer("0.5s", "5s", null); // [0.5s, 5s]
        DistributorAdapter client = createClient();

        // Client → Server: descriptor carrying requestedAggregationPeriod=1s.
        ProtocolDescriptor clientDesc = clientDescriptor("1s");
        server.processDescribeProtocol(clientDesc, false);
        assertPeriod(1000, server);

        // Server → Client: server emits its descriptor with aggregationPeriodInfo on SEND DATA msgs.
        ProtocolDescriptor serverResponse = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        server.prepareProtocolDescriptor(serverResponse);

        // The distributor reads the remote's SEND DATA messages; forward them verbatim.
        ProtocolDescriptor descForDistributor = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        for (MessageDescriptor msg : serverResponse.getSendMessages()) {
            MessageDescriptor copy = descForDistributor.newMessageDescriptor(MessageType.findById(msg.getId()));
            copy.setProperty(ProtocolDescriptor.AGGREGATION_PERIOD_INFO_PROPERTY,
                msg.getProperty(ProtocolDescriptor.AGGREGATION_PERIOD_INFO_PROPERTY));
            descForDistributor.addSend(copy);
        }
        client.processDescribeProtocol(descForDistributor, false);
        assertEquals(TimePeriodInfo.valueOf(1000, 1000), client.getAggregationPeriodInfo());
    }

    /**
     * Legacy client (no {@code requestedAggregationPeriod} property in DESCRIBE_PROTOCOL) against
     * a new server: the server must fall back to its default without error.
     */
    @Test
    public void testLegacyClientAgainstNewServer() {
        AgentAdapter server = createServer(null, null, "4s"); // default=4s
        // Legacy descriptor: no REQUESTED_AGGREGATION_PERIOD_PROPERTY at all.
        ProtocolDescriptor legacyDesc = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        MessageDescriptor tickerData = legacyDesc.newMessageDescriptor(MessageType.TICKER_DATA);
        legacyDesc.addReceive(tickerData);
        assertNull(tickerData.getProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY));

        server.processDescribeProtocol(legacyDesc, false);
        assertPeriod(4000, server);
    }

    /**
     * New client against a legacy server: server response has no {@code aggregationPeriodInfo}
     * property on SEND DATA messages; distributor must observe {@link TimePeriodInfo#UNKNOWN}.
     */
    @Test
    public void testNewClientAgainstLegacyServer() {
        DistributorAdapter client = createClient();
        // Legacy server response: TICKER_DATA sent but no aggregationPeriodInfo property.
        ProtocolDescriptor legacyServerResp = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        MessageDescriptor tickerData = legacyServerResp.newMessageDescriptor(MessageType.TICKER_DATA);
        legacyServerResp.addReceive(tickerData);
        assertNull(tickerData.getProperty(ProtocolDescriptor.AGGREGATION_PERIOD_INFO_PROPERTY));

        client.processDescribeProtocol(legacyServerResp, false);
        assertEquals(TimePeriodInfo.UNKNOWN, client.getAggregationPeriodInfo());
    }

    /**
     * Validates that both DistributorAdapter setters reject periods greater than 24h (covers
     * REL1/PROJ2). Without validation, an oversized period would flow onto the wire and be
     * rejected asymmetrically by the peer.
     */
    @Test
    public void testDistributorAdapterSettersRejectOversizedPeriod() {
        TimePeriod tooBig = TimePeriod.valueOf("48h");
        QDTicker ticker = QDFactory.getDefaultFactory().createTicker(SCHEME);
        try {
            new DistributorAdapter.Factory(ticker).setRequestedAggregationPeriod(tooBig);
            fail("Factory setter should reject 48h");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            createClient().setRequestedAggregationPeriod(tooBig);
            fail("Instance setter should reject 48h");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    // ==================== Helpers ====================

    private AgentAdapter createServer(String min, String max, String defaultPeriod) {
        QDTicker ticker = QDFactory.getDefaultFactory().createTicker(SCHEME);
        AgentAdapter adapter = new AgentAdapter(null, ticker, null, null, null, null, QDStats.VOID);
        if (min != null)
            adapter.setMinAggregationPeriod(TimePeriod.valueOf(min));
        if (max != null)
            adapter.setMaxAggregationPeriod(TimePeriod.valueOf(max));
        if (defaultPeriod != null)
            adapter.setDefaultAggregationPeriod(TimePeriod.valueOf(defaultPeriod));
        return adapter;
    }

    private DistributorAdapter createClient() {
        QDTicker ticker = QDFactory.getDefaultFactory().createTicker(SCHEME);
        return new DistributorAdapter(null, ticker, null, null, null, null, QDStats.VOID, null);
    }

    private void process(AgentAdapter server, String requestedPeriod) {
        server.processDescribeProtocol(clientDescriptor(requestedPeriod), false);
    }

    private ProtocolDescriptor clientDescriptor(String requestedPeriod) {
        ProtocolDescriptor desc = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        MessageDescriptor tickerData = desc.newMessageDescriptor(MessageType.TICKER_DATA);
        if (requestedPeriod != null)
            tickerData.setProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY, requestedPeriod);
        desc.addReceive(tickerData);
        return desc;
    }

    private void assertPeriod(long expectedMillis, AgentAdapter server) {
        assertEquals(TimePeriodInfo.valueOf(expectedMillis, expectedMillis).toString(),
            server.getAggregationPeriodInfoStr());
    }
}
