/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp;

import com.devexperts.annotation.Internal;
import com.devexperts.connector.proto.EndpointId;
import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.qd.QDFactory;
import com.devexperts.util.IndexedMap;
import com.devexperts.util.TimePeriodInfo;

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
import java.util.Objects;
import java.util.Set;

/**
 * Descriptor for QTP protocol that is sent in {@link MessageType#DESCRIBE_PROTOCOL DESCRIBE_PROTOCOL} message.
 */
@Internal
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

    /**
     * Protocol/format type identifier (e.g., "qtp" for socket QTP, "tape" for tape files).
     * Set by both sides during initialization.
     */
    public static final String TYPE_PROPERTY = "type";
    /**
     * Implementation version in free text form. SHOULD NOT be used to determine compatibility or
     * supported feature set. MAY be used to work around known bugs in specific implementations.
     */
    public static final String VERSION_PROPERTY = "version";
    /**
     * Serialization options affecting wire format (e.g., "hs" enables EventFlags storage).
     * Parsed into {@link ProtocolOption} set. Affects message encoding and decoding.
     */
    public static final String OPT_PROPERTY = "opt";
    /**
     * Timestamp format for event time embedding (e.g., "millis", "seconds", "nanos").
     * Indicates whether and how event times are present in the data stream.
     */
    public static final String TIME_PROPERTY = "time";
    /**
     * Human-readable endpoint name for logging and diagnostics.
     * Set via endpoint configuration; helps distinguish multiple connections in the same JVM.
     */
    public static final String NAME_PROPERTY = "name";
    /**
     * Connection filter in {@link com.devexperts.qd.QDFilter QDFilter} syntax. MAY be used to determine
     * what data the remote side will ignore, to avoid sending it for performance reasons.
     */
    public static final String FILTER_PROPERTY = "filter";
    /**
     * Symbol striping filter in {@link com.devexperts.qd.QDFilter QDFilter} syntax (using SymbolStriper).
     * Coordinates symbol partitioning across connections for load distribution.
     */
    public static final String STRIPE_PROPERTY = "stripe";
    /**
     * Available RMI services in ServiceFilter syntax. Set on per-message descriptors
     * (RMI_ADVERTISE_SERVICES) by RMI-enabled endpoints.
     */
    public static final String SERVICES_PROPERTY = "services";
    /**
     * Authorization token sent from client to server (e.g., "Basic base64..." or "Bearer token").
     * Masked in logs for security; only the scheme prefix is shown in diagnostic output.
     */
    public static final String AUTHORIZATION_PROPERTY = "authorization";
    /**
     * Authentication challenge/response between server and client during login handshake.
     * Server sends a reason or challenge; empty string signals successful acknowledgement.
     */
    public static final String AUTHENTICATION_PROPERTY = "authentication";
    /**
     * RMI endpoint role identifier (e.g., "CLIENT", "SERVER").
     * Enables the remote side to determine RMI role compatibility.
     */
    public static final String RMI_PROPERTY = "rmi";
    /**
     * Scheme identifier for HTTP Basic authentication, used as a prefix
     * in {@link #AUTHORIZATION_PROPERTY} values.
     */
    public static final String BASIC_AUTHORIZATION = "Basic";
    /**
     * Client's requested data aggregation period as a {@link com.devexperts.util.TimePeriod TimePeriod}
     * string (e.g., "1s", "0.5s"). Set per message type (TICKER_DATA, STREAM_DATA, HISTORY_DATA).
     * Server clamps to its configured bounds; empty string signals reset to server default.
     */
    public static final String REQUESTED_AGGREGATION_PERIOD_PROPERTY = "requestedAggregationPeriod";
    /**
     * Per-message property carrying the server's actual aggregation period as a {@link TimePeriodInfo}
     * JSON string: {@code {"min":1.5,"max":2.0}} (seconds with decimals).
     */
    public static final String AGGREGATION_PERIOD_INFO_PROPERTY = "aggregationPeriodInfo";

    private final Map<String, String> properties = new LinkedHashMap<>();
    private final IndexedMap<Integer, MessageDescriptor> send = IndexedMap.createInt(MessageDescriptor::getId);
    private final IndexedMap<Integer, MessageDescriptor> receive = IndexedMap.createInt(MessageDescriptor::getId);
    private EndpointId endpointId;

    private ProtocolDescriptor(ProtocolDescriptor previouslyRead) {
        if (previouslyRead != null) {
            properties.putAll(previouslyRead.properties);
            for (MessageDescriptor md : previouslyRead.send.values())
                send.put(new MessageDescriptor(this, md));
            for (MessageDescriptor md : previouslyRead.receive.values())
                receive.put(new MessageDescriptor(this, md));
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

    /**
     * Creates an empty protocol descriptor with no properties and no message descriptors.
     * Intended for parsing a single on-wire {@code DESCRIBE_PROTOCOL} message in isolation,
     * so the resulting descriptor reflects exactly the bytes received and can later be merged
     * into an accumulator via {@link #mergeFrom(ProtocolDescriptor)}.
     */
    public static ProtocolDescriptor newEmptyProtocolDescriptor() {
        return new ProtocolDescriptor(null);
    }

    public static ProtocolDescriptor newPeerProtocolDescriptorReadAs(ProtocolDescriptor original, MessageType readAs) {
        ProtocolDescriptor result = new ProtocolDescriptor(original);
        MessageDescriptor readAsMessage = new MessageDescriptor(result, readAs);
        for (Iterator<MessageDescriptor> it = result.send.values().iterator(); it.hasNext(); ) {
            MessageDescriptor md = it.next();
            if (md.getMessageType().hasRecords()) {
                readAsMessage.properties.putAll(md.properties);
                it.remove();
            }
        }
        result.send.put(readAsMessage);
        return result;
    }

    public MessageDescriptor newMessageDescriptor(MessageType messageType) {
        return new MessageDescriptor(this, messageType);
    }

    public MessageDescriptor newMessageDescriptor(int id, String name) {
        return new MessageDescriptor(this, id, name);
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
        return Collections.unmodifiableSet(new LinkedHashSet<>(send.values()));
    }

    public Set<MessageDescriptor> getReceiveMessages() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(receive.values()));
    }

    public String getProperty(String key) {
        return properties.get(key);
    }

    public void setProperty(String key, String value) {
        // Never remove — parser accumulates incrementally, so removal would not propagate to the receiving side.
        // Use empty string to signal "property cleared" on the wire.
        properties.put(key, value == null ? "" : value);
    }

    public boolean canSend(MessageType message) {
        for (MessageDescriptor md : send.values())
            if (md.getMessageType() == message)
                return true;
        return false;
    }

    public boolean canReceive(MessageType message) {
        for (MessageDescriptor md : receive.values())
            if (md.getMessageType() == message)
                return true;
        return false;
    }

    public EnumSet<MessageType> getSendSet() {
        EnumSet<MessageType> result = EnumSet.noneOf(MessageType.class);
        for (MessageDescriptor md : send.values()) {
            MessageType message = md.getMessageType();
            if (message != null)
                result.add(message);
        }
        return result;
    }

    public MessageDescriptor getSend(MessageType message) {
        for (MessageDescriptor md : send.values())
            if (md.getMessageType() == message)
                return md;
        return null;
    }

    public EnumSet<MessageType> getReceiveSet() {
        EnumSet<MessageType> result = EnumSet.noneOf(MessageType.class);
        for (MessageDescriptor md : receive.values()) {
            MessageType message = md.getMessageType();
            if (message != null)
                result.add(message);
        }
        return result;
    }

    public MessageDescriptor getReceive(MessageType message) {
        for (MessageDescriptor md : receive.values())
            if (md.getMessageType() == message)
                return md;
        return null;
    }

    public void addSend(MessageDescriptor message) {
        send.put(message);
    }

    public void addReceive(MessageDescriptor message) {
        receive.put(message);
    }

    /**
     * Merges {@code delta} into this descriptor with the same accumulation semantics as
     * {@link #parseFrom(BufferedInput)}: top-level properties are {@code putAll}-ed; per-message
     * descriptors with the same id have their properties {@code putAll}-ed into the existing one,
     * while new ids are added as reparented copies; a non-null {@code endpointId} overrides.
     */
    public void mergeFrom(ProtocolDescriptor delta) {
        properties.putAll(delta.properties);
        mergeMessages(send, delta.send);
        mergeMessages(receive, delta.receive);
        if (delta.endpointId != null)
            endpointId = delta.endpointId;
    }

    private void mergeMessages(IndexedMap<Integer, MessageDescriptor> target,
        IndexedMap<Integer, MessageDescriptor> source)
    {
        for (MessageDescriptor md : source.values()) {
            MessageDescriptor existing = target.getByKey(md.getId());
            if (existing != null) {
                existing.properties.putAll(md.properties);
            } else {
                target.put(new MessageDescriptor(this, md));
            }
        }
    }

    /**
     * Returns {@code current} as-is when {@code prev == null} (first emission is always
     * full); otherwise returns a descriptor containing only the top-level properties and
     * per-message properties whose values differ from {@code prev}.
     */
    public ProtocolDescriptor deltaFrom(ProtocolDescriptor prev) {
        if (prev == null)
            return this;
        ProtocolDescriptor delta = ProtocolDescriptor.newEmptyProtocolDescriptor();
        if (!Objects.equals(endpointId, prev.endpointId))
            delta.setEndpointId(getEndpointId());
        diffProps(properties, prev.properties, delta.properties);
        diffMessages(send, prev.send, delta, delta.send);
        diffMessages(receive, prev.receive, delta, delta.receive);
        return delta;
    }

    private static void diffMessages(IndexedMap<Integer, MessageDescriptor> newMessages,
        IndexedMap<Integer, MessageDescriptor> oldMessages,
        ProtocolDescriptor delta, IndexedMap<Integer, MessageDescriptor> sink)
    {
        for (MessageDescriptor newMsg : newMessages.values()) {
            // ignore possible difference of names and dissapiered messages
            MessageDescriptor oldMsg = oldMessages.getByKey(newMsg.getId());
            if (oldMsg == null) {
                sink.put(new MessageDescriptor(delta, newMsg));
                continue;
            }
            if (oldMsg.properties.equals(newMsg.properties))
                continue;
            MessageDescriptor changed = delta.newMessageDescriptor(newMsg.getMessageType());
            diffProps(newMsg.properties, oldMsg.properties, changed.properties);
            if (!changed.properties.isEmpty())
                sink.put(changed);
        }
    }

    private static void diffProps(Map<String, String> newProps, Map<String, String> oldProps,
        Map<String, String> deltaProps)
    {
        for (Map.Entry<String, String> e : newProps.entrySet()) {
            if (!Objects.equals(oldProps.get(e.getKey()), e.getValue()))
                deltaProps.put(e.getKey(), e.getValue() == null ? "" : e.getValue());
        }
    }

    /**
     * Composes this protocol descriptor in compact binary format
     * <b>including</b> its {@link #MAGIC_STRING}.
     */
    public void composeTo(BufferedOutput out) throws IOException {
        out.write(MAGIC_BYTES);
        out.writeProperties(properties);
        composeMessageListTo(out, send.values());
        composeMessageListTo(out, receive.values());
        if (endpointId != null)
            EndpointId.writeEndpointId(out, endpointId, null);
    }

    /**
     * Parses protocol descriptor from compact binary format
     * <b>including</b> its {@link #MAGIC_STRING}.
     * @throws IOException when descriptor cannot be read due to wrong magic or other reasons.
     */
    public void parseFrom(BufferedInput in) throws IOException {
        for (byte b : MAGIC_BYTES) {
            if (in.read() != b)
                throw new IOException("Invalid protocol descriptor magic. Wrong protocol.");
        }
        in.readProperties(properties);
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
        convertMessageListToTextTokens(tokens, "+", send.values());
        convertMessageListToTextTokens(tokens, "-", receive.values());
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

    private void composeMessageListTo(BufferedOutput out, Collection<MessageDescriptor> messages) throws IOException {
        out.writeCompactInt(messages.size());
        for (MessageDescriptor message : messages)
            message.composeTo(out);
    }

    private void parseMessageListFrom(BufferedInput in, IndexedMap<Integer, MessageDescriptor> messages)
        throws IOException
    {
        int size = in.readCompactInt();
        if (size < 0)
            throw new IOException("Invalid size: " + size);
        for (int i = 0; i < size; i++) {
            MessageDescriptor message = MessageDescriptor.parseFrom(this, in);
            mergeOrAdd(messages, message);
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

    private void convertMessageListToTextTokens(List<String> tokens, String prefix,
        Collection<MessageDescriptor> messages)
    {
        for (MessageDescriptor message : messages) {
            tokens.add(prefix + message.getName());
            convertPropertiesToTextTokens(tokens, message.properties);
        }
    }

    private int appendMessageListFromTextTokens(List<String> tokens, String prefix,
        IndexedMap<Integer, MessageDescriptor> messages, int i)
    {
        while (i < tokens.size() && tokens.get(i).startsWith(prefix)) {
            String name = tokens.get(i++).substring(prefix.length());
            MessageDescriptor message = new MessageDescriptor(this, name);
            i = appendPropertiesFromTextTokens(tokens, message.properties, i);
            mergeOrAdd(messages, message);
        }
        return i;
    }

    // Merges properties of incoming MessageDescriptor into existing one with same ID,
    // or adds it as new. Consistent with how main ProtocolDescriptor properties accumulate.
    private static void mergeOrAdd(IndexedMap<Integer, MessageDescriptor> messages, MessageDescriptor message) {
        MessageDescriptor existing = messages.getByKey(message.getId());
        if (existing != null) {
            existing.properties.putAll(message.properties);
        } else {
            messages.put(message);
        }
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
        sb.append(send.values());
        sb.append(" receiving ");
        sb.append(receive.values());
        return sb.toString();
    }
}


