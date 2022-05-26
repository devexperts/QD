/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf.impl;

import com.devexperts.io.CSVFormatException;
import com.devexperts.io.CSVReader;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileField;
import com.dxfeed.ipf.InstrumentProfileFormatException;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Parser for Instrument Profile Format.
 * Please see <b>Instrument Profile Format</b> documentation for complete description.
 */
public class InstrumentProfileParser implements Closeable {
    private static class Format {
        final String type;
        final int n;
        final String[] fields; // except TYPE field
        final InstrumentProfileField[] standardFields;
        final String[] fieldValues;
        final List<String> leftoverFields = new ArrayList<>();

        Format(String type, int n, List<String> tmpFields) {
            this.type = type.intern();
            this.n = n;
            fields = new String[n];
            standardFields = new InstrumentProfileField[n];
            fieldValues = new String[n];
            for (int i = 0; i < n; i++) {
                fields[i] = tmpFields.get(i).intern();
                standardFields[i] = InstrumentProfileField.find(fields[i]);
                fieldValues[i] = "";
            }
        }

        @Override
        public String toString() {
            return type + "=" + Arrays.toString(fields);
        }
    }

    private final Map<String, Format> formats = new HashMap<>();
    private final CSVReader reader;
    private final List<String> tmpFields = new ArrayList<>();
    private String prevF0 = "";
    
    private Runnable flushHandler;
    private Runnable completeHandler;
    private Function<String, String> internalizer = Function.identity();

    public InstrumentProfileParser(InputStream in) {
        reader = new CSVReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    /** Chained call to set {@link #onFlush()} handler, use {@code null} to remove handler. */
    public InstrumentProfileParser whenFlush(Runnable command) {
        flushHandler = command;
        return this;
    }

    /** Chained call to set {@link #onComplete()} handler, use {@code null} to remove handler. */
    public InstrumentProfileParser whenComplete(Runnable command) {
        completeHandler = command;
        return this;
    }

    /** Chained call to set {@link #intern(String) internalizer}, use {@code null} for no internalizing. */
    public InstrumentProfileParser withIntern(Function<String, String> internFunction) {
        internalizer = (internFunction == null) ? Function.identity() : internFunction;
        return this;
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    public InstrumentProfile next() throws IOException {
        try {
            while (true) {
                int line = reader.getLineNumber();
                String f0 = reader.readField(prevF0);
                Format format = formats.get(f0);
                if (format == null) {
                    if (parseSpecial(f0, line))
                        return null; // EOF reached
                    continue; // processed meta line or skip empty line
                }
                if (f0.isEmpty() && reader.isRecordEnded()) {
                    reader.readRecord(tmpFields); // readout current line
                    continue; // skip empty line
                }
                prevF0 = format.type; // remember interned type for faster lookup
                InstrumentProfile ip = new InstrumentProfile();
                ip.setType(format.type);
                for (int i = 0; i < format.n; i++) {
                    String oldValue = format.fieldValues[i];
                    String newValue = reader.readField(oldValue);
                    if (newValue == null)
                        throw new InstrumentProfileFormatException("wrong number of fields (line " + line + ")");
                    if (newValue.isEmpty())
                        continue; // do not waste time on setting empty fields
                    try {
                        InstrumentProfileField ipf = format.standardFields[i];
                        //noinspection StringEquality
                        if (newValue != oldValue) {
                            if (ipf == null || !ipf.isNumericField()) // do not intern numbers and dates
                                newValue = intern(newValue);
                            format.fieldValues[i] = newValue; // remember only non-empty [interned] fields
                        }
                        if (ipf != null)
                            ipf.setField(ip, newValue);
                        else
                            ip.setField(format.fields[i], newValue);
                    } catch (Exception e) {
                        throw new InstrumentProfileFormatException(e.getMessage() + " (line " + line + ")");
                    }
                }
                if (reader.readRecord(format.leftoverFields) != 0) // readout current line
                    throw new InstrumentProfileFormatException("wrong number of fields (line " + line + ")");
                return ip;
            }
        } catch (CSVFormatException e) {
            throw new InstrumentProfileFormatException(e.getMessage());
        }
    }

    private boolean parseSpecial(String f0, int line) throws IOException {
        if (f0 == null)
            return reader.readRecord(tmpFields) < 0; // readout current line; detect EOF
        if (f0.startsWith(Constants.METADATA_PREFIX))
            parseMeta(f0);
        else
            checkUndefinedFormat(f0, line);
        return false;
    }

    private void checkUndefinedFormat(String f0, int line) throws IOException {
        int n = reader.readRecord(tmpFields); // readout current line
        if (f0.isEmpty() && n <= 0)
            return; // skip empty line
        throw new InstrumentProfileFormatException("undefined format " + (f0.isEmpty() ? "\"\"" : f0) + " (line " + line + ")");
    }

    private void parseMeta(String f0) throws IOException {
        if (f0.endsWith(Constants.METADATA_SUFFIX)) {
            parseFormat(f0);
            return;
        }
        reader.readRecord(tmpFields); // readout current line
        if (f0.equals(Constants.FLUSH_COMMAND)) {
            onFlush();
        } else if (f0.equals(Constants.COMPLETE_COMMAND)) {
            onComplete();
        }
        // else it is a comment - skip silently
    }

    private void parseFormat(String f0) throws IOException {
        String type = f0.substring(Constants.METADATA_PREFIX.length(), f0.length() - Constants.METADATA_SUFFIX.length());
        Format format = formats.get(type);
        if (format != null) {
            // pre-populate tmpFields with old format
            while (tmpFields.size() < format.n) // defensive; shall not happen as format was created via tmpFields
                tmpFields.add("");
            for (int i = 0; i < format.n; i++)
                tmpFields.set(i, format.fields[i]);
        }
        int n = reader.readRecord(tmpFields); // readout current line
        if (n < 0)
            return; // EOF reached
        if (format != null && n == format.n) {
            boolean sameFormat = true;
            for (int i = 0; sameFormat && i < n; i++)
                sameFormat = tmpFields.get(i).equals(format.fields[i]);
            if (sameFormat)
                return; // new format is equal to old format
        }
        if (type.startsWith(Constants.METADATA_PREFIX) && type.endsWith(Constants.METADATA_SUFFIX) || type.equals(Constants.FLUSH_COMMAND) || type.equals(Constants.COMPLETE_COMMAND))
            throw new InstrumentProfileFormatException("forbidden format " + type);
        if (type.isEmpty() && n == 0)
            return; // empty type + empty format == empty line; always skipped
        format = new Format(type, n, tmpFields);
        formats.put(format.type, format);
    }

    protected String intern(String value) {
        return internalizer.apply(value);
    }

    protected void onFlush() {
        if (flushHandler != null) {
            flushHandler.run();
        }
    }

    protected void onComplete() {
        if (completeHandler != null) {
            completeHandler.run();
        }
    }
}
