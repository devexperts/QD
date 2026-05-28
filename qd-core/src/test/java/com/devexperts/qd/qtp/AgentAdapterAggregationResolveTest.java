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
package com.devexperts.qd.qtp;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Unit tests for {@link AgentAdapter#resolveChannelPeriod} — the pure aggregation-period
 * resolver. All values in milliseconds; channel and connector bounds are real positive
 * values ({@code 0} for "no lower bound", {@link ChannelShaper#AGGREGATION_PERIOD_MAX_VALUE}
 * for "no upper bound"). Only {@code requested == -1} carries a sentinel meaning ("no client
 * request, use the channel default").
 */
public class AgentAdapterAggregationResolveTest {

    private static final long NO_MIN = 0L;
    private static final long NO_MAX = ChannelShaper.AGGREGATION_PERIOD_MAX_VALUE;

    // ----- channel default and request without per-channel bounds -----

    @Test
    public void testChannelDefaultUsedWhenNoRequest() {
        // No request → target = channelDefault = 4s, inside the connector envelope.
        assertEquals(4000, AgentAdapter.resolveChannelPeriod(4000, NO_MIN, NO_MAX, -1, 1000, 10000, -1));
    }

    @Test
    public void testRequestApplied() {
        assertEquals(5000, AgentAdapter.resolveChannelPeriod(4000, NO_MIN, NO_MAX, -1, 1000, 10000, 5000));
    }

    @Test
    public void testRequestClampedToConnectorMax() {
        assertEquals(10000, AgentAdapter.resolveChannelPeriod(4000, NO_MIN, NO_MAX, -1, 1000, 10000, 15000));
    }

    @Test
    public void testRequestRaisedToConnectorMin() {
        assertEquals(1000, AgentAdapter.resolveChannelPeriod(4000, NO_MIN, NO_MAX, -1, 1000, 10000, 200));
    }

    // ----- @Ns shorthand: channel default == channel min -----

    @Test
    public void testChannelDefaultBelowChannelMinRaisesToMin() {
        // @Ns sets both default and min to N; the resolver clamps the default up to the min.
        assertEquals(8000, AgentAdapter.resolveChannelPeriod(8000, 8000, NO_MAX, -1, 1000, 10000, -1));
    }

    @Test
    public void testChannelMinAboveConnectorMaxLetsMinWin() {
        // channel_min=12s > connector_max=10s → effMax := 12s (min wins).
        assertEquals(12000, AgentAdapter.resolveChannelPeriod(12000, 12000, NO_MAX, -1, 1000, 10000, -1));
    }

    @Test
    public void testChannelMinRaisesRequestBelowIt() {
        assertEquals(8000, AgentAdapter.resolveChannelPeriod(8000, 8000, NO_MAX, -1, 1000, 10000, 5000));
    }

    @Test
    public void testRequestInsideChannelMinPasses() {
        assertEquals(6000, AgentAdapter.resolveChannelPeriod(3000, 3000, NO_MAX, -1, 1000, 10000, 6000));
    }

    // ----- channel max constrains the request below the connector max -----

    @Test
    public void testChannelMaxClampsRequestTighter() {
        assertEquals(5000, AgentAdapter.resolveChannelPeriod(3000, NO_MIN, 5000, -1, 1000, 10000, 8000));
    }

    @Test
    public void testChannelMaxLetsBelowMaxRequestThrough() {
        assertEquals(3000, AgentAdapter.resolveChannelPeriod(3000, NO_MIN, 5000, -1, 1000, 10000, -1));
    }

    // ----- min > max conflict resolution -----

    @Test
    public void testChannelMinGreaterThanChannelMaxLetsMinWin() {
        // channel_min=8 > channel_max=4 → effMax := 8; default=4 below new min, clamped to 8.
        assertEquals(8000, AgentAdapter.resolveChannelPeriod(4000, 8000, 4000, -1, 1000, 20000, -1));
    }

    @Test
    public void testChannelMinGreaterThanChannelMaxRequestStaysAtMin() {
        assertEquals(8000, AgentAdapter.resolveChannelPeriod(4000, 8000, 4000, -1, 1000, 20000, 12000));
    }

    // ----- connector bounds raise / lower effective channel bounds -----

    @Test
    public void testConnectorMinRaisesEffectiveChannelMin() {
        // channel_min=1s, connector_min=8s → effMin=8s. channel_default=4s clamped to 8s.
        assertEquals(8000, AgentAdapter.resolveChannelPeriod(4000, 1000, 20000, -1, 8000, 20000, -1));
    }

    @Test
    public void testConnectorMaxLowersEffectiveChannelMax() {
        // channel_max=20s, connector_max=10s → effMax=10s. Request 15s clamped to 10s.
        assertEquals(10000, AgentAdapter.resolveChannelPeriod(3000, 1000, 20000, -1, 1000, 10000, 15000));
    }

    // ----- connector default fills in when channel default and request are absent -----

    @Test
    public void testConnectorDefaultUsedWhenNoChannelDefaultAndNoRequest() {
        // channelDefault=-1, requested=-1 → target falls through to connectorDefault=6s.
        assertEquals(6000, AgentAdapter.resolveChannelPeriod(-1, NO_MIN, NO_MAX, 6000, 1000, 10000, -1));
    }

    @Test
    public void testChannelDefaultBeatsConnectorDefault() {
        // channelDefault=4s present → connectorDefault=6s ignored.
        assertEquals(4000, AgentAdapter.resolveChannelPeriod(4000, NO_MIN, NO_MAX, 6000, 1000, 10000, -1));
    }

    @Test
    public void testRequestBeatsConnectorDefault() {
        // requested=5s wins over both channelDefault=-1 and connectorDefault=6s.
        assertEquals(5000, AgentAdapter.resolveChannelPeriod(-1, NO_MIN, NO_MAX, 6000, 1000, 10000, 5000));
    }

    @Test
    public void testConnectorDefaultClampedToEffectiveBounds() {
        // connectorDefault=500ms below connector_min=1s → raised to 1s.
        assertEquals(1000, AgentAdapter.resolveChannelPeriod(-1, NO_MIN, NO_MAX, 500, 1000, 10000, -1));
    }

    @Test
    public void testFallbackToBuiltInDefaultWhenAllNegative() {
        // requested=-1, channelDefault=-1, connectorDefault=-1 → falls through to
        // ChannelShaper.DEFAULT_AGGREGATION_PERIOD, then clamped to effMin=0.
        long builtIn = ChannelShaper.DEFAULT_AGGREGATION_PERIOD.getTime();
        long expected = Math.max(NO_MIN, Math.min(builtIn, NO_MAX));
        assertEquals(expected, AgentAdapter.resolveChannelPeriod(-1, NO_MIN, NO_MAX, -1, NO_MIN, NO_MAX, -1));
    }

    // ----- min == max collapses to single value -----

    @Test
    public void testEqualBoundsCollapseToSinglePeriod() {
        assertEquals(5000, AgentAdapter.resolveChannelPeriod(3000, 5000, 5000, -1, 1000, 10000, -1));
        assertEquals(5000, AgentAdapter.resolveChannelPeriod(3000, 5000, 5000, -1, 1000, 10000, 9000));
    }

    // ----- 24h hard cap respected when caller provides it -----

    @Test
    public void testEffectiveMaxRespectsCallerProvidedCap() {
        long capped = ChannelShaper.AGGREGATION_PERIOD_MAX_VALUE;
        // connector_max passed in is 24h (caller of synchronizeAggregationPeriods caps it);
        // verify the resolver respects whatever ceiling the caller provides.
        assertEquals(capped, AgentAdapter.resolveChannelPeriod(0, NO_MIN, capped, -1, 0, capped, capped + 1000));
    }
}
