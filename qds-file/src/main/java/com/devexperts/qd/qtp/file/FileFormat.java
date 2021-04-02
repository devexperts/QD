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
package com.devexperts.qd.qtp.file;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.qtp.AbstractQTPComposer;
import com.devexperts.qd.qtp.AbstractQTPParser;
import com.devexperts.qd.qtp.BinaryQTPComposer;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.blob.BlobQTPComposer;
import com.devexperts.qd.qtp.blob.BlobQTPParser;
import com.devexperts.qd.qtp.text.TextDelimiters;
import com.devexperts.qd.qtp.text.TextQTPComposer;
import com.devexperts.qd.qtp.text.TextQTPParser;
import com.devexperts.util.InvalidFormatException;

import java.util.Locale;

/**
 * QDS file format specification.
 */
public class FileFormat {
    public enum Type {
        BINARY, TEXT, CSV, BLOB;
    }

    public static final FileFormat BINARY = new FileFormat(Type.BINARY, null, null);
    public static final FileFormat TEXT = new FileFormat(Type.TEXT, null, null);
    public static final FileFormat CSV = new FileFormat(Type.CSV, null, null);

    private final Type type;
    private final String record;
    private final String symbol;

    /**
     * Construct <code>FileFormat</code> by a given string description.
     * The format description is "binary", "text", or "blob:&lt;record&gt;:&lt;symbol&gt;".
     * @throws InvalidFormatException when desc has invalid format.
     */
    public static FileFormat valueOf(String desc) {
        if (desc == null)
            return null;
        String[] ss = desc.split(":");
        Type type;
        try {
            type = Type.valueOf(ss[0].toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            throw new InvalidFormatException("Wrong format specification \"" + ss[0] + "\"");
        }
        if (type == Type.BLOB) {
            if (ss.length != 3)
                throw new InvalidFormatException("Wrong format specification for BLOB. Must be \"blob:<record>:<symbol>\"");
            return new FileFormat(type, ss[1], ss[2]);
        } else {
            if (ss.length != 1)
                throw new InvalidFormatException("Wrong format specification for " + type + ". Cannot have additional options");
            return new FileFormat(type, null, null);
        }
    }

    public static FileFormat detectFormat(byte[] buffer) {
        if (isTextFileInBuffer(buffer, TextDelimiters.TAB_SEPARATED))
            return TEXT;
        if (isTextFileInBuffer(buffer, TextDelimiters.COMMA_SEPARATED))
            return CSV;
        return BINARY;
    }

    private static boolean isTextFileInBuffer(byte[] buffer, TextDelimiters delimiters) {
        String s = delimiters.messageTypePrefix;
        if (s == null) // for CSV bareBones format
            s = delimiters.describePrefix;
        int n = s.length();
        if (buffer.length < n)
            return false;
        for (int i = 0; i < n; i++)
            if (buffer[i] != s.charAt(i))
                return false;
        return true;
    }

    private FileFormat(Type type, String record, String symbol) {
        this.type = type;
        this.record = record;
        this.symbol = symbol;
    }

    public Type getType() {
        return type;
    }

    /**
     * Returns {@code true} for "bare bones" formats that have no describe protocol header and no heartbeat footer.
     */
    public boolean isBareBones() {
        return type == Type.CSV || type == Type.BLOB;
    }

    private DataRecord getRecord(DataScheme scheme) {
        return scheme.findRecordByName(record);
    }

    /**
     * Creates QTP parser for this format.
     */
    public AbstractQTPParser createQTPParser(DataScheme scheme) {
        switch (type) {
        case BINARY:
            return new BinaryFileQTPParser(scheme);
        case TEXT:
            return new TextQTPParser(scheme, null);
        case CSV:
            TextQTPParser parser = new TextQTPParser(scheme, MessageType.STREAM_DATA);
            parser.setDelimiters(TextDelimiters.COMMA_SEPARATED);
            return parser;
        case BLOB:
            return new BlobQTPParser(getRecord(scheme), symbol);
        default:
            throw new AssertionError();
        }
    }

    /**
     * Creates QTP composer for this format that is configured with a
     * {@link #getTimestampsType() default timestamps type}.
     */
    public AbstractQTPComposer createQTPComposer(DataScheme scheme) {
        AbstractQTPComposer composer;
        switch (type) {
        case BINARY:
            composer = new BinaryQTPComposer(scheme, true);
            composer.setWriteHeartbeat(true); // this is how times are represented in binary by default
            return composer;
        case TEXT:
            composer = new TextQTPComposer(scheme);
            composer.setWriteEventTimeSequence(true); // this is how times are represented in text by default
            return composer;
        case CSV:
            TextQTPComposer textComposer = new TextQTPComposer(scheme);
            textComposer.setWriteEventTimeSequence(true); // this is how times are represented in csv by default
            textComposer.setDelimiters(TextDelimiters.COMMA_SEPARATED);
            return textComposer;
        case BLOB:
            return new BlobQTPComposer(getRecord(scheme), symbol);
        default:
            throw new AssertionError();
        }
    }

    /**
     * Returns default timestamps type for this format.
     */
    public TimestampsType getTimestampsType() {
        switch (type) {
        case BINARY:
            return TimestampsType.MESSAGE;
        case TEXT:
        case CSV:
            return TimestampsType.FIELD;
        case BLOB:
            return TimestampsType.NONE;
        default:
            throw new AssertionError();
        }
    }

    public String getContentType() {
        switch (type) {
        case BINARY:
            return "application/x.qds.binary"; // unregistered tree
        case TEXT:
            return "text/plain; charset=UTF-8";
        case CSV:
            return "text/csv; charset=UTF-8";
        case BLOB:
            return "application/x.qds.blob";
        default:
            throw new AssertionError();
        }
    }

    public String toString() {
        return symbol == null ? type.toString() : type + ":" + record + ":" + symbol;
    }
}
