/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.misc;

import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;
import com.dxfeed.event.EventType;
import com.dxfeed.impl.XmlTimeAdapter;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Message event with text payload. Messages are never conflated and are delivered to
 * all connected subscribers. There is no built-in persistence for messages. They are lost when subscribers
 * are not connected to the message publisher, so they shall be only used for notification purposes in
 * addition to persistence mechanism.
 *
 * <h3>Properties</h3>
 *
 * {@code TextMessage} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getTime() time} - time of the text message;
 * <li>{@link #getSequence() sequence} - sequence of the text message;
 * <li>{@link #getText() text} - text payload.
 * </ul>
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS record {@code TextMessage}.
 */
@XmlRootElement(name = "TextMessage")
@XmlType(propOrder = { "eventSymbol", "eventTime", "time", "sequence", "text" })
public class TextMessage implements EventType<String> {
    private static final long serialVersionUID = 0;

    /**
     * Maximum allowed sequence value.
     * @see #setSequence(int)
     */
    public static final int MAX_SEQUENCE = (1 << 22) - 1;

    private String eventSymbol;
    private long eventTime;
    private long timeSequence;
    private String text;

    /**
     * Creates new message with default values.
     */
    public TextMessage() {}

    /**
     * Creates new message with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    public TextMessage(String eventSymbol) {
        setEventSymbol(eventSymbol);
    }

    /**
     * Creates new message with the specified event symbol and text.
     * @param eventSymbol event symbol.
     * @param text text.
     */
    public TextMessage(String eventSymbol, String text) {
        setEventSymbol(eventSymbol);
        setText(text);
    }

    /**
     * Creates new message with the specified event symbol, time and text.
     *
     * @param eventSymbol event symbol.
     * @param time time.
     * @param text text.
     */
    public TextMessage(String eventSymbol, long time, String text) {
        setEventSymbol(eventSymbol);
        setTime(time);
        setText(text);
    }

    /**
     * Returns symbol for this event.
     */
    @Override
    public String getEventSymbol() {
        return eventSymbol;
    }

    /**
     * Changes symbol for this event.
     * @param eventSymbol event symbol.
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

    /**
     * Returns time and sequence of text message packaged into single long value.
     * @return time and sequence of text message.
     */
    @XmlTransient
    public long getTimeSequence() {
        return timeSequence;
    }

    /**
     * Changes time and sequence of text message.
     * <b>Do not use this method directly.</b>
     * Change {@link #setTime(long) time} and/or {@link #setSequence(int) sequence}.
     *
     * @param timeSequence the time and sequence.
     * @see #getTimeSequence()
     */
    public void setTimeSequence(long timeSequence) {
        this.timeSequence = timeSequence;
    }

    /**
     * Returns time of the text message.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @return time of the text message.
     */
    @XmlJavaTypeAdapter(type=long.class, value= XmlTimeAdapter.class)
    @XmlSchemaType(name="dateTime")
    public long getTime() {
        return (timeSequence >> 32) * 1000 + ((timeSequence >> 22) & 0x3ff);
    }

    /**
     * Changes time of the text message.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     * @param time time of the text message.
     */
    public void setTime(long time) {
        timeSequence = ((long) TimeUtil.getSecondsFromTime(time) << 32) | ((long) TimeUtil.getMillisFromTime(time) << 22) | getSequence();
    }

    /**
     * Returns sequence number of the text message to distinguish messages that have the same
     * {@link #getTime() time}. This sequence number does not have to be unique and
     * does not need to be sequential. Sequence can range from 0 to {@link #MAX_SEQUENCE}.
     * @return sequence of the text message.
     */
    public int getSequence() {
        return (int) timeSequence & MAX_SEQUENCE;
    }

    /**
     * Changes {@link #getSequence()} sequence number of the text message.
     * @param sequence the sequence.
     * @throws IllegalArgumentException if sequence is below zero or above {@link #MAX_SEQUENCE}.
     * @see #getSequence()
     */
    public void setSequence(int sequence) {
        if (sequence < 0 || sequence > MAX_SEQUENCE)
            throw new IllegalArgumentException();
        timeSequence = (timeSequence & ~MAX_SEQUENCE) | sequence;
    }

    /**
     * Returns text.
     */
    public String getText() {
        return text;
    }

    /**
     * Changes text.
     * @param text text.
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Returns string representation of this TextMessage event.
     */
    @Override
    public String toString() {
        return "TextMessage{" + getEventSymbol() +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", time=" + TimeFormat.DEFAULT.withMillis().format(getTime()) +
            ", sequence=" + getSequence() +
            ", text='" + getText() + "'" +
            "}";
    }
}
