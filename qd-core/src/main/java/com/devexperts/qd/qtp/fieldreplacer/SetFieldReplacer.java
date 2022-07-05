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
package com.devexperts.qd.qtp.fieldreplacer;

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.FieldReplacer;
import com.devexperts.services.ServiceProvider;

import java.util.function.Consumer;

/**
 * This implementation of {@link FieldReplacer} provides an ability to set value of one record's
 * field to value of another field of same type.
 *
 * <h3>Specification</h3>
 * Specification of this {@link FieldReplacer field replacer} should be in the following format:
 * {@code "set:<recordFilter>:<target>:<source>"}, where {@code recordFilter} is a {@link RecordOnlyFilter},
 * {@code target} is a name of target field and {@code source} name of a source field.
 *
 * <p>All records which are accepted by {@code recordFilter} and contain both {@code target} and
 * {@code source} fields of the same type are processed by this replacer.
 *
 * <p>It is an error to have record which is accepted by {@code recordFilter} and has only one of
 * specified fields or has both fields but of a different type.
 *
 * <h3>Sample usage</h3>
 * The following code reads data from {@code example.qds} and set all quotes' bid prices to ask price (make them equal).
 * <pre><tt>
 *     DXEndpoint endpoint = ...
 *     endpoint.connect("example.qds[fieldReplacer=set:Quote*:bidPrice:askPrice");
 * </tt></pre>
 *
 * @see FieldReplacer
 * @see FieldReplacerUtil
 */
public class SetFieldReplacer implements FieldReplacer {
    private final DataScheme scheme;
    private final Consumer<RecordCursor>[] consumers;

    private SetFieldReplacer(DataScheme scheme, Consumer<RecordCursor>[] consumers) {
        this.scheme = scheme;
        this.consumers = consumers;
    }

    @Override
    public Consumer<RecordCursor> createFieldReplacer(DataRecord dataRecord) {
        if (dataRecord.getScheme() != scheme)
            throw new IllegalArgumentException("Unknown record");
        return consumers[dataRecord.getId()];
    }

    @ServiceProvider
    public static class Factory implements FieldReplacer.Factory {
        private static final String DELIMETER = ":";
        private static final String SET_PREFIX = "set" + DELIMETER;

        @Override
        public FieldReplacer createFieldReplacer(String fieldReplacerSpec, DataScheme dataScheme) {
            if (!fieldReplacerSpec.startsWith(SET_PREFIX))
                return null;

            String[] fieldReplacerSpecParts = fieldReplacerSpec.split(DELIMETER);
            if (fieldReplacerSpecParts.length != 4) {
                throw new IllegalArgumentException("SetFieldReplacer specification should be in " +
                        SET_PREFIX + "<recordFilter>:<target>:<source> format: " + fieldReplacerSpec);
            }
            // Parse record filter
            RecordOnlyFilter recordFilter = RecordOnlyFilter.valueOf(fieldReplacerSpecParts[1], dataScheme);
            // Check fields
            final String trgName = fieldReplacerSpecParts[2];
            final String srcName = fieldReplacerSpecParts[3];

            @SuppressWarnings("unchecked")
            Consumer<RecordCursor>[] consumers = new Consumer[dataScheme.getRecordCount()];
            boolean recordsFound = false;
            for (int rid = 0; rid < dataScheme.getRecordCount(); rid++) {
                DataRecord rec = dataScheme.getRecord(rid);
                if (!recordFilter.acceptRecord(rec)) {
                    consumers[rid] = null;
                    continue;
                }

                DataField trg = rec.findFieldByName(trgName);
                DataField src = rec.findFieldByName(srcName);
                if (trg == null && src == null)
                    continue;
                if (trg == null) {
                    throw new IllegalArgumentException("fieldReplacer " + fieldReplacerSpec + ":" +
                            " record " + rec.getName() +
                            " target field " + trgName + " is not found.");
                } else if (src == null) {
                    throw new IllegalArgumentException("fieldReplacer " + fieldReplacerSpec + ":" +
                            " record " + rec.getName() +
                            " source field " + srcName + " is not found.");
                } else if (trg == src) {
                    throw new IllegalArgumentException("fieldReplacer " + fieldReplacerSpec + ":" +
                            " record " + rec.getName() +
                            " target " + trgName +
                            " and source " + srcName + " fields are the same field.");
                } else if (!trg.getSerialType().equals(src.getSerialType())) {
                    throw new IllegalArgumentException("fieldReplacer " + fieldReplacerSpec + ":" +
                            " record " + rec.getName() +
                            " target " + trgName +
                            " and source " + srcName + " fields" +
                            " have incompatible types");
                }
                recordsFound = true;

                final int tfid = trg.getIndex();
                final int sfid = src.getIndex();
                if (trg.getSerialType().isLong()) {
                    consumers[rid] = c -> c.setLong(tfid, c.getLong(sfid));
                } else if (trg.getSerialType().isObject()) {
                    consumers[rid] = c -> c.setObj(tfid, c.getObj(sfid));
                } else {
                    consumers[rid] = c -> c.setInt(tfid, c.getInt(sfid));
                }
            }
            if (!recordsFound)
                throw new IllegalArgumentException("Can not find any target fields: " + fieldReplacerSpec);
            // Create SetFieldReplacer
            return new SetFieldReplacer(dataScheme, consumers);
        }
    }
}
