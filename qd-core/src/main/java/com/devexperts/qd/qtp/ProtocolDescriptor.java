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
package com.devexperts.qd.qtp;

import com.devexperts.connector.proto.EndpointId;
import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.qd.QDFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Descriptor for QTP protocol that is sent in {@link MessageType#DESCRIBE_PROTOCOL DESCRIBE_PROTOCOL} message.
 */
public final class ProtocolDescriptor {
    /**
     * This string identifies protocol descriptor message.
     */
    public static final String MAGIC_STRING = "DXP3";

    private static final byte[] MAGIC_BYTES;

    static {
        int n = MAGIC_STRING.length();
        MAGIC_BYTES = new byte[n];
        for (int i = 0; i < n; i++) {
            MAGIC_BYTES[i] = (byte) MAGIC_STRING.charAt(i);
        }
    }

    public static final String TYPE_PROPERTY = "type";
    public static final String VERSION_PROPERTY = "version";
    public static final String OPT_PROPERTY = "opt";
    public static final String TIME_PROPERTY = "time";
    public static final String NAME_PROPERTY = "name";
    public static final String FILTER_PROPERTY = "filter"; // using QDFilter syntax
    public static final String STRIPE_PROPERTY = "stripe"; // using SymbolStriper QDFilter
    public static final String SERVICES_PROPERTY = "services"; // using ServiceFilter syntax
    public static final String AUTHORIZATION_PROPERTY = "authorization";
    public static final String AUTHENTICATION_PROPERTY = "authentication";
    public static final String RMI_PROPERTY = "rmi";
    public static final String BASIC_AUTHORIZATION = "Basic";

    private final Map<String, String> properties = new LinkedHashMap<>();
    private final Set<MessageDescriptor> send = new LinkedHashSet<>();
    private final Set<MessageDescriptor> receive = new LinkedHashSet<>();
    private EndpointId endpointId;

    private ProtocolDescriptor(ProtocolDescriptor previouslyRead) {
        if (previouslyRead != null) {
            properties.putAll(previouslyRead.properties);
            send.addAll(previouslyRead.send);
            receive.addAll(previouslyRead.receive);
            endpointId = previouslyRead.getEndpointId();
        }
    }

    /**
     * Creates empty protocol description for a specified type with an embedded QDS version.
     * Types are used for files and should be short camel-case strings like "qtp" for socket QTP protocol,
     * "tape" for generic tape files, "snapshot", "extract", etc.
     *
     * @param type the type.
     * @return new protocol descriptor.
     */
    public static ProtocolDescriptor newSelfProtocolDescriptor(String type) {
        if (type == null)
            throw new NullPointerException();
        ProtocolDescriptor result = new ProtocolDescriptor(null);
        result.setProperty(TYPE_PROPERTY, type);
        result.setProperty(VERSION_PROPERTY, QDFactory.getVersion());
        if (!ProtocolOption.SUPPORTED_SET.isEmpty())
            result.setProperty(OPT_PROPERTY, ProtocolOption.SUPPORTED_SET.toString());
        return result;
    }

    public static ProtocolDescriptor newPeerProtocolDescriptor(ProtocolDescriptor previouslyRead) {
        return new ProtocolDescriptor(previouslyRead);
    }

    public static ProtocolDescriptor newPeerProtocolDescriptorReadAs(ProtocolDescriptor original, MessageType readAs) {
        ProtocolDescriptor result = new ProtocolDescriptor(original);
        MessageDescriptor readAsMessage = new MessageDescriptor(result);
        readAsMessage.setMessageType(readAs);
        for (Iterator<MessageDescriptor> it = result.send.iterator(); it.hasNext(); ) {
            MessageDescriptor md = it.next();
            if (md.getMessageType().hasRecords()) {
                readAsMessage.properties.putAll(md.properties);
                it.remove();
            }
        }
        result.send.add(readAsMessage);
        return result;
    }

    public MessageDescriptor newMessageDescriptor() {
        return new MessageDescriptor(this);
    }

    public MessageDescriptor newMessageDescriptor(MessageType messageType) {
        MessageDescriptor result = new MessageDescriptor(this);
        result.setMessageType(messageType);
        return result;
    }

    public EndpointId getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(EndpointId endpointId) {
        this.endpointId = endpointId;
    }

    public Map<String,String> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    public Set<MessageDescriptor> getSendMessages() {
        return Collections.unmodifiableSet(send);
    }

    public Set<MessageDescriptor> getReceiveMessages() {
        return Collections.unmodifiableSet(receive);
    }

    public String getProperty(String key) {
        return properties.get(key);
    }

    public void setProperty(String key, String value) {
        if (value == null)
            properties.remove(key);
        else
            properties.put(key, value);
    }

    public boolean canSend(MessageType message) {
        for (MessageDescriptor md : send)
            if (md.getMessageType() == message)
                return true;
        return false;
    }

    public boolean canReceive(MessageType message) {
        for (MessageDescriptor md : receive)
            if (md.getMessageType() == message)
                return true;
        return false;
    }

    public EnumSet<MessageType> getSendSet() {
        EnumSet<MessageType> result = EnumSet.noneOf(MessageType.class);
        for (MessageDescriptor md : send) {
            MessageType message = md.getMessageType();
            if (message != null)
                result.add(message);
        }
        return result;
    }

    public MessageDescriptor getSend(MessageType message) {
        for (MessageDescriptor md : send)
            if (md.getMessageType() == message)
                return md;
        return null;
    }

    public EnumSet<MessageType> getReceiveSet() {
        EnumSet<MessageType> result = EnumSet.noneOf(MessageType.class);
        for (MessageDescriptor md : receive) {
            MessageType message = md.getMessageType();
            if (message != null)
                result.add(message);
        }
        return result;
    }

    public MessageDescriptor getReceive(MessageType message) {
        for (MessageDescriptor md : receive)
            if (md.getMessageType() == message)
                return md;
        return null;
    }

    public void addSend(MessageDescriptor message) {
        send.add(message);
    }

    public void addReceive(MessageDescriptor message) {
        receive.add(message);
    }

    /**
     * Composes this protocol descriptor in compact binary format
     * <b>including</b> its {@link #MAGIC_STRING}.
     */
    public void composeTo(BufferedOutput out) throws IOException {
        out.write(MAGIC_BYTES);
        composePropertiesTo(out, properties);
        composeMessageListTo(out, send);
        composeMessageListTo(out, receive);
        if (endpointId != null)
            EndpointId.writeEndpointId(out, endpointId, null);
    }

    /**
     * Parses protocol descriptor from compact binary format
     * <b>including</b> its {@link #MAGIC_STRING}.
     * @throws IOException when descriptor cannot be read due to wrong magic or other reasons.
     */
    public void parseFrom(BufferedInput in) throws IOException {
        for (byte b : MAGIC_BYTES)
            if (in.read() != b)
                throw new IOException("Invalid protocol descriptor magic. Wrong protocol.");
        parsePropertiesFrom(in, properties);
        parseMessageListFrom(in, send);
        parseMessageListFrom(in, receive);
        if (in.hasAvailable())
            endpointId = EndpointId.readEndpointId(in, null);
    }

    /**
     * Converts this protocol descriptor to a list of strings for representation in tab or comma separated text format.
     * It <b>does not include</b> its {@link #MAGIC_STRING}.
     */
    public List<String> convertToTextTokens() {
        ArrayList<String> tokens = new ArrayList<>();
        convertPropertiesToTextTokens(tokens, properties);
        convertMessageListToTextTokens(tokens, "+", send);
        convertMessageListToTextTokens(tokens, "-", receive);
        return tokens;
    }

    /**
     * Adds information to this protocol descriptor from a list of strings that are parsed
     * from tab or comma separated text format.
     */
    public int appendFromTextTokens(List<String> tokens, int i) {
        i = appendPropertiesFromTextTokens(tokens, properties, i);
        i = appendMessageListFromTextTokens(tokens, "+", send, i);
        i = appendMessageListFromTextTokens(tokens, "-", receive, i);
        return i;
    }

    static void composePropertiesTo(BufferedOutput out, Map<String,String> properties) throws IOException {
        out.writeCompactInt(properties.size());
        for (Map.Entry<String,String> entry: properties.entrySet()) {
            out.writeUTFString(entry.getKey());
            out.writeUTFString(entry.getValue());
        }
    }

    static void parsePropertiesFrom(BufferedInput in, Map<String,String> properties) throws IOException {
        int size = in.readCompactInt();
        if (size < 0)
            throw new IOException("Invalid size: " + size);
        for (int i = 0; i < size; i++) {
            String key = in.readUTFString();
            String value = in.readUTFString();
            properties.put(key, value);
        }
    }

    private void composeMessageListTo(BufferedOutput out, Collection<MessageDescriptor> messages) throws IOException {
        out.writeCompactInt(messages.size());
        for (MessageDescriptor message : messages)
            message.composeTo(out);
    }

    private void parseMessageListFrom(BufferedInput in, Collection<MessageDescriptor> messages) throws IOException {
        int size = in.readCompactInt();
        if (size < 0)
            throw new IOException("Invalid size: " + size);
        for (int i = 0; i < size; i++) {
            MessageDescriptor message = newMessageDescriptor();
            message.parseFrom(in);
            messages.add(message);
        }
    }

    static void convertPropertiesToTextTokens(List<String> tokens, Map<String, String> properties) {
        for (Map.Entry<String, String> entry : properties.entrySet())
            tokens.add(entry.getKey() + "=" + entry.getValue());
    }

    static int appendPropertiesFromTextTokens(List<String> tokens, Map<String, String> properties, int i) {
        while (i < tokens.size()) {
            String token = tokens.get(i);
            int j = token.indexOf('=');
            if (j < 0)
                break;
            String key = token.substring(0, j);
            String value = token.substring(j + 1);
            properties.put(key, value);
            i++;
        }
        return i;
    }

    private void convertMessageListToTextTokens(List<String> tokens, String prefix, Set<MessageDescriptor> messages) {
        for (MessageDescriptor message : messages)
            message.convertToTextTokens(tokens, prefix);
    }

    private int appendMessageListFromTextTokens(List<String> tokens, String prefix, Set<MessageDescriptor> messages, int i) {
        while (i < tokens.size() && tokens.get(i).startsWith(prefix)) {
            MessageDescriptor message = newMessageDescriptor();
            i = message.appendFromTextTokens(tokens, prefix, i);
            messages.add(message);
        }
        return i;
    }


    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (endpointId != null)
            sb.append(endpointId).append(' ');
        sb.append("[");
        int index = 0;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            if (index++ > 0)
                sb.append(", ");
            String key = entry.getKey();
            String value = entry.getValue();
            if (key.equals(AUTHORIZATION_PROPERTY)) {
                int i = value.indexOf(' ');
                if (i >= 0)
                    value = value.substring(0, i);
            }
            sb.append(key);
            sb.append("=");
            sb.append(value);
        }
        sb.append("] sending ");
        Set<MessageType.Flag> flags = EnumSet.noneOf(MessageType.Flag.class);
        for (MessageDescriptor md : send) {
            MessageType message = md.getMessageType();
            if (message != null)
                flags.addAll(message.getFlags());
        }
        sb.append(flags);
        return sb.toString();
    }
}


