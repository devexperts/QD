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
package com.devexperts.qd.qtp.test;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.qd.qtp.MessageDescriptor;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Tests for {@link ProtocolDescriptor} serialization round-trip, property semantics,
 * and deep clone behavior — parameterized across binary and text protocols.
 *
 * <p>Property three-state semantics:
 * <ul>
 *   <li>{@code null} (never set) — property absent, not sent on wire</li>
 *   <li>{@code ""} (empty) — property reset, sent on wire to override previous value</li>
 *   <li>{@code "value"} — property set, sent on wire</li>
 * </ul>
 *
 * <p>Once a property has been set, {@code setProperty(key, null)} stores {@code ""} (reset),
 * not removes — because the incremental protocol accumulates properties on the receiver side,
 * so omitting a property would leave the old value intact.
 */
@RunWith(Parameterized.class)
public class ProtocolDescriptorTest {

    enum Format { BINARY, TEXT }

    @Parameters(name = "{0}")
    public static Format[] data() {
        return Format.values();
    }

    private final Format format;

    public ProtocolDescriptorTest(Format format) {
        this.format = format;
    }

    // ==================== Round-trip: properties preserved ====================

    @Test
    public void testRoundTripPreservesProtocolAndMessageProperties() throws IOException {
        ProtocolDescriptor original = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        original.setProperty("custom", "value1");

        MessageDescriptor tickerData = original.newMessageDescriptor(MessageType.TICKER_DATA);
        tickerData.setProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY, "1s");
        original.addSend(tickerData);

        ProtocolDescriptor parsed = roundTrip(original, null);

        assertEquals("value1", parsed.getProperty("custom"));
        MessageDescriptor parsedTicker = parsed.getSend(MessageType.TICKER_DATA);
        assertNotNull(parsedTicker);
        assertEquals("1s", parsedTicker.getProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY));
    }

    @Test
    public void testRoundTripPreservesEmptyString() throws IOException {
        ProtocolDescriptor original = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        original.setProperty("key", "");

        ProtocolDescriptor parsed = roundTrip(original, null);

        assertEquals("", parsed.getProperty("key"));
    }

    @Test
    public void testRoundTripMultipleMessageTypesPreserved() throws IOException {
        ProtocolDescriptor original = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");

        MessageDescriptor ticker = original.newMessageDescriptor(MessageType.TICKER_DATA);
        ticker.setProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY, "1s");
        original.addSend(ticker);

        MessageDescriptor stream = original.newMessageDescriptor(MessageType.STREAM_DATA);
        stream.setProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY, "0.5s");
        original.addSend(stream);

        MessageDescriptor history = original.newMessageDescriptor(MessageType.HISTORY_DATA);
        history.setProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY, "2s");
        original.addSend(history);

        ProtocolDescriptor parsed = roundTrip(original, null);

        assertEquals("1s",
            parsed.getSend(MessageType.TICKER_DATA).getProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY));
        assertEquals("0.5s",
            parsed.getSend(MessageType.STREAM_DATA).getProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY));
        assertEquals("2s",
            parsed.getSend(MessageType.HISTORY_DATA).getProperty(ProtocolDescriptor.REQUESTED_AGGREGATION_PERIOD_PROPERTY));
    }

    // ==================== Incremental accumulation ====================

    @Test
    public void testIncrementalPropertyAccumulation() throws IOException {
        // First: propA on TICKER_DATA, connProp on protocol
        ProtocolDescriptor first = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        first.setProperty("connProp", "initial");
        MessageDescriptor ticker1 = first.newMessageDescriptor(MessageType.TICKER_DATA);
        ticker1.setProperty("propA", "valueA");
        first.addSend(ticker1);

        ProtocolDescriptor accumulated = roundTrip(first, null);
        assertEquals("initial", accumulated.getProperty("connProp"));
        assertEquals("valueA", accumulated.getSend(MessageType.TICKER_DATA).getProperty("propA"));

        // Second: propB on same TICKER_DATA, updated connProp
        ProtocolDescriptor second = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        second.setProperty("connProp", "updated");
        MessageDescriptor ticker2 = second.newMessageDescriptor(MessageType.TICKER_DATA);
        ticker2.setProperty("propB", "valueB");
        second.addSend(ticker2);

        ProtocolDescriptor merged = roundTrip(second, accumulated);

        assertEquals("updated", merged.getProperty("connProp"));
        MessageDescriptor mergedTicker = merged.getSend(MessageType.TICKER_DATA);
        assertNotNull(mergedTicker);
        assertEquals("valueA", mergedTicker.getProperty("propA"));
        assertEquals("valueB", mergedTicker.getProperty("propB"));
    }

    @Test
    public void testIncrementalPropertyOverwrite() throws IOException {
        ProtocolDescriptor first = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        MessageDescriptor ticker1 = first.newMessageDescriptor(MessageType.TICKER_DATA);
        ticker1.setProperty("propA", "old");
        first.addSend(ticker1);

        ProtocolDescriptor accumulated = roundTrip(first, null);

        ProtocolDescriptor second = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        MessageDescriptor ticker2 = second.newMessageDescriptor(MessageType.TICKER_DATA);
        ticker2.setProperty("propA", "new");
        second.addSend(ticker2);

        ProtocolDescriptor merged = roundTrip(second, accumulated);
        assertEquals("new", merged.getSend(MessageType.TICKER_DATA).getProperty("propA"));
    }

    @Test
    public void testIncrementalEmptyOverridesPreviousValue() throws IOException {
        // First: key=value
        ProtocolDescriptor first = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        first.setProperty("key", "value");
        ProtocolDescriptor accumulated = roundTrip(first, null);
        assertEquals("value", accumulated.getProperty("key"));

        // Second: key="" (reset)
        ProtocolDescriptor second = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        second.setProperty("key", "");
        ProtocolDescriptor merged = roundTrip(second, accumulated);

        assertEquals("", merged.getProperty("key"));
    }

    @Test
    public void testIncrementalOmittedPropertyKeepsPreviousValue() throws IOException {
        // First: key=value
        ProtocolDescriptor first = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        first.setProperty("key", "value");
        ProtocolDescriptor accumulated = roundTrip(first, null);

        // Second: omits key entirely
        ProtocolDescriptor second = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        ProtocolDescriptor merged = roundTrip(second, accumulated);

        assertEquals("value", merged.getProperty("key"));
    }

    @Test
    public void testIncrementalNewValueOverridesPreviousEmpty() throws IOException {
        // First: key="" (reset)
        ProtocolDescriptor first = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        first.setProperty("key", "");
        ProtocolDescriptor accumulated = roundTrip(first, null);

        // Second: key=newValue
        ProtocolDescriptor second = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        second.setProperty("key", "newValue");
        ProtocolDescriptor merged = roundTrip(second, accumulated);

        assertEquals("newValue", merged.getProperty("key"));
    }

    // ==================== Three-state property semantics ====================

    @Test
    public void testPropertyNotSetReturnsNull() {
        ProtocolDescriptor pd = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        assertNull(pd.getProperty("absent"));
    }

    @Test
    public void testSetPropertyNullAfterValueStoresEmpty() {
        ProtocolDescriptor pd = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        pd.setProperty("key", "value");
        assertEquals("value", pd.getProperty("key"));

        pd.setProperty("key", null);
        assertNotNull(pd.getProperty("key"));
        assertEquals("", pd.getProperty("key"));
    }

    // ==================== Clone (deep copy) ====================

    @Test
    public void testCloneDeepCopiesMessageDescriptorProperties() {
        ProtocolDescriptor original = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        MessageDescriptor ticker = original.newMessageDescriptor(MessageType.TICKER_DATA);
        ticker.setProperty("propA", "original");
        original.addSend(ticker);

        ProtocolDescriptor clone = ProtocolDescriptor.newPeerProtocolDescriptor(original);

        MessageDescriptor clonedTicker = clone.getSend(MessageType.TICKER_DATA);
        assertNotNull(clonedTicker);
        clonedTicker.setProperty("propA", "modified");

        assertEquals("original", original.getSend(MessageType.TICKER_DATA).getProperty("propA"));
        assertEquals("modified", clone.getSend(MessageType.TICKER_DATA).getProperty("propA"));
    }

    // ==================== Message-level property inheritance ====================

    @Test
    public void testMessagePropertyInheritsFromProtocol() {
        ProtocolDescriptor pd = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        pd.setProperty("shared", "fromProtocol");

        MessageDescriptor md = pd.newMessageDescriptor(MessageType.TICKER_DATA);
        pd.addSend(md);

        assertEquals("fromProtocol", md.getProperty("shared"));
    }

    @Test
    public void testMessagePropertyOverridesProtocol() {
        ProtocolDescriptor pd = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        pd.setProperty("shared", "fromProtocol");

        MessageDescriptor md = pd.newMessageDescriptor(MessageType.TICKER_DATA);
        md.setProperty("shared", "fromMessage");
        pd.addSend(md);

        assertEquals("fromMessage", md.getProperty("shared"));
    }

    @Test
    public void testMessagePropertyEmptyOverridesProtocol() {
        ProtocolDescriptor pd = ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
        pd.setProperty("shared", "fromProtocol");

        MessageDescriptor md = pd.newMessageDescriptor(MessageType.TICKER_DATA);
        md.setProperty("shared", "");
        pd.addSend(md);

        assertEquals("", md.getProperty("shared"));
    }

    // ==================== Helpers ====================

    private ProtocolDescriptor roundTrip(ProtocolDescriptor source, ProtocolDescriptor previouslyRead)
        throws IOException
    {
        switch (format) {
            case BINARY:
                return binaryRoundTrip(source, previouslyRead);
            case TEXT:
                return textRoundTrip(source, previouslyRead);
            default:
                throw new AssertionError("Unknown format: " + format);
        }
    }

    private ProtocolDescriptor binaryRoundTrip(ProtocolDescriptor source, ProtocolDescriptor previouslyRead)
        throws IOException
    {
        ByteArrayOutput out = new ByteArrayOutput();
        source.composeTo(out);

        ByteArrayInput in = new ByteArrayInput(out.getBuffer(), 0, out.getPosition());
        ProtocolDescriptor result = ProtocolDescriptor.newPeerProtocolDescriptor(previouslyRead);
        result.parseFrom(in);
        return result;
    }

    private ProtocolDescriptor textRoundTrip(ProtocolDescriptor source, ProtocolDescriptor previouslyRead) {
        List<String> tokens = source.convertToTextTokens();

        ProtocolDescriptor result = ProtocolDescriptor.newPeerProtocolDescriptor(previouslyRead);
        result.appendFromTextTokens(tokens, 0);
        return result;
    }
}
