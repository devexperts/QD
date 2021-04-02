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
package com.dxfeed.event.market;

import com.dxfeed.event.EventType;

import javax.xml.bind.annotation.XmlType;

/**
 * Abstract base class for all market events. All market events are plain java objects that
 * extend this class. Market event classes are simple beans with setter and getter methods for their
 * properties and minimal business logic. All market events have {@code eventSymbol} property that is
 * defined by this class.
 *
 * <p>Event symbol for a market event is a market symbol {@link String}.
 * See {@link MarketEventSymbols} class for a description of market event symbology.
 */
@XmlType(propOrder = {"eventSymbol", "eventTime"})
public abstract class MarketEvent implements EventType<String> {
    private static final long serialVersionUID = 0;

    private String eventSymbol;
    private long eventTime;

    /**
     * Protected constructor for concrete implementation classes.
     */
    protected MarketEvent() {}

    /**
     * Protected constructor for concrete implementation classes that initializes
     * {@code eventSymbol} property.
     *
     * @param eventSymbol the event symbol.
     */
    protected MarketEvent(String eventSymbol) {
        this.eventSymbol = eventSymbol;
    }

    /**
     * Returns symbol of this event.
     * @return symbol of this event.
     */
    @Override
    public String getEventSymbol() {
        return eventSymbol;
    }

    /**
     * Changes symbol of this event.
     * @param eventSymbol symbol of this event.
     */
    @Override
    public void setEventSymbol(String eventSymbol) {
        this.eventSymbol = eventSymbol;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getEventTime() {
        return eventTime;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setEventTime(long eventTime) {
        this.eventTime = eventTime;
    }
}
