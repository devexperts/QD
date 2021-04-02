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
package com.dxfeed.api.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMapping;
import com.devexperts.qd.util.TimeSequenceUtil;
import com.devexperts.util.Timing;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.EventType;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.TimeSeriesEvent;

import java.util.EnumSet;

public abstract class EventDelegate<T extends EventType<?>> {
    protected final DataRecord record;
    protected final QDContract contract;
    protected final boolean sub;
    protected final boolean pub;
    protected final boolean timeSeries;
    protected final boolean wildcard;

    protected final SymbolCodec codec;
    protected final Class<T> eventType;
    protected final boolean lastingEvent;

    @SuppressWarnings("unchecked")
    protected EventDelegate(DataRecord record, QDContract contract, EnumSet<EventDelegateFlags> flags) {
        this.record = record;
        this.contract = contract;
        this.sub = flags.contains(EventDelegateFlags.SUB);
        this.pub = flags.contains(EventDelegateFlags.PUB);
        this.timeSeries = flags.contains(EventDelegateFlags.TIME_SERIES);
        this.wildcard = flags.contains(EventDelegateFlags.WILDCARD);
        this.codec = record.getScheme().getCodec();
        this.eventType = (Class<T>) createEvent().getClass();
        boolean timeSeriesEvent = TimeSeriesEvent.class.isAssignableFrom(eventType);
        if (timeSeries && !timeSeriesEvent)
            throw new IllegalArgumentException("Cannot create time series delegate for non time series event " + eventType);
        this.lastingEvent = LastingEvent.class.isAssignableFrom(eventType);
    }

    public final Class<T> getEventType() {
        return eventType;
    }

    public final DataRecord getRecord() {
        return record;
    }

    public final QDContract getContract() {
        return contract;
    }

    public final boolean isSub() {
        return sub;
    }

    public boolean isPub() {
        return pub;
    }

    public final boolean isTimeSeries() {
        return timeSeries;
    }

    public boolean isWildcard() {
        return wildcard;
    }

    public final boolean isLastingEvent() {
        return lastingEvent;
    }

    public EventDelegateSet<T, ? extends EventDelegate<T>> createDelegateSet() {
        return new EventDelegateSet<>(eventType);
    }

    // Works on string symbols, too, so DelegateSet.convertSymbol call is optional
    public String getQDSymbolByEventSymbol(Object symbol) {
        return getMapping().getQDSymbolByEventSymbol(symbol);
    }

    public Object getEventSymbolByQDSymbol(String qdSymbol) {
        return getMapping().getEventSymbolByQDSymbol(qdSymbol);
    }

    public long getQDTimeByEventTime(long time) {
        return time == Long.MAX_VALUE ? Long.MAX_VALUE : TimeSequenceUtil.getTimeSequenceFromTimeMillis(time);
    }

    public long getEventTimeByQDTime(long time) {
        return time == Long.MAX_VALUE ? Long.MAX_VALUE : TimeSequenceUtil.getTimeMillisFromTimeSequence(time);
    }

    public long getFetchTimeHeuristicByEventSymbolAndFromTime(Object eventSymbol, long fromTime) {
        Timing.Day day = Timing.EST.getByTime(fromTime);
        do {
            day = Timing.EST.getById(day.day_id - 1); // prev day
        } while (!day.isTrading());
        return day.day_start; // start of previous trading day
    }

    public String getQDSymbolByEvent(T event) {
        return getQDSymbolByEventSymbol(event.getEventSymbol());
    }

    public Object getSubscriptionSymbolByQDSymbolAndTime(String qdSymbol, long time) {
        Object eventSymbol = getEventSymbolByQDSymbol(qdSymbol);
        return timeSeries ? new TimeSeriesSubscriptionSymbol<>(
            eventSymbol, getEventTimeByQDTime(time)) : eventSymbol;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public T createEvent(Object symbol, RecordCursor cursor) {
        EventType event = createEvent();
        event.setEventSymbol(symbol);
        return getEvent((T) event, cursor);
    }

    public T createEvent(RecordCursor cursor) {
        return createEvent(getEventSymbolByQDSymbol(codec.decode(cursor.getCipher(), cursor.getSymbol())), cursor);
    }

    //----------------------- must be implemented in subclasses -----------------------

    public abstract RecordMapping getMapping();

    public abstract T createEvent();

    // subclasses override
    public T getEvent(T event, RecordCursor cursor) {
        event.setEventTime(TimeSequenceUtil.getTimeMillisFromTimeSequence(cursor.getEventTimeSequence()));
        return event;
    }

    //----------------------- must be overridden in publishable subclasses -----------------------

    // subclasses override
    public RecordCursor putEvent(T event, RecordBuffer buf) {
        String symbol = getQDSymbolByEvent(event);
        RecordCursor cursor = buf.add(record, codec.encode(symbol), symbol);
        if (cursor.hasEventTimeSequence())
            cursor.setEventTimeSequence(TimeSequenceUtil.getTimeSequenceFromTimeMillis(event.getEventTime()));
        return cursor;
    }

    @Override
    public String toString() {
        return "EventDelegate{" +
            "eventType=" + eventType.getName() +
            ", record=" + record +
            ", contract=" + contract +
            ", sub=" + sub +
            ", pub=" + pub +
            ", timeSeries=" + timeSeries +
            ", wildcard=" + wildcard +
            '}';
    }
}
