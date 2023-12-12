/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.text;

import com.devexperts.io.BufferedOutput;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataField;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.kit.VoidIntField;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.AbstractQTPComposer;
import com.devexperts.qd.qtp.BuiltinFields;
import com.devexperts.qd.qtp.HeartbeatPayload;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.RuntimeQTPException;
import com.devexperts.qd.util.TimeSequenceUtil;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimeFormat;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * Composes QTP messages in text format into byte stream.
 * The output for this composer must be configured with {@link #setOutput(BufferedOutput)} method
 * immediately after construction.
 *
 * @see AbstractQTPComposer
 */
public class TextQTPComposer extends AbstractQTPComposer {

    private static final boolean USE_LEGACY_QD_PREFIX = // note -- legacy property names (see ReleaseNotes.txt)
        SystemProperties.getBooleanProperty("com.devexperts.qd.qtp.text.TextByteArrayComposer.useLegacyQDPrefix", false);

    private static final boolean USE_LOCAL_NAMES = // note -- legacy property names (see ReleaseNotes.txt)
        SystemProperties.getBooleanProperty("com.devexperts.qd.qtp.text.TextByteArrayComposer.useLocalNames", false);

    private static final Logging log = Logging.getLogging(TextQTPComposer.class);

    private static final byte[] lineSeparator;

    static {
        try {
            lineSeparator = SystemProperties.getProperty("line.separator", "\n").getBytes("ASCII");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    // ======================== private instance fields ========================

    private MessageType currentMessageType;
    private MessageType prevMessageType;
    private boolean newLine;
    private TextDelimiters delimiters = TextDelimiters.TAB_SEPARATED;

    // ======================== constructor and instance methods ========================

    /**
     * Constructs text composer.
     * You must {@link #setOutput(BufferedOutput) setOutput} before using this composer.
     *
     * @param scheme the data scheme.
     */
    public TextQTPComposer(DataScheme scheme) {
        super(scheme, true);
        newLine = true;
    }

    // ------------------------ configuration methods ------------------------

    public void setDelimiters(TextDelimiters delimiters) {
        this.delimiters = delimiters;
    }

    // extension point
    protected boolean acceptField(DataField f) {
        return !(f instanceof VoidIntField);
    }

    // ------------------------ session control ------------------------

    @Override
    public void resetSession() {
        super.resetSession();
        currentMessageType = null;
        newLine = true;
    }

    // ------------------------ impl methods to write special messages ------------------------

    @Override
    protected void writeDescribeProtocolMessage(BufferedOutput out, ProtocolDescriptor descriptor) throws IOException {
        String prefix = delimiters.messageTypePrefix;
        if (prefix == null)
            return; // does not support protocol descriptor
        write(prefix + ProtocolDescriptor.MAGIC_STRING);
        for (String token : descriptor.convertToTextTokens()) {
            separator();
            write(token);
        }
        writeln();
        finishComposingMessage(out); // flush written message to out
    }

    @Override
    protected void writeEmptyHeartbeatMessage(BufferedOutput out) throws IOException {
        String prefix = delimiters.messageTypePrefix;
        if (prefix == null)
            return; // does not support heartbeat
        write(prefix);
        writeln();
        finishComposingMessage(out); // flush written message to out
    }

    @Override
    protected void writeHeartbeatMessage(BufferedOutput out, HeartbeatPayload heartbeatPayload) throws IOException {
        String prefix = delimiters.messageTypePrefix;
        if (prefix == null)
            return; // does not support heartbeat
        // don't do beginMessage(...) because we don't want to affect current message type
        write(prefix);
        for (String token : heartbeatPayload.convertToTextTokens()) {
            separator();
            write(token);
        }
        writeln();
        finishComposingMessage(out); // flush written message to out
    }

    // ------------------------ impl methods to write protocol elements ------------------------

    @Override
    protected void writeMessageHeader(MessageType messageType) {
        writeln();
        prevMessageType = currentMessageType;
        if (currentMessageType == messageType)
            return;
        currentMessageType = messageType;
        if (messageType != null) {
            String prefix = delimiters.messageTypePrefix;
            if (prefix != null) {
                write(prefix + (USE_LEGACY_QD_PREFIX ? TextDelimiters.LEGACY_QD_PREFIX : "") + messageType.toString());
                writeln();
            }
        } else {
            log.error("Unknown message in composer: " + messageType);
        }
    }

    @Override
    protected void undoWriteMessageHeaderStateChange() {
        currentMessageType = prevMessageType;
        prevMessageType = null;
    }

    @Override
    protected void finishComposingMessage(BufferedOutput out) throws IOException {
        writeln();
        super.finishComposingMessage(out);
    }

    @Override
    protected void writeHistorySubscriptionTime(DataRecord record, long time) {
        separator();
        if (!record.hasTime()) {
            throw new IllegalArgumentException("Met history subscription for record with no time coordinate.");
        }
        if (time != 0) {
            writeIntField(record.getIntField(0), (int) (time >>> 32));
            writeIntField(record.getIntField(1), (int) (time));
        }
    }

    @Override
    protected int writeRecordHeader(DataRecord record, int cipher, String symbol, int eventFlags) {
        writeln();
        write(record.getName());
        separator();
        write(record.getScheme().getCodec().decode(cipher, symbol));
        return eventFlags;
    }

    @Override
    protected void writeRecordPayload(RecordCursor cursor, int eventFlags) throws IOException {
        super.writeRecordPayload(cursor, eventFlags);
        if (eventFlags != 0) {
            String s = EventFlag.formatEventFlags(eventFlags, currentMessageType);
            if (!s.isEmpty()) {
                separator();
                write(BuiltinFields.EVENT_FLAGS_FIELD_NAME + "=" + s);
            }
        }
    }

    @Override
    protected void writeEventTimeSequence(long eventTimeSequence) throws IOException {
        separator();
        write(TimeFormat.DEFAULT.withTimeZone().withMillis().format(
            TimeSequenceUtil.getTimeMillisFromTimeSequence(eventTimeSequence)));
    }

    @Override
    protected void writeIntField(DataIntField field, int value) {
        if (acceptField(field)) {
            separator();
            write(field.toString(value));
        }
    }

    @Override
    protected void writeObjField(DataObjField field, Object value) {
        if (acceptField(field)) {
            separator();
            write(field.toString(value));
        }
    }

    @Override
    protected void writeField(DataField field, RecordCursor cursor) {
        if (acceptField(field)) {
            separator();
            write(field.getString(cursor));
        }
    }

    // ------------------------ describe records support ------------------------

    @Override
    protected void describeRecord(DataRecord record) {
        writeln();
        writeDescribeRecordLine(record);
    }

    protected final void writeDescribeRecordLine(DataRecord record) {
        write(delimiters.describePrefix + record.getName());
        separator();
        write(USE_LOCAL_NAMES ? BuiltinFields.SYMBOL_FIELD_NAME : BuiltinFields.EVENT_SYMBOL_FIELD_NAME);
        if (writeEventTimeSequence) {
            separator();
            write(BuiltinFields.EVENT_TIME_FIELD_NAME);
        }
        for (int i = 0; i < record.getIntFieldCount(); i++) {
            DataIntField field = record.getIntField(i);
            if (acceptField(field)) {
                separator();
                write(USE_LOCAL_NAMES ? field.getLocalName() : field.getPropertyName());
            }
        }
        for (int i = 0; i < record.getObjFieldCount(); i++) {
            DataObjField field = record.getObjField(i);
            if (acceptField(field)) {
                separator();
                write(USE_LOCAL_NAMES ? field.getLocalName() : field.getPropertyName());
            }
        }
        writeln();
    }

    // ------------------------ helper methods (lowest-level) ------------------------

    protected void write(String token) {
        token = TextCoding.encode(token);
        boolean quote = token.indexOf(' ') >= 0 || token.isEmpty() || token.indexOf(delimiters.fieldSeparatorChar) >= 0;
        try {
            if (quote)
                msg.write('\"');
            msg.writeBytes(token);
            if (quote)
                msg.write('\"');
        } catch (IOException e) {
            throw new RuntimeQTPException(e);
        }
        newLine = false;
    }

    protected void writeln() {
        if (!newLine) {
            try {
                msg.write(lineSeparator);
            } catch (IOException e) {
                throw new RuntimeQTPException(e);
            }
            newLine = true;
        }
    }

    protected void separator() {
        try {
            msg.writeByte(delimiters.fieldSeparatorChar);
        } catch (IOException e) {
            throw new RuntimeQTPException(e);
        }
        newLine = false;
    }

}
