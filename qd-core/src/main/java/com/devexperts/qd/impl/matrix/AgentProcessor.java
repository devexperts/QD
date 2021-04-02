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

import static com.devexperts.qd.impl.matrix.Collector.NEXT_AGENT;
import static com.devexperts.qd.impl.matrix.Collector.NEXT_INDEX;

/**
 * Processes subscription list starting from a given agent and index.
 */
class AgentProcessor {
    final Distribution dist;

    AgentProcessor(Distribution dist) {
        this.dist = dist;
    }

    /**
     * Iterates over a list of agents and {@link Distribution#add adds}
     * agents and indices inside agents into {@link Distribution} structure.
     * @param nagent Number of the agent in {@link Collector#agents} array.
     * @param nindex Index of subscription inside the agent.
     * @param timeMark Time mark of the event.
     * @param rid the record id.
     */
    // This method can try to allocate memory and die due to OutOfMemoryError.
    void processAgentsList(int nagent, int nindex, int timeMark, int rid) {
        while (nagent > 0) {
            SubMatrix nsub = dist.add(nagent, nindex, timeMark, 0, rid).sub;
            nagent = nsub.getInt(nindex + NEXT_AGENT);
            nindex = nsub.getInt(nindex + NEXT_INDEX);
        }
    }

    /**
     * Finishes remaining {@link #processAgentsList processing} and clears internal state.
     */
    void flush() {
        // nothing to do here
    }
}
