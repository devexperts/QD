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

import com.devexperts.util.TimePeriod;
import com.devexperts.util.TimePeriodInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Integration test: socket-based server + client at the QDEndpoint level with QTP
 * authentication, combined with live JMX-style propagation of aggregation period bounds.
 */
public class AuthAndAggregationLiveUpdateTest extends AbstractAggregationTest {

    @Test
    public void testTokenDrivenAggregationPeriod() throws Exception {
        CountingRealm realm = counting(tokenChannelsRealm("AAPL&ticker@1s", "AAPL&ticker@15s"));
        startServer("channels=(AAPL&ticker@7s)(AAPL&ticker@12s)," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            realm);
        startClient("", validLoginHandler());

        // ch@1s: eff=[1,10], period=1. ch@15s: min=15>connectorMax=10 → eff_max:=15 (min wins), period=15.
        awaitClientPeriod(TimePeriodInfo.valueOf(1_000, 15_000));
        awaitFirstData("handshake stuck");

        client.setRequestedAggregationPeriod(TimePeriod.valueOf("5s"));
        // ch@1s: 5s ∈ [1,10] → 5. ch@15s: 5<min=15 → 15.
        awaitClientPeriod(TimePeriodInfo.valueOf(5_000, 15_000));

        client.setRequestedAggregationPeriod(null);
        awaitClientPeriod(TimePeriodInfo.valueOf(1_000, 15_000));

        server.getConnectors().get(0).setMaxAggregationPeriod("20s");
        // ch@1s: eff_max=20, period=1. ch@15s: eff_max=20, min=15, period=15.
        awaitClientPeriod(TimePeriodInfo.valueOf(1_000, 15_000));

        assertEquals(1, realm.calls.get());
    }

    @Test
    public void testConnectorDrivenAggregationPeriod() throws Exception {
        CountingRealm realm = counting(unrestrictedRealm());
        startServer("channels=(AAPL&ticker@7s)(AAPL&ticker@12s)," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            realm);
        startClient("", validLoginHandler());

        // ch@7s: eff=[7,10], period=7. ch@12s: min=12>connectorMax=10 → eff_max:=12 (min wins), period=12.
        awaitClientPeriod(TimePeriodInfo.valueOf(7_000, 12_000));
        awaitFirstData("handshake stuck");

        client.setRequestedAggregationPeriod(TimePeriod.valueOf("5s"));
        // 5s < both channel mins → each raised to its own min.
        awaitClientPeriod(TimePeriodInfo.valueOf(7_000, 12_000));

        client.setRequestedAggregationPeriod(TimePeriod.valueOf("8s"));
        // ch@7s: 8s ∈ [7,10] → 8. ch@12s: 8<min=12 → 12.
        awaitClientPeriod(TimePeriodInfo.valueOf(8_000, 12_000));

        client.setRequestedAggregationPeriod(null);
        awaitClientPeriod(TimePeriodInfo.valueOf(7_000, 12_000));

        server.getConnectors().get(0).setMaxAggregationPeriod("20s");
        awaitClientPeriod(TimePeriodInfo.valueOf(7_000, 12_000));

        assertEquals(1, realm.calls.get());
    }

    /**
     * {@code setDefaultAggregationPeriod(null)} reverts the runtime override back to the
     * address-parsed baseline carried by the adapter's original factory.
     */
    @Test
    public void testDefaultAggregationPeriodNullReset() throws Exception {
        startServer("channels=(AAPL&ticker)," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(4_000, 4_000));
        awaitFirstData("handshake stuck");

        server.getConnectors().get(0).setDefaultAggregationPeriod("8s");
        awaitClientPeriod(TimePeriodInfo.valueOf(8_000, 8_000));

        server.getConnectors().get(0).setDefaultAggregationPeriod(null);
        awaitClientPeriod(TimePeriodInfo.valueOf(4_000, 4_000));
    }

    /**
     * Reset spellings accepted by {@code parseAggregationPeriodString}: {@code null}, empty,
     * {@code "-1"}, {@code "undefined"} — all revert the connector default to the baseline.
     */
    @Test
    public void testDefaultAggregationPeriodResetSpellings() throws Exception {
        startServer("channels=(AAPL&ticker)," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(4_000, 4_000));
        awaitFirstData("handshake stuck");

        for (String reset : new String[] {"", "-1", "undefined"}) {
            server.getConnectors().get(0).setDefaultAggregationPeriod("8s");
            awaitClientPeriod(TimePeriodInfo.valueOf(8_000, 8_000));

            server.getConnectors().get(0).setDefaultAggregationPeriod(reset);
            awaitClientPeriod(TimePeriodInfo.valueOf(4_000, 4_000));
        }
    }
}
