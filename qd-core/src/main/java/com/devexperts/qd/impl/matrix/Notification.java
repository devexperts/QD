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

import com.devexperts.util.LockFreePool;


/**
 * The <code>Notification</code> is an auxiliary data structure used during
 * data distribution. Unlike {@link Distribution}, <code>Notification</code>
 * accumulates data from several usages until cleared, thus it allows single
 * notification to be issued after distribution.
 */
final class Notification {

    static final int SNAPSHOT_BIT = 1 << 31;
    static final int UPDATE_BIT = 1 << 30;
    static final int NEXT_MASK = (1 << 30) - 1;


    // --------- Pool of Notification objects ---------

    private static final LockFreePool<Notification> POOL =
        new LockFreePool<Notification>(Notification.class.getName(), 2 * Runtime.getRuntime().availableProcessors());

    static Notification getInstance() {
        Notification result = POOL.poll();
        return result == null ? new Notification() : result;
    }

    void release() {
        clear();
        POOL.offer(this);
    }

    private Notification() {} // only take instances via getInstance

    // --------- Instance data & code  ---------

    private Agent[] agents; // [agent.number] -> agent at the start of distribution.
    private int[] info; // [agent.number] -> next | SNAPSHOT_BIT
    private int firstAffected; // first affected agent number.

    /**
     * Ensures that this notification has capacity to work with specified nAgents.
     * Keeps accumulated data.
     */
    void ensureCapacity(int nAgents) {
        int length = agents == null ? 0 : agents.length;
        if (nAgents < length)
            return;
        length = Math.max(16, Math.max(length << 1, nAgents + 1));
        Agent[] oldAgents = agents;
        int[] oldInfo = info;

        agents = new Agent[length];
        info = new int[length];

        for (int i = firstAffected; i > 0; i = oldInfo[i] & NEXT_MASK) {
            agents[i] = oldAgents[i];
            info[i] = oldInfo[i];
        }
    }

    /**
     * Clears this notification so it holds no references and is ready for next usage.
     */
    void clear() {
        for (int i = firstAffected; i > 0;) {
            int next = info[i] & NEXT_MASK;
            agents[i] = null;
            info[i] = 0;
            i = next;
        }
        firstAffected = 0;
    }

    /**
     * Adds specified agent for notification.
     */
    void add(Agent agent, int bits) {
        int number = agent.number;
        if (agents[number] == null) {
            info[number] = firstAffected | bits;
            firstAffected = number;
        } else
            info[number] |= bits;
        agents[number] = agent; // Overwrite agent anyway as it could change between iterations.
    }

    /**
     * Returns first affected agent or null if none.
     */
    Agent firstAgent() {
        return agents[firstAffected];
    }

    /**
     * Returns next affected agent or null if none.
     */
    Agent nextAgent(Agent agent) {
        return agents[info[agent.number] & NEXT_MASK];
    }

    int getBits(Agent agent) {
        return info[agent.number] & ~NEXT_MASK;
    }
}
