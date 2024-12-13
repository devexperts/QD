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
package com.devexperts.qd.qtp.fieldreplacer;

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.kit.RecordOnlyFilter;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.FieldReplacer;
import com.devexperts.services.ServiceProvider;

import java.util.function.Consumer;

/**
 * This factory creates an implementation of {@link FieldReplacer} that provides an ability
 * to set value of one record's field to a fixed value.
 *
 * <h3>Specification</h3>
 * Specification of this {@link FieldReplacer field replacer} should be in the following format:
 * {@code "value:<recordFilter>:<target>:<value>"}, where {@code recordFilter} is a {@link RecordOnlyFilter},
 * {@code target} is a name of the target field and {@code value} is the value to set.
 *
 * <p>All records which are accepted by {@code recordFilter} and contain the {@code target}
 * field are processed by this replacer.
 *
 * <h3>Sample usage</h3>
 * The following code reads data from {@code example.qds} and set all quotes' bid prices to 123.45.
 * <pre><tt>
 *     DXEndpoint endpoint = ...
 *     endpoint.connect("example.qds[fieldReplacer=value:Quote*:bidPrice:123.45]");
 * </tt></pre>
 *
 * @see FieldReplacer
 */
@ServiceProvider
public class ValueFieldReplacerFactory implements FieldReplacer.Factory {

    private static final String PREFIX = "value" + FieldReplacer.DELIMITER;

    @Override
    public FieldReplacer createFieldReplacer(String fieldReplacerSpec, DataScheme dataScheme) {
        if (!fieldReplacerSpec.startsWith(PREFIX))
            return null;

        String[] fieldReplacerSpecParts = fieldReplacerSpec.split(FieldReplacer.DELIMITER, 4);
        if (fieldReplacerSpecParts.length != 4) {
            throw new IllegalArgumentException("ValueFieldReplacer specification should be in " +
                PREFIX + "<recordFilter>:<target>:<value> format: " + fieldReplacerSpec);
        }
        // Parse record filter
        RecordOnlyFilter recordFilter = RecordOnlyFilter.valueOf(fieldReplacerSpecParts[1], dataScheme);
        // Check fields
        final String targetName = fieldReplacerSpecParts[2];
        final String value = fieldReplacerSpecParts[3];

        @SuppressWarnings("unchecked")
        Consumer<RecordCursor>[] consumers = new Consumer[dataScheme.getRecordCount()];
        boolean recordsFound = false;
        for (int rid = 0; rid < dataScheme.getRecordCount(); rid++) {
            DataRecord rec = dataScheme.getRecord(rid);
            if (!recordFilter.acceptRecord(rec)) {
                consumers[rid] = null;
                continue;
            }

            DataField targetField = rec.findFieldByName(targetName);
            if (targetField == null)
                continue;

            recordsFound = true;

            int fid = targetField.getIndex();
            SerialFieldType serialType = targetField.getSerialType();

            // Use record buffer to serialize a value
            RecordCursor cursor = RecordCursor.allocate(rec, 0, "TEMP");
            try {
                // Write to temp buffer with all the needed conversions (from time, date, decimal, etc)
                targetField.setString(cursor, value);
            } catch (Exception e) {
                throw new IllegalArgumentException("fieldReplacer " + fieldReplacerSpec + ": value '" + value +
                    "' is incompatible with record " + rec.getName() + " target field " + targetName, e);
            }

            // Create the replacer by using the value from the temp buffer
            if (serialType.isLong()) {
                long longValue = cursor.getLong(fid);
                consumers[rid] = c -> c.setLong(fid, longValue);
            } else if (serialType.isObject()) {
                Object objValue = cursor.getObj(fid);
                consumers[rid] = c -> c.setObj(fid, objValue);
            } else {
                int intValue = cursor.getInt(fid);
                consumers[rid] = c -> c.setInt(fid, intValue);
            }
        }
        if (!recordsFound)
            throw new IllegalArgumentException("Cannot find any target fields: " + fieldReplacerSpec);

        return new DefaultFieldReplacer(dataScheme, consumers);
    }
}
