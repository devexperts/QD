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
package com.dxfeed.webservice.comet;

import com.dxfeed.event.EventType;
import com.dxfeed.webservice.EventSymbolMap;

import java.util.List;

public class DataMessage {
    private final boolean sendScheme;
    private final Class<?> eventType;
    private final List<? extends EventType<?>> events;
    private final EventSymbolMap symbolMap;

    DataMessage(boolean sendScheme, Class<?> eventType, List<? extends EventType<?>> events, EventSymbolMap symbolMap) {
        this.sendScheme = sendScheme;
        this.eventType = eventType;
        this.events = events;
        this.symbolMap = symbolMap;
    }

    public boolean isSendScheme() {
        return sendScheme;
    }

    public Class<?> getEventType() {
        return eventType;
    }

    public List<? extends EventType<?>> getEvents() {
        return events;
    }

    public EventSymbolMap getSymbolMap() {
        return symbolMap;
    }
}
