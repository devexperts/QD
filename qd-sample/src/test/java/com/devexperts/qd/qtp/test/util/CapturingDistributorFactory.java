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
package com.devexperts.qd.qtp.test.util;

import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.util.TimePeriod;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only {@link DistributorAdapter.Factory} that captures protocol descriptors
 * flowing through every adapter it creates.
 *
 * <p>Captured snapshots are deep copies (via
 * {@link ProtocolDescriptor#newPeerProtocolDescriptor(ProtocolDescriptor)}); the descriptor
 * reference held by the adapter is mutated by {@code super.*}, so plain references would
 * not reflect the on-wire content.
 *
 * <p>Note: this factory does not propagate {@code fieldReplacer} into the adapter — the
 * parent factory exposes it only via a String getter. Tests that need field replacers
 * should not use this capturing factory.
 */
public class CapturingDistributorFactory extends DistributorAdapter.Factory {

    private final List<ProtocolDescriptor> incoming = new CopyOnWriteArrayList<>();
    private final List<ProtocolDescriptor> outgoing = new CopyOnWriteArrayList<>();

    public CapturingDistributorFactory(QDTicker ticker, QDStream stream, QDHistory history, SubscriptionFilter filter) {
        super(ticker, stream, history, filter);
    }

    public CapturingDistributorFactory(QDEndpoint endpoint, SubscriptionFilter filter) {
        super(endpoint, filter);
    }

    public CapturingDistributorFactory(QDCollector collector) {
        super(collector);
    }

    /** Snapshots of descriptors received from the peer, in arrival order. */
    public List<ProtocolDescriptor> incoming() {
        return incoming;
    }

    /** Snapshots of descriptors prepared for sending to the peer, in send order. */
    public List<ProtocolDescriptor> outgoing() {
        return outgoing;
    }

    public ProtocolDescriptor lastIncoming() {
        return incoming.isEmpty() ? null : incoming.get(incoming.size() - 1);
    }

    public ProtocolDescriptor lastOutgoing() {
        return outgoing.isEmpty() ? null : outgoing.get(outgoing.size() - 1);
    }

    public void clear() {
        incoming.clear();
        outgoing.clear();
    }

    @Override
    public MessageAdapter createAdapter(QDStats stats) {
        DistributorAdapter adapter = new CapturingDistributorAdapter(endpoint, ticker, stream, history,
            getFilter(), getStripe(), stats);
        TimePeriod requested = getRequestedAggregationPeriod();
        if (requested != null)
            adapter.setRequestedAggregationPeriod(requested);
        return adapter;
    }

    private class CapturingDistributorAdapter extends DistributorAdapter {
        // Diff against the parser's running accumulator so captures reflect wire-level deltas.
        private ProtocolDescriptor lastIncoming;

        CapturingDistributorAdapter(QDEndpoint endpoint, QDTicker ticker, QDStream stream, QDHistory history,
            SubscriptionFilter filter, QDFilter stripe, QDStats stats)
        {
            super(endpoint, ticker, stream, history, filter, stripe, stats, null);
        }

        @Override
        public void processDescribeProtocol(ProtocolDescriptor desc, boolean logDescriptor) {
            incoming.add(desc.deltaFrom(lastIncoming));
            lastIncoming = desc;
            super.processDescribeProtocol(desc, logDescriptor);
        }

        @Override
        public void prepareProtocolDescriptor(ProtocolDescriptor desc) {
            super.prepareProtocolDescriptor(desc);
            outgoing.add(ProtocolDescriptor.newPeerProtocolDescriptor(desc));
        }
    }
}
