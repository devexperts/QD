/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.matrix;

/**
 * Reusable auxiliary class that iterates over agents that are subscribed to the specified (key, rid) chain.
 * The subscription chain can be either direct subscription (key, rid) or wildcard subscription (wildcard, rid).
 * Use:
 * <pre>
 * for (Agent agent = ait.start(collector, key, rid); agent != null; agent = ait.next()) {
 *     // use agent here
 * }
 * </pre>
 */
final class AgentIterator {
    private Agent[] agents;
    private Agent agent;
    private int index;

    // SYNC: global
    Agent start(Collector collector, int key, int rid) {
        return start(collector, collector.total.sub.getIndex(key, rid, 0));
    }

    // SYNC: global
    Agent start(Collector collector, int tindex) {
        agents = collector.agents;
        agent = collector.total;
        index = tindex;
        return next();
    }

    void clear() {
        agents = null;
        agent = null;
    }

    Agent next() {
        if (agent != null) {
            SubMatrix sub = agent.sub;
            int nagent = sub.getInt(index + Collector.NEXT_AGENT);
            agent = nagent > 0 ? agents[nagent] : null; // beware of negative nagent in tsub
            index = sub.getInt(index + Collector.NEXT_INDEX);
        }
        return agent;
    }

    int currentIndex() {
        return index;
    }
}
