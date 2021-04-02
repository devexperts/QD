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
package com.dxfeed.webservice.rest;

import com.dxfeed.event.EventType;
import com.dxfeed.webservice.EventSymbolMap;

import java.util.List;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "events")
public class Events {
    @XmlType(name="events-status")
    public enum Status { OK, TIMED_OUT, NOT_SUBSCRIBED }

    private Status status;
    private List<EventType<?>> events;
    private EventSymbolMap symbolMap;

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @XmlAnyElement(lax = true)
    public List<EventType<?>> getEvents() {
        return events;
    }

    public void setEvents(List<EventType<?>> events) {
        this.events = events;
    }

    @XmlTransient
    public EventSymbolMap getSymbolMap() {
        return symbolMap;
    }

    public void setSymbolMap(EventSymbolMap symbolMap) {
        this.symbolMap = symbolMap;
    }
}
