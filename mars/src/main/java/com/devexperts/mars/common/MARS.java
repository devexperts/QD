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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The instance of MARS system that defines and maintains individual tree of MARS parameters.
 */
public class MARS {

    private final Map<String, MARSEvent> events = new HashMap<String, MARSEvent>();
    private final Set<MARSAgent> agents = new HashSet<MARSAgent>();
    private MARSAgent[] agents_array;
    private ArrayList<MARSEvent> filtered_events;

    private final MARSEventFactory marsEventFactory = MARSEventFactory.getInstance();

    public MARS() {
    }

    /**
     * Returns current value of specified parameter or null if not found.
     */
    public String getValue(String name) {
        MARSEvent event = getEvent(name);
        return event == null ? null : event.getValue();
    }

    public void setValue(String name, String value, long timestamp) {
        putEvent(new MARSEvent(name, value, timestamp));
    }

    /**
     * Sets new value for specified parameter.
     */
    public void setValue(String name, String value) {
        putEvent(marsEventFactory.createMARSEvent(name, value));
    }

    /**
     * Returns last event that has changed specified parameter or null if not found.
     */
    public synchronized MARSEvent getEvent(String name) {
        return events.get(name);
    }

    /**
     * Puts new event to change corresponding parameter.
     */
    public void putEvent(MARSEvent event) {
        MARSAgent[] agents_array;
        synchronized (this) {
            ArrayList<MARSEvent> filtered = getFiltered();
            processEvent(event, filtered);
            agents_array = addEvents(filtered);
            filtered.clear();
            filtered_events = filtered;
        }
        fireListeners(agents_array);
    }

    /**
     * Puts collection of events to change corresponding parameters.
     */
    public void putEvents(Collection<MARSEvent> events) {
        if (events.isEmpty())
            return;
        MARSAgent[] agents_array;
        synchronized (this) {
            ArrayList<MARSEvent> filtered = getFiltered();
            for (MARSEvent event : events)
                processEvent(event, filtered);
            agents_array = addEvents(filtered);
            filtered.clear();
            filtered_events = filtered;
        }
        fireListeners(agents_array);
    }

    /**
     * Removes collection of authentic events as if they were never put before.
     */
    public void removeAuthenticEvents(Collection<MARSEvent> events) {
        if (events.isEmpty())
            return;
        MARSAgent[] agents_array;
        synchronized (this) {
            Set<String> nodes = new HashSet<String>();
            for (MARSEvent event : events) {
                if (event.getName().endsWith(MARSEvent.PARAM_TYPE_STATUS) && MARSStatus.find(event.getValue()) == MARSStatus.REMOVED)
                    continue;
                if (event != this.events.get(event.getName()))
                    continue;
                this.events.remove(event.getName());
                nodes.add(event.getNodeName());
            }
            Collection<MARSEvent> filtered = new ArrayList<MARSEvent>();
            for (String node : nodes) {
                filtered.add(marsEventFactory.createMARSEvent(node + MARSEvent.PARAM_TYPE_STATUS, MARSStatus.REMOVED.getName()));
                MARSEvent event;
                if ((event = this.events.get(node + MARSEvent.PARAM_TYPE_CATEGORY)) != null)
                    filtered.add(event);
                if ((event = this.events.get(node + MARSEvent.PARAM_TYPE_DESCRIPTION)) != null)
                    filtered.add(event);
                if ((event = this.events.get(node + MARSEvent.PARAM_TYPE_STATUS)) != null)
                    filtered.add(event);
                if ((event = this.events.get(node + MARSEvent.PARAM_TYPE_VALUE)) != null)
                    filtered.add(event);
            }
            agents_array = addEvents(filtered);
        }
        fireListeners(agents_array);
    }

    // ========== Internal API ==========

    private static final Comparator<MARSEvent> EVENT_NAME_COMPARATOR = new Comparator<MARSEvent>() {
        public int compare(MARSEvent e1, MARSEvent e2) {
            return e1.getName().compareTo(e2.getName());
        }
    };

    /**
     * Returns events map.
     * @return events map
     */
    synchronized Map<String, MARSEvent> getEvents() {
        return events;
    }

    /**
     * Registers new agent to receive notifications and returns events to complete agent's initialization.
     */
    synchronized Collection<MARSEvent> addAgent(MARSAgent agent) {
        agents.add(agent);
        agents_array = null;

        List<MARSEvent> result = new ArrayList<MARSEvent>(events.values());
        Collections.sort(result, EVENT_NAME_COMPARATOR);
        return result;
    }

    /**
     * Unregisters specified agent from notifications.
     */
    synchronized void removeAgent(MARSAgent agent) {
        agents.remove(agent);
        agents_array = null;
    }

    // Must be called SYNCHRONIZED
    private ArrayList<MARSEvent> getFiltered() {
        ArrayList<MARSEvent> filtered = filtered_events;
        if (filtered == null)
            filtered = new ArrayList<MARSEvent>();
        else
            filtered_events = null;
        filtered.clear();
        return filtered;
    }

    // Must be called SYNCHRONIZED
    private void processEvent(MARSEvent event, Collection<MARSEvent> filtered) {
        if (event.getName().endsWith(MARSEvent.PARAM_TYPE_STATUS) && MARSStatus.find(event.getValue()) == MARSStatus.REMOVED) {
            String nodeName = event.getNodeName();
            if (events.remove(nodeName + MARSEvent.PARAM_TYPE_CATEGORY) != null |
                events.remove(nodeName + MARSEvent.PARAM_TYPE_DESCRIPTION) != null |
                events.remove(nodeName + MARSEvent.PARAM_TYPE_STATUS) != null |
                events.remove(nodeName + MARSEvent.PARAM_TYPE_VALUE) != null)
            {
                filtered.add(event);
            }
        } else {
            MARSEvent oldEvent = events.put(event.getName(), event);
            if (oldEvent == null || !oldEvent.getValue().equals(event.getValue()) || oldEvent.getTimestamp() != event.getTimestamp())
                filtered.add(event);
        }
    }

    // Must be called SYNCHRONIZED
    private MARSAgent[] addEvents(Collection<MARSEvent> filtered) {
        if (filtered.isEmpty() || agents.isEmpty())
            return null;
        if (agents_array == null)
            agents_array = agents.toArray(new MARSAgent[agents.size()]);
        MARSAgent[] agents_array = this.agents_array;
        for (MARSAgent agent : agents_array)
            agent.addEvents(filtered);
        return agents_array;
    }

    // Must be called UNSYNCHRONIZED
    private static void fireListeners(MARSAgent[] agents_array) {
        if (agents_array != null)
            for (MARSAgent agent : agents_array)
                agent.fireListener();
    }
}
