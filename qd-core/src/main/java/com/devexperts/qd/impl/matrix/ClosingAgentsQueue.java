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
 * Queue of agents that are being closed. Operations that increase subscription size
 * ({@link Collector#addSubscriptionPart} and {@link Collector#removeSubscriptionPart}) help
 * closing agents from this queue under their global lock.
 */
class ClosingAgentsQueue {
    private Agent head;
    private Agent tail;

    // SYNC: global
    void add(Agent agent) {
        if (agent.isCloseCompleted())
            return;
        if (tail == null)
            head = agent;
        else
            tail.nextClosingAgent = agent;
        tail = agent;
    }

    /**
     * Returns first agent that is being closed from this queue.
     * Being closed means {@link Agent#isClosed()} is {@code true},
     * but {@link Agent#isCloseCompleted()} is {@code false}.
     */
    // SYNC: global
    Agent peek() {
        while (head != null && head.isCloseCompleted())
            head = head.nextClosingAgent;
        if (head == null)
            tail = null;
        return head;
    }
}
