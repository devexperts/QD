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

/**
 * Acceptance scenarios for the aggregation period bounds resolution.
 *
 * <p>Each test names the channel source mode (token / connector / default), the
 * client action under exercise, and the expected effect on
 * {@link com.devexperts.qd.qtp.MessageConnector#getAggregationPeriodInfo()}.
 *
 * <p>{@code @Ns} on a channel sets both {@code minAggregationPeriod} and
 * {@code defaultAggregationPeriod}; per-channel bounds compose with connector-level
 * bounds as {@code max(min, connectorMin)} and {@code min(max, connectorMax)}; if the
 * composed {@code min} exceeds the composed {@code max}, {@code min} wins (capped
 * at 24h).
 *
 * <p>Companion test for live updates and basic JMX propagation:
 * {@link AuthAndAggregationLiveUpdateTest}.
 */
public class AggregationPeriodScenariosTest extends AbstractAggregationTest {

    // ===== Token-driven scenarios =====

    /**
     * Scenario 1: token-driven, request below the lowest channel {@code min} — no channel moves.
     * Channel {@code @12s} keeps {@code max=12s} (channel {@code min} wins over address {@code max=10s}).
     */
    @Test
    public void testTokenDrivenRequestBelowBaselineIsNoOp() throws Exception {
        startServer("minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            tokenChannelsRealm("AAPL&ticker@7s", "AAPL&ticker@12s"));
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(7_000, 12_000));
        awaitFirstData("handshake stuck");

        client.setRequestedAggregationPeriod(TimePeriod.valueOf("3s"));
        // Request 3s < both channel mins (7s, 12s) → both raised to their mins.
        awaitClientPeriod(TimePeriodInfo.valueOf(7_000, 12_000));
    }

    /** Scenario 2: token-driven, request lifts only channels whose {@code min} is below the request. */
    @Test
    public void testTokenDrivenRequestLiftsLowerChannelsOnly() throws Exception {
        startServer("minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            tokenChannelsRealm("AAPL&ticker@3s", "AAPL&ticker@8s"));
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(3_000, 8_000));
        awaitFirstData("handshake stuck");

        client.setRequestedAggregationPeriod(TimePeriod.valueOf("6s"));
        // ch@3s: 6s ∈ [3s, 10s] → 6s. ch@8s: 6s < min=8s → raised to 8s.
        awaitClientPeriod(TimePeriodInfo.valueOf(6_000, 8_000));
    }

    // ===== Address-driven scenarios =====

    /**
     * Scenario 3a: connector-driven, request inside the channel's {@code [min, max]} window.
     * Each channel clamps the request independently to its own bounds.
     */
    @Test
    public void testConnectorDrivenRequestOverridesAtPeriod() throws Exception {
        startServer("channels=(AAPL&ticker@3s)(AAPL&ticker@8s)," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(3_000, 8_000));
        awaitFirstData("handshake stuck");

        client.setRequestedAggregationPeriod(TimePeriod.valueOf("5s"));
        // ch@3s: 5s ∈ [3s, 10s] → 5s. ch@8s: 5s < min=8s → raised to 8s.
        awaitClientPeriod(TimePeriodInfo.valueOf(5_000, 8_000));
    }

    /** Scenario 3b: connector-driven reset (empty string) — channels return to their {@code @period}. */
    @Test
    public void testConnectorDrivenResetRestoresAtPeriod() throws Exception {
        startServer("channels=(AAPL&ticker@3s)(AAPL&ticker@8s)," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(3_000, 8_000));
        awaitFirstData("handshake stuck");

        client.setRequestedAggregationPeriod(TimePeriod.valueOf("5s"));
        awaitClientPeriod(TimePeriodInfo.valueOf(5_000, 8_000));

        client.setRequestedAggregationPeriod(null);
        awaitClientPeriod(TimePeriodInfo.valueOf(3_000, 8_000));
    }

    /** Scenario 4: connector-driven, request outside bounds is clamped before being applied. */
    @Test
    public void testConnectorDrivenRequestClampedToBounds() throws Exception {
        startServer("channels=(AAPL&ticker@3s)(AAPL&ticker@8s)," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(3_000, 8_000));
        awaitFirstData("handshake stuck");

        client.setRequestedAggregationPeriod(TimePeriod.valueOf("15s"));
        // 15s clamped to connector max=10s, then applied per channel; both channel maxes resolve to 10s.
        awaitClientPeriod(TimePeriodInfo.valueOf(10_000, 10_000));
    }

    // ===== Default-mode scenario =====

    /** Scenario 5: no channels configured anywhere — one channel per collector at default period; request overrides. */
    @Test
    public void testDefaultModeRequestAppliesToAllChannels() throws Exception {
        startServer("minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(4_000, 4_000));
        awaitFirstData("handshake stuck");

        client.setRequestedAggregationPeriod(TimePeriod.valueOf("6s"));
        awaitClientPeriod(TimePeriodInfo.valueOf(6_000, 6_000));
    }

    // ===== Live bound edits =====

    /** Scenario 6: hot JMX edit of the minimum bound lifts channels currently below the new floor. */
    @Test
    public void testLiveMinChangeRecalculatesWithoutReconnect() throws Exception {
        startServer("minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            tokenChannelsRealm("AAPL&ticker@2s", "AAPL&ticker@8s"));
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(2_000, 8_000));
        awaitFirstData("handshake stuck");

        server.getConnectors().get(0).setMinAggregationPeriod("5s");
        // ch@2s lifted to new floor; ch@8s untouched.
        awaitClientPeriod(TimePeriodInfo.valueOf(5_000, 8_000));
    }

    // ===== New-syntax scenarios (per-channel min / max / default via properties) =====

    /**
     * Scenario 7: channel-level {@code maxAggregationPeriod} via properties clamps client requests
     * tighter than the connector {@code max}. {@code defaultAggregationPeriod} on the channel sets
     * the initial period; channel's own max wins when stricter than connector max.
     */
    @Test
    public void testChannelMaxFromPropertyClampsRequestBelowAddressMax() throws Exception {
        startServer("channels=(AAPL&ticker[defaultAggregationPeriod=3s,maxAggregationPeriod=5s])," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(3_000, 3_000));
        awaitFirstData("handshake stuck");

        client.setRequestedAggregationPeriod(TimePeriod.valueOf("8s"));
        // 8s clamped to channel max=5s (stricter than connector max=10s).
        awaitClientPeriod(TimePeriodInfo.valueOf(5_000, 5_000));
    }

    /**
     * Scenario 8: channel-level {@code minAggregationPeriod} via properties raises client requests
     * below it. Initial period uses the channel default (channel default overrides connector default).
     */
    @Test
    public void testChannelMinFromPropertyRaisesRequest() throws Exception {
        startServer("channels=(AAPL&ticker[minAggregationPeriod=5s,defaultAggregationPeriod=7s])," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        awaitClientPeriod(TimePeriodInfo.valueOf(7_000, 7_000));
        awaitFirstData("handshake stuck");

        client.setRequestedAggregationPeriod(TimePeriod.valueOf("2s"));
        // 2s < channel min=5s → raised to 5s.
        awaitClientPeriod(TimePeriodInfo.valueOf(5_000, 5_000));
    }

    /**
     * Scenario 9: {@code @Ns} sets both {@code min} and {@code default}; an explicit
     * {@code defaultAggregationPeriod} property after {@code @} overrides the default but
     * leaves the min from {@code @}. Initial period reflects the override; a request below
     * the min from {@code @} is raised to it.
     */
    @Test
    public void testExplicitDefaultPropertyOverridesAtSyntaxDefaultButKeepsMin() throws Exception {
        startServer("channels=(AAPL&ticker@3s[defaultAggregationPeriod=6s])," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=10s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        // min=3s (from @), default=6s (property overrides) → initial period = 6s.
        awaitClientPeriod(TimePeriodInfo.valueOf(6_000, 6_000));
        awaitFirstData("handshake stuck");

        client.setRequestedAggregationPeriod(TimePeriod.valueOf("2s"));
        // 2s < min=3s → raised to 3s.
        awaitClientPeriod(TimePeriodInfo.valueOf(3_000, 3_000));
    }

    /**
     * Scenario 10: channel {@code min > max} via properties — {@code min} wins, {@code max}
     * is overwritten to equal {@code min}. The channel collapses to a single value;
     * any request is clamped to it.
     */
    @Test
    public void testChannelMinGreaterThanChannelMaxLetsMinWin() throws Exception {
        startServer("channels=(AAPL&ticker[minAggregationPeriod=8s,maxAggregationPeriod=4s,defaultAggregationPeriod=4s])," +
            "minAggregationPeriod=1s,defaultAggregationPeriod=4s,maxAggregationPeriod=20s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        // min=8s > max=4s → max := 8s. default=4s < min=8s → period clamped to 8s.
        awaitClientPeriod(TimePeriodInfo.valueOf(8_000, 8_000));
        awaitFirstData("handshake stuck");

        client.setRequestedAggregationPeriod(TimePeriod.valueOf("12s"));
        // Channel collapsed to 8s — request can't widen it.
        awaitClientPeriod(TimePeriodInfo.valueOf(8_000, 8_000));
    }

    /**
     * Scenario 11: connector {@code min} raises the effective channel {@code min} above the
     * channel's own {@code min}. A client request below the connector {@code min} has no effect —
     * it is raised first by the connector bound and then absorbed by the channel.
     */
    @Test
    public void testAddressMinRaisesEffectiveChannelMin() throws Exception {
        startServer("channels=(AAPL&ticker[minAggregationPeriod=1s,maxAggregationPeriod=20s,defaultAggregationPeriod=4s])," +
            "minAggregationPeriod=8s,defaultAggregationPeriod=8s,maxAggregationPeriod=20s",
            unrestrictedRealm());
        startClient("", validLoginHandler());

        // Effective channel min = max(channel min=1s, connector min=8s) = 8s.
        // Channel default=4s < eff_min=8s → period clamped to 8s.
        awaitClientPeriod(TimePeriodInfo.valueOf(8_000, 8_000));
        awaitFirstData("handshake stuck");

        client.setRequestedAggregationPeriod(TimePeriod.valueOf("3s"));
        // 3s < eff_min=8s → raised to 8s.
        awaitClientPeriod(TimePeriodInfo.valueOf(8_000, 8_000));
    }
}
