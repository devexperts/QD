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
import static org.junit.Assert.assertNull;

/**
 * Tests {@link ProtocolDescriptor} property semantics in both binary and text protocol formats.
 *
 * <p>Properties have three-state semantics on the wire:
 * <ul>
 *   <li>{@code null} — never set, not sent on wire</li>
 *   <li>{@code ""} — explicitly cleared, sent on wire to override old value</li>
 *   <li>{@code "value"} — set, sent on wire</li>
 * </ul>
 *
 * <p>DESCRIBE_PROTOCOL messages accumulate properties incrementally: re-sent descriptors
 * merge into the previous state. Omitted properties keep their old values; empty string
 * overrides the old value (acts as "clear").
 */
@RunWith(Parameterized.class)
public class ProtocolDescriptorPropertyTest {

    enum Format { BINARY, TEXT }

    @Parameters(name = "{0}")
    public static Format[] data() {
        return Format.values();
    }

    private final Format format;

    public ProtocolDescriptorPropertyTest(Format format) {
        this.format = format;
    }

    // ==================== Wire round-trip ====================

    @Test
    public void testValueSurvivesRoundTrip() throws IOException {
        ProtocolDescriptor pd = newDescriptor();
        pd.setProperty("key", "value");

        assertEquals("value", roundTrip(pd, null).getProperty("key"));
    }

    @Test
    public void testEmptyStringSurvivesRoundTrip() throws IOException {
        ProtocolDescriptor pd = newDescriptor();
        pd.setProperty("key", "");

        assertEquals("", roundTrip(pd, null).getProperty("key"));
    }

    @Test
    public void testAbsentPropertyNotSent() throws IOException {
        ProtocolDescriptor pd = newDescriptor();
        // "absent" never set

        assertNull(roundTrip(pd, null).getProperty("absent"));
    }

    // ==================== Incremental accumulation ====================
    //
    // Simulates multiple DESCRIBE_PROTOCOL messages on the same connection.
    // Each roundTrip(desc, previouslyRead) merges into previouslyRead.

    /** value → omit: omitting a property preserves the old value. */
    @Test
    public void testOmittedPropertyKeepsPreviousValue() throws IOException {
        // 1st message: key=value
        ProtocolDescriptor first = newDescriptor();
        first.setProperty("key", "value");
        ProtocolDescriptor state = roundTrip(first, null);

        // 2nd message: key omitted
        ProtocolDescriptor second = newDescriptor();
        ProtocolDescriptor merged = roundTrip(second, state);

        assertEquals("value", merged.getProperty("key")); // preserved
    }

    /** value → "": empty string explicitly clears the previous value. */
    @Test
    public void testEmptyOverridesPreviousValue() throws IOException {
        ProtocolDescriptor first = newDescriptor();
        first.setProperty("key", "value");
        ProtocolDescriptor state = roundTrip(first, null);

        ProtocolDescriptor second = newDescriptor();
        second.setProperty("key", "");
        ProtocolDescriptor merged = roundTrip(second, state);

        assertEquals("", merged.getProperty("key")); // cleared
    }

    /** "" → "new": new value overrides the previous empty. */
    @Test
    public void testNewValueOverridesPreviousEmpty() throws IOException {
        ProtocolDescriptor first = newDescriptor();
        first.setProperty("key", "");
        ProtocolDescriptor state = roundTrip(first, null);

        ProtocolDescriptor second = newDescriptor();
        second.setProperty("key", "newValue");
        ProtocolDescriptor merged = roundTrip(second, state);

        assertEquals("newValue", merged.getProperty("key"));
    }

    // ==================== setProperty(key, null) semantics ====================

    /** After a value was set, setProperty(key, null) stores "" (not removes). */
    @Test
    public void testSetNullAfterValueStoresEmpty() {
        ProtocolDescriptor pd = newDescriptor();
        pd.setProperty("key", "value");
        pd.setProperty("key", null);

        assertEquals("", pd.getProperty("key"));
    }

    // ==================== Message-level properties ====================

    @Test
    public void testMessageInheritsProtocolProperty() {
        ProtocolDescriptor pd = newDescriptor();
        pd.setProperty("shared", "fromProtocol");

        MessageDescriptor md = pd.newMessageDescriptor(MessageType.TICKER_DATA);
        pd.addSend(md);

        assertEquals("fromProtocol", md.getProperty("shared"));
    }

    @Test
    public void testMessageOverridesProtocolProperty() {
        ProtocolDescriptor pd = newDescriptor();
        pd.setProperty("shared", "fromProtocol");

        MessageDescriptor md = pd.newMessageDescriptor(MessageType.TICKER_DATA);
        md.setProperty("shared", "fromMessage");
        pd.addSend(md);

        assertEquals("fromMessage", md.getProperty("shared"));
    }

    @Test
    public void testMessageEmptyOverridesProtocolProperty() {
        ProtocolDescriptor pd = newDescriptor();
        pd.setProperty("shared", "fromProtocol");

        MessageDescriptor md = pd.newMessageDescriptor(MessageType.TICKER_DATA);
        md.setProperty("shared", "");
        pd.addSend(md);

        assertEquals("", md.getProperty("shared"));
    }

    // ==================== Helpers ====================

    private static ProtocolDescriptor newDescriptor() {
        return ProtocolDescriptor.newSelfProtocolDescriptor("qtp");
    }

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
