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
package com.dxfeed.ipf.impl;

import com.devexperts.io.CSVWriter;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileField;
import com.dxfeed.ipf.InstrumentProfileType;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Composer for Instrument Profile Format.
 * Please see <b>Instrument Profile Format</b> documentation for complete description.
 */
public class InstrumentProfileComposer implements Closeable {
    // All fields from enum except TYPE and SYMBOL
    private static final InstrumentProfileField[] FIELDS = Arrays.stream(InstrumentProfileField.values())
        .filter(field -> (field != InstrumentProfileField.TYPE) && (field != InstrumentProfileField.SYMBOL))
        .toArray(InstrumentProfileField[]::new);

    private static final String REMOVED_TYPE = InstrumentProfileType.REMOVED.name();

    // Raw enum fields by type
    private final Map<String, EnumSet<InstrumentProfileField>> rawEnumFormats = new HashMap<>();
    // Filtered enum fields by type
    private final Map<String, EnumSet<InstrumentProfileField>> enumFormats = new HashMap<>();
    // Raw custom fields by type (HashSet for fast "contains" check)
    private final Map<String, HashSet<String>> rawCustomFormats = new HashMap<>();
    // Filtered custom fields by type (TreeSet is used for ordering fields by name)
    private final Map<String, TreeSet<String>> customFormats = new HashMap<>();

    private final List<String> types = new ArrayList<>(); // atomically captures types
    private final CSVWriter writer;
    private final Predicate<? super String> fieldFilter;

    /**
     * Creates composer for the specified output stream.
     * @param out output stream to which instrument profiles will be written
     */
    public InstrumentProfileComposer(OutputStream out) {
        this(out, s -> true);
    }

    /**
     * Creates composer for the specified output stream and the field name filter.
     *
     * <p><b>Note</b> that fields {@link InstrumentProfileField#TYPE TYPE} and
     * {@link InstrumentProfileField#SYMBOL SYMBOL} are required and cannot be filtered out.
     *
     * @param out output stream to which instrument profiles will be written
     * @param fieldFilter predicate allowing to filter instrument profile fields by name.
     */
    public InstrumentProfileComposer(OutputStream out, Predicate<? super String> fieldFilter) {
        this.writer = new CSVWriter(new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8)));
        this.fieldFilter = Objects.requireNonNull(fieldFilter, "fieldFilter");
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
        // Force flushing data to downstream
        writer.flush();
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
        Set<String> updatedTypes = new TreeSet<>();
        for (int i = 0; i < profiles.size(); i++) {
            String type = types.get(i); // atomically captured
            if (REMOVED_TYPE.equals(type) && skipRemoved)
                continue;

            boolean updated = false;
            EnumSet<InstrumentProfileField> rawEnumFormat = rawEnumFormats.get(type);
            HashSet<String> rawCustomFormat = rawCustomFormats.get(type);
            if (rawEnumFormat == null) {
                updated = true;
                rawEnumFormats.put(type, rawEnumFormat = EnumSet.of(InstrumentProfileField.SYMBOL));
                rawCustomFormats.put(type, rawCustomFormat = new HashSet<>());
            }

            InstrumentProfile ip = profiles.get(i);
            if (!REMOVED_TYPE.equals(type)) {
                // Collect actual used fields for non-removed instrument profiles
                for (InstrumentProfileField field : FIELDS) {
                    if (!field.getField(ip).isEmpty() && rawEnumFormat.add(field)) {
                        updated = true;
                    }
                }
                if (ip.addNonEmptyCustomFieldNames(rawCustomFormat))
                    updated = true;
            }
            if (updated)
                updatedTypes.add(type);
        }
        for (String type : updatedTypes) {
            if (filterFormat(type)) {
                writeFormat(type);
            }
        }
    }

    private boolean filterFormat(String type) {
        boolean updated = false;
        EnumSet<InstrumentProfileField> enumFormat = enumFormats.get(type);
        TreeSet<String> customFormat = customFormats.get(type);
        if (enumFormat == null) {
            updated = true;
            // Always write symbol (type is always written by a special code)
            enumFormats.put(type, enumFormat = EnumSet.of(InstrumentProfileField.SYMBOL));
            customFormats.put(type, customFormat = new TreeSet<>());
        }

        for (InstrumentProfileField field : rawEnumFormats.get(type)) {
            if (fieldFilter.test(field.name()) && enumFormat.add(field))
                updated = true;
        }
        for (String field : rawCustomFormats.get(type)) {
            if (fieldFilter.test(field) && customFormat.add(field))
                updated = true;
        }
        return updated;
    }

    private void writeFormat(String type) throws IOException {
        writer.writeField(Constants.METADATA_PREFIX + type + Constants.METADATA_SUFFIX);
        for (InstrumentProfileField field : enumFormats.get(type)) {
            writer.writeField(field.name());
        }
        for (String field : customFormats.get(type)) {
            writer.writeField(field);
        }
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
