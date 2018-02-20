/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf.impl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import com.devexperts.io.CSVWriter;
import com.dxfeed.ipf.*;

/**
 * Composer for Instrument Profile Simple File Format.
 * Please see <b>Instrument Profile Format</b> documentation for complete description.
 */
public class InstrumentProfileComposer implements Closeable {
    private static final InstrumentProfileField[] FIELDS = InstrumentProfileField.values();
    private static final String REMOVED_TYPE = InstrumentProfileType.REMOVED.name();

    private final Map<String, EnumSet<InstrumentProfileField>> enumFormats = new HashMap<>();
    private final Map<String, TreeSet<String>> customFormats = new HashMap<>();
    private final List<String> types = new ArrayList<>(); // atomically captures types
    private final CSVWriter writer;

    public InstrumentProfileComposer(OutputStream out) {
        writer = new CSVWriter(new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)));
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }

    // atomically captures profile types to work correctly when profile type is being changed concurrently,
    // otherwise, the method is not thread-safe.
    // when skipRemoved == true, it ignores removed instruments when composing.
    public void compose(List<InstrumentProfile> profiles, boolean skipRemoved) throws IOException {
        captureTypes(profiles);
        writeFormats(profiles, skipRemoved);
        writeProfiles(profiles, skipRemoved);
        types.clear();
    }

    public void composeNewLine() throws IOException {
        writer.writeRecord(new String[] {""}); // Force CRLF
        writer.flush();
    }

    public void composeFlush() throws IOException {
        writer.writeRecord(new String[] {Constants.FLUSH_COMMAND});
        composeNewLine();
    }

    public void composeComplete() throws IOException {
        writer.writeRecord(new String[] {Constants.COMPLETE_COMMAND});
        composeNewLine();
    }

    private void captureTypes(List<InstrumentProfile> profiles) {
        types.clear();
        for (InstrumentProfile ip : profiles)
            types.add(ip.getType());
    }

    private void writeFormats(List<InstrumentProfile> profiles, boolean skipRemoved) throws IOException {
        Set<String> updated = new TreeSet<>();
        for (int i = 0; i < profiles.size(); i++) {
            String type = types.get(i); // atomically captured
            if (REMOVED_TYPE.equals(type) && skipRemoved)
                continue;
            InstrumentProfile ip = profiles.get(i);
            EnumSet<InstrumentProfileField> enumFormat = enumFormats.get(type);
            TreeSet<String> customFormat = customFormats.get(type);
            if (enumFormat == null) {
                updated.add(type);
                // always write symbol (type is always written by a special code)
                enumFormats.put(type, enumFormat = EnumSet.of(InstrumentProfileField.SYMBOL));
                customFormats.put(type, customFormat = new TreeSet<>());
            }
            if (!REMOVED_TYPE.equals(type)) {
                // collect actual used fields for non-removed instrument profiles
                for (InstrumentProfileField ipf : FIELDS)
                    if (ipf != InstrumentProfileField.TYPE && ipf.getField(ip).length() > 0)
                        if (enumFormat.add(ipf))
                            updated.add(type);
                if (ip.addNonEmptyCustomFieldNames(customFormat))
                    updated.add(type);
            }
        }
        for (String type : updated)
            writeFormat(type);
    }

    private void writeFormat(String type) throws IOException {
        writer.writeField(Constants.METADATA_PREFIX + type + Constants.METADATA_SUFFIX);
        for (InstrumentProfileField field : enumFormats.get(type))
            writer.writeField(field.name());
        for (String field : customFormats.get(type))
            writer.writeField(field);
        writer.writeRecord(null);
    }

    private void writeProfiles(List<InstrumentProfile> profiles, boolean skipRemoved) throws IOException {
        for (int i = 0; i < profiles.size(); i++) {
            String type = types.get(i); // atomically captured
            if (REMOVED_TYPE.equals(type) && skipRemoved)
                continue;
            InstrumentProfile ip = profiles.get(i);
            writeProfile(type, ip);
        }
    }

    private void writeProfile(String type, InstrumentProfile ip) throws IOException {
        writer.writeField(type);
        for (InstrumentProfileField field : enumFormats.get(type))
            writer.writeField(field.getField(ip));
        for (String field : customFormats.get(type))
            writer.writeField(ip.getField(field));
        writer.writeRecord(null);
    }
}
