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
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only {@link AgentAdapter.Factory} that captures protocol descriptors
 * flowing through every adapter it creates.
 *
 * <p>Captured snapshots are deep copies (via
 * {@link ProtocolDescriptor#newPeerProtocolDescriptor(ProtocolDescriptor)}) — the
 * descriptor reference held by the adapter is mutated by {@code super.*}, so plain
 * references would not reflect the on-wire content.
 *
 * <p>The factory wires itself into the adapter via {@code setAgentFactory} reflectively
 * because the setter is private; without it the adapter would not inherit the factory's
 * aggregation-period bounds.
 */
public class CapturingAgentFactory extends AgentAdapter.Factory {

    private static final Method SET_AGENT_FACTORY;
    static {
        try {
            SET_AGENT_FACTORY = AgentAdapter.class
                .getDeclaredMethod("setAgentFactory", AgentAdapter.Factory.class);
            SET_AGENT_FACTORY.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final List<ProtocolDescriptor> incoming = new CopyOnWriteArrayList<>();
    private final List<ProtocolDescriptor> outgoing = new CopyOnWriteArrayList<>();

    public CapturingAgentFactory(QDTicker ticker, QDStream stream, QDHistory history, SubscriptionFilter filter) {
        super(ticker, stream, history, filter);
    }

    public CapturingAgentFactory(QDEndpoint endpoint, SubscriptionFilter filter) {
        super(endpoint, filter);
    }

    public CapturingAgentFactory(QDCollector collector) {
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
        // Route through the scheme constructor so channels stays null until reinitConfiguration
        // installs the session-driven shapers, matching production AgentAdapter.Factory.
        AgentAdapter adapter = new CapturingAgentAdapter(endpoint,
            MessageAdapter.getCommonScheme(ticker, stream, history), getFilter(), getStripe(), stats);
        try {
            SET_AGENT_FACTORY.invoke(adapter, this);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to wire AgentAdapter factory reference", e);
        }
        return adapter;
    }

    private class CapturingAgentAdapter extends AgentAdapter {
        // Diff against the parser's running accumulator so captures reflect wire-level deltas.
        private ProtocolDescriptor lastIncoming;

        CapturingAgentAdapter(QDEndpoint endpoint, DataScheme scheme, QDFilter filter, QDFilter stripe, QDStats stats) {
            super(endpoint, scheme, filter, stripe, stats);
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
