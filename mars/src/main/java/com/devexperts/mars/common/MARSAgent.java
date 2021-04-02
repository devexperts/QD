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
package com.devexperts.mars.common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * The agent attached to {@link MARS} instance to track, accumulate and retrieve all {@link MARSEvent} events.
 */
public class MARSAgent {

    private final MARS mars;

    private Collection<MARSEvent> events;
    private MARSListener listener;

    /**
     * Creates new agent and attaches it to specified MARS instance.
     */
    public MARSAgent(MARS mars) {
        if (mars == null)
            throw new NullPointerException();
        this.mars = mars;
        this.events = mars.addAgent(this);
    }

    /**
     * Retrieves all {@link MARSEvent} events accumulated by this agent.<br> You can use returned the collection as you
     * want. Nobody deals with it after returning.
     */
    public Collection<MARSEvent> retrieveEvents() {
        synchronized (mars) {
            Collection<MARSEvent> events = this.events;
            this.events = null;
            return events != null ? events : Collections.EMPTY_LIST;
        }
    }

    /**
     * Sets new listener to receive notifications. The listener is immediately notified if this agent has accumulated
     * {@link MARSEvent} events.
     */
    public void setListener(MARSListener listener) {
        synchronized (mars) {
            this.listener = listener;
            if (events == null || events.isEmpty())
                return;
        }
        fireListener();
    }

    /**
     * Closes this agent by detaching it from it's {@link MARS} instance.
     */
    public void close() {
        mars.removeAgent(this);
    }

    protected MARSListener getListener() {
        return listener;
    }

    // ========== Internal API ==========

    /**
     * Adds specified {@link MARSEvent} events to accumulated events collection.
     */
    void addEvents(Collection<MARSEvent> events) {
        if (this.events == null)
            this.events = new ArrayList<MARSEvent>(Math.max(events.size(), 5));
        if (events instanceof ArrayList)
            for (int i = 0; i < events.size(); i++)
                this.events.add((MARSEvent) ((ArrayList) events).get(i));
        else
            this.events.addAll(events);
    }

    /**
     * Notifies associated lister if any.
     */
    void fireListener() {
        MARSListener listener = this.listener; // Atomic read.
        if (listener != null)
            listener.marsChanged(this);
    }
}
