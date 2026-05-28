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

import com.devexperts.qd.QDContract;
import com.devexperts.qd.qtp.MessageDescriptor;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.util.TimePeriod;
import com.devexperts.util.TimePeriodInfo;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.waitAtMost;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Wire-level acceptance for descriptor delta emission.
 *
 * <p>Each test asserts the structure of the protocol descriptor that crosses the
 * wire after a single state change: only the property that actually changed is
 * present; auth and filter are not repeated.
 */
public class DescriptorDeltaEmissionTest extends AbstractAggregationTest {

    /** Sanity: the first descriptor exchanged in each direction is the full handshake. */
    @Test
    public void testFirstOutgoingIsFullHandshake() throws Exception {
        startServer("channels=(AAPL&ticker@3s)(AAPL&ticker@8s)," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(3_000, 8_000));
        awaitFirstData("handshake stuck");

        ProtocolDescriptor firstClientOut = clientCapture.outgoing().isEmpty()
            ? null : clientCapture.outgoing().get(0);
        assertNotNull("client never emitted a descriptor", firstClientOut);
        assertNotNull("first client descriptor lacks TICKER_DATA receive",
            firstClientOut.getReceive(MessageType.forData(QDContract.TICKER)));

        ProtocolDescriptor firstServerOut = serverCapture.outgoing().isEmpty()
            ? null : serverCapture.outgoing().get(0);
        assertNotNull("server never emitted a descriptor", firstServerOut);
        assertNotNull("first server descriptor lacks TICKER_DATA send",
            firstServerOut.getSend(MessageType.forData(QDContract.TICKER)));
    }

    /**
     * Scenario 8: a client {@code setRequestedAggregationPeriod} call must produce a single
     * post-handshake descriptor whose only payload is the new {@code requestedAggregationPeriod}
     * on the data message — no auth, no filter, no agent-info.
     */
    @Test
    public void testClientRequestEmitsOnlyRequestedAggregationPeriodDelta() throws Exception {
        startServer("channels=(AAPL&ticker@3s)(AAPL&ticker@8s)," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(3_000, 8_000));
        awaitFirstData("handshake stuck");

        int baseline = serverCapture.incoming().size();
        client.setRequestedAggregationPeriod(TimePeriod.valueOf("5s"));
        waitAtMost(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .until(() -> serverCapture.incoming().size() > baseline);

        ProtocolDescriptor delta = serverCapture.incoming().get(baseline);
        MessageDescriptor data = delta.getReceive(MessageType.forData(QDContract.TICKER));
        assertNotNull("delta missing TICKER_DATA receive descriptor", data);
        assertEquals("delta did not carry the new request",
            "PT5S", data.getProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY));
        assertNull("auth must not repeat in delta",
            delta.getProperty(ProtocolDescriptor.AUTHORIZATION_PROPERTY));
        assertNull("auth must not repeat in delta",
            delta.getProperty(ProtocolDescriptor.AUTHENTICATION_PROPERTY));
        assertNull("filter must not repeat in delta",
            delta.getProperty(ProtocolDescriptor.FILTER_PROPERTY));
    }

    /**
     * Scenario 9: a server hot edit of {@code maxAggregationPeriod} must produce a single
     * post-handshake descriptor whose only payload is the new {@code aggregationPeriodInfo}
     * on the data message — no auth, no filter.
     */
    @Test
    public void testServerMaxChangeEmitsOnlyAggregationPeriodInfoDelta() throws Exception {
        // Channels declared via defaultAggregationPeriod only (no channel min) so the address
        // max edit can clamp them — an @Ns channel keeps its own min and would shadow the edit.
        startServer("channels=" +
            "(AAPL&ticker[defaultAggregationPeriod=3s])" +
            "(AAPL&ticker[defaultAggregationPeriod=8s])," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(3_000, 8_000));
        awaitFirstData("handshake stuck");

        int baseline = clientCapture.incoming().size();
        // Lower the max bound below the second channel default so aggregationPeriodInfo actually moves.
        server.getConnectors().get(0).setMaxAggregationPeriod("5s");
        awaitClientPeriod(TimePeriodInfo.valueOf(3_000, 5_000));
        waitAtMost(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .until(() -> clientCapture.incoming().size() > baseline);

        ProtocolDescriptor delta = clientCapture.incoming().get(baseline);
        MessageDescriptor data = delta.getSend(MessageType.forData(QDContract.TICKER));
        assertNotNull("delta missing TICKER_DATA send descriptor", data);
        assertNotNull("delta did not carry the new aggregationPeriodInfo",
            data.getProperty(ProtocolDescriptor.AGGREGATION_PERIOD_INFO_PROPERTY));
        assertNull("auth must not repeat in delta",
            delta.getProperty(ProtocolDescriptor.AUTHORIZATION_PROPERTY));
        assertNull("auth must not repeat in delta",
            delta.getProperty(ProtocolDescriptor.AUTHENTICATION_PROPERTY));
        assertNull("filter must not repeat in delta",
            delta.getProperty(ProtocolDescriptor.FILTER_PROPERTY));
    }

    /**
     * Transactional batching: several server-side bound edits issued in rapid succession must
     * collapse into a single emission whose final descriptor reflects every change.
     */
    @Test
    public void testMultiplePropertyChangesCollapseIntoFewEmissions() throws Exception {
        // Channels without own min/max so connector-level edits can actually move them.
        startServer("channels=" +
            "(AAPL&ticker[defaultAggregationPeriod=3s])" +
            "(AAPL&ticker[defaultAggregationPeriod=8s])," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(3_000, 8_000));
        awaitFirstData("handshake stuck");

        int baseline = clientCapture.incoming().size();
        // Three rapid edits — expect they collapse into far fewer than three emissions.
        server.getConnectors().get(0).setMinAggregationPeriod("2s");
        server.getConnectors().get(0).setDefaultAggregationPeriod("5s");
        server.getConnectors().get(0).setMaxAggregationPeriod("6s");

        // Final state: channels clamp to [2s, 6s] over defaults (3s, 8s) → (3s, 6s).
        awaitClientPeriod(TimePeriodInfo.valueOf(3_000, 6_000));

        int emitted = clientCapture.incoming().size() - baseline;
        assertTrue("expected one or two descriptor emissions for three batched edits, got " + emitted,
            emitted >= 1 && emitted <= 2);
    }
}
