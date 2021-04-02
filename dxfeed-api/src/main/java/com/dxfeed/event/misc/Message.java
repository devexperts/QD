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
package com.dxfeed.event.misc;

import com.devexperts.io.IOUtil;
import com.devexperts.io.Marshalled;
import com.devexperts.util.TimeFormat;
import com.dxfeed.event.EventType;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import javax.xml.bind.annotation.XmlAnyElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

/**
 * Message event with application-specific attachment. Messages are never conflated and are delivered to
 * all connected subscribers. There is no built-in persistence for messages. They are lost when subscribers
 * are not connected to the message publisher, so they shall be only used for notification purposes in
 * addition to persistence mechanism.
 *
 * <h3>Properties</h3>
 *
 * {@code Message} event has the following properties:
 *
 * <ul>
 * <li>{@link #getEventSymbol() eventSymbol} - symbol of this event;
 * <li>{@link #getAttachment() attachment} - attachment.
 * </ul>
 *
 * <h3>Implementation details</h3>
 *
 * This event is implemented on top of QDS record {@code Message}.
 */
@XmlRootElement(name = "Message")
@XmlType(propOrder = { "eventSymbol", "eventTime", "attachment" })
public class Message implements EventType<String> {
    private static final long serialVersionUID = 0;

    private String eventSymbol;
    private long eventTime;
    private transient Marshalled<?> attachment;

    /**
     * Creates new message with default values.
     */
    public Message() {}

    /**
     * Creates new message with the specified event symbol.
     * @param eventSymbol event symbol.
     */
    public Message(String eventSymbol) {
        setEventSymbol(eventSymbol);
    }

    /**
     * Creates new message with the specified event symbol and attachment.
     * @param eventSymbol event symbol.
     * @param attachment attachment.
     */
    public Message(String eventSymbol, Object attachment) {
        setEventSymbol(eventSymbol);
        setAttachment(attachment);
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
     * Returns attachment.
     * @throws RuntimeException if object cannot be deserialized from its serial form
     */
    @XmlAnyElement
    public Object getAttachment() {
        return attachment == null ? null : attachment.getObject();
    }

    /**
     * Returns attachment.
     * @param cl the ClassLoader that will be used to load classes; <code>null</code> for default
     * @throws RuntimeException if object cannot be deserialized from its serial form
     */
    public Object getAttachment(ClassLoader cl) {
        return attachment == null ? null : attachment.getObject(cl);
    }

    /**
     * Changes attachment.
     * @param attachment attachment.
     */
    public void setAttachment(Object attachment) {
        this.attachment = Marshalled.forObject(attachment);
    }

    /**
     * Returns implementation-specific form of an attachment.
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     */
    Marshalled<?> getMarshalledAttachment() {
        return attachment;
    }

    /**
     * Changes implementation-specific form of an attachment.
     * <b>Do not use this method directly.</b>
     * It may be removed or changed in the future versions.
     * @param attachment implementation-specific form of an attachment.
     */
    void setMarshalledAttachment(Marshalled<?> attachment) {
        this.attachment = attachment;
    }

    /**
     * Returns string representation of this message event.
     */
    @Override
    public String toString() {
        return "Message{" + eventSymbol +
            ", eventTime=" + TimeFormat.DEFAULT.withMillis().format(getEventTime()) +
            ", attachment=" + attachment +
            "}";
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        IOUtil.writeByteArray(out, attachment == null ? null : attachment.getBytes());
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        attachment = Marshalled.forBytes(IOUtil.readByteArray(in));
    }
}
