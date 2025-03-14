/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.misc;

import com.devexperts.annotation.Internal;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.impl.XmlTimeAdapter;

import java.io.Serializable;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

/**
 * Configuration event with the text payload.
 *
 * <h3>Properties</h3>
 *
 * {@code TextConfiguration} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getTime() time} - time of this text configuration;
 * <li>{@link #getSequence() sequence} - sequence of this text configuration;
 * <li>{@link #getVersion() version} - version;
 * <li>{@link #getText() text} - text payload.
 * </ul>
 *
 * <h3><a name="versioningSection">Versioning</a></h3>
 *
 * <p>The {@link #getVersion() version} field is used to track changes to the event data.
 * When the version field is enabled (by default), events with a lower version number than the last received
 * will be dropped. This is useful for conflating events, where only the latest version of an event is processed.
 * This is only true when transferring under a <b>TICKER</b> contract and the version field is enabled.
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS record {@code TextConfiguration}.
 */
@XmlRootElement(name = "TextConfiguration")
@XmlType(propOrder = {"eventSymbol", "eventTime", "time", "sequence", "version", "text"})
public class TextConfiguration implements LastingEvent<String>, Serializable {
    private static final long serialVersionUID = 0;

    /**
     * Maximum allowed sequence value.
     * @see #setSequence(int)
     */
    public static final int MAX_SEQUENCE = (1 << 22) - 1;

    private String eventSymbol;
    private long eventTime;
    private long timeSequence;
    private int version;
    private String text;

    /**
     * Creates a new text configuration event with default values.
     */
    public TextConfiguration() {
    }

    /**
     * Creates a new text configuration event with the specified event symbol.
     *
     * @param eventSymbol the event symbol.
     */
    public TextConfiguration(String eventSymbol) {
        setEventSymbol(eventSymbol);
    }

    /**
     * Creates a new text configuration event with the specified event symbol and text.
     *
     * @param eventSymbol the event symbol.
     * @param text the text.
     */
    public TextConfiguration(String eventSymbol, String text) {
        setEventSymbol(eventSymbol);
        setText(text);
    }

    /**
     * Creates a new text configuration event with the specified event symbol, text, and version.
     *
     * @param eventSymbol the event symbol.
     * @param text the text.
     * @param version the version.
     */
    public TextConfiguration(String eventSymbol, String text, int version) {
        setEventSymbol(eventSymbol);
        setText(text);
        setVersion(version);
    }

    /**
     * Returns the symbol for this event.
     *
     * @return the event symbol.
     */
    @Override
    public String getEventSymbol() {
        return eventSymbol;
    }

    /**
     * Changes the symbol for this event.
     *
     * @param eventSymbol the event symbol.
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
     * Returns time of this text configuration event.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * @return time of this text configuration event.
     */
    @XmlJavaTypeAdapter(type = long.class, value = XmlTimeAdapter.class)
    @XmlSchemaType(name = "dateTime")
    public long getTime() {
        return (timeSequence >> 32) * 1000 + ((timeSequence >> 22) & 0x3ff);
    }

    /**
     * Changes time of this text configuration event.
     * Time is measured in milliseconds between the current time and midnight, January 1, 1970 UTC.
     *
     * @param time time of this text configuration event.
     */
    public void setTime(long time) {
        timeSequence = ((long) TimeUtil.getSecondsFromTime(time) << 32) |
            ((long) TimeUtil.getMillisFromTime(time) << 22) | getSequence();
    }

    /**
     * Returns sequence number of this event to distinguish text configuration events that have the same
     * {@link #getTime() time}. This sequence number does not have to be unique and
     * does not need to be sequential. Sequence can range from 0 to {@link #MAX_SEQUENCE}.
     *
     * @return sequence of this text configuration event.
     */
    public int getSequence() {
        return (int) timeSequence & MAX_SEQUENCE;
    }

    /**
     * Changes {@link #getSequence() sequence number} of this text configuration event.
     *
     * @param sequence the sequence.
     * @throws IllegalArgumentException if the sequence is below zero or above {@link #MAX_SEQUENCE}.
     * @see #getSequence()
     */
    public void setSequence(int sequence) {
        if (sequence < 0 || sequence > MAX_SEQUENCE)
            throw new IllegalArgumentException();
        timeSequence = (timeSequence & ~MAX_SEQUENCE) | sequence;
    }

    /**
     * Returns the version of this event.
     *
     * @return the version of this event. A higher value indicates a more recent update.
     * @see <a href="#versioningSection">Versioning section</a>
     */
    public int getVersion() {
        return version;
    }

    /**
     * Changes the version of this event.
     *
     * @param version the version of this event. A higher value indicates a more recent update.
     * @see <a href="#versioningSection">Versioning section</a>
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * Returns the text.
     *
     * @return the text.
     */
    public String getText() {
        return text;
    }

    /**
     * Changes the text.
     *
     * @param text the text.
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Returns a string representation of this text configuration event.
     *
     * @return a string representation of this text configuration event.
     */
    @Override
    public String toString() {
        return "TextConfiguration{" + eventSymbol +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", time=" + TimeFormat.DEFAULT.withMillis().format(getTime()) +
            ", sequence=" + getSequence() +
            ", version=" + version +
            ", text='" + text + "'" +
            "}";
    }

    // ========================= package private access for delegate =========================

    /**
     * Returns time and sequence of this event packaged into single long value.
     *
     * @return time and sequence of this text configuration event.
     */
    @XmlTransient
    @Internal
    long getTimeSequence() {
        return timeSequence;
    }

    /**
     * Changes time and sequence of this event.
     * <b>Do not use this method directly.</b>
     * Change {@link #setTime(long) time} and/or {@link #setSequence(int) sequence}.
     *
     * @param timeSequence the time and sequence.
     * @see #getTimeSequence()
     */
    @Internal
    void setTimeSequence(long timeSequence) {
        this.timeSequence = timeSequence;
    }
}
