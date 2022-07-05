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

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.FieldReplacer;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.services.Services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class FieldReplacersCache implements Consumer<RecordCursor> {
    private final DataScheme scheme;
    private final String spec;
    private final Consumer<RecordCursor>[] replacers;

    public static FieldReplacersCache valueOf(DataScheme scheme, String spec) {
        if (spec == null || spec.isEmpty()) {
            return null;
        }

        //noinspection unchecked
        Consumer<RecordCursor>[] replacers = new Consumer[scheme.getRecordCount()];

        // Get all services, which can implement
        List<FieldReplacer.Factory> factories =
            (List<FieldReplacer.Factory>) Services.createServices(FieldReplacer.Factory.class,
                scheme.getClass().getClassLoader());

        List<String> fieldReplacerSpecs;
        if (spec.startsWith("(")) {
            fieldReplacerSpecs = QDConfig.splitParenthesisSeparatedString(spec);
        } else {
            fieldReplacerSpecs = Collections.singletonList(spec);
        }

        // Create all field replacers (fabrics, realistically)
        List<FieldReplacer> allReplacers = new ArrayList<>();
        for (String fieldReplacerSpec : fieldReplacerSpecs) {
            boolean found = false;
            for (FieldReplacer.Factory factory : factories) {
                FieldReplacer fieldReplacer = factory.createFieldReplacer(fieldReplacerSpec, scheme);
                if (fieldReplacer != null) {
                    allReplacers.add(fieldReplacer);
                    found = true;
                    break;
                }
            }
            if (!found) {
                throw new IllegalArgumentException("Unknown field replacer spec: " + fieldReplacerSpec);
            }
        }

        // Convert field replacers into consumers
        // Create all possible consumers
        for (int rid = 0; rid < scheme.getRecordCount(); rid++) {
            replacers[rid] = createFieldReplacerForRecord(scheme.getRecord(rid), allReplacers);
        }
        return new FieldReplacersCache(scheme, spec, replacers);
    }

    private FieldReplacersCache(DataScheme scheme, String spec, Consumer<RecordCursor>[] replacers) {
        this.scheme = scheme;
        this.spec = spec;
        this.replacers = replacers;
    }

    public DataScheme getScheme() {
        return scheme;
    }

    public String getSpec() {
        return spec;
    }

    @Override
    public String toString() {
        return "FieldReplacersCache{spec=" + spec + "}";
    }

    @Override
    public void accept(RecordCursor cursor) {
        DataRecord rec = cursor.getRecord();
        Consumer<RecordCursor> replacer = replacers[rec.getId()];
        if (replacer == null)
            return;
        replacer.accept(cursor);
    }

    private static Consumer<RecordCursor> createFieldReplacerForRecord(DataRecord record,
        List<FieldReplacer> allReplacers)
    {
        List<Consumer<RecordCursor>> consumers = new ArrayList<>();
        for (FieldReplacer r : allReplacers) {
            Consumer<RecordCursor> consumer = r.createFieldReplacer(record);
            if (consumer == null)
                continue;
            consumers.add(consumer);
        }
        if (consumers.isEmpty()) {
            return null;
        }
        if (consumers.size() == 1) {
            return consumers.get(0);
        }
        if (consumers.size() == 2) {
            Consumer<RecordCursor> a = consumers.get(0);
            Consumer<RecordCursor> b = consumers.get(1);
            return rc -> {
                a.accept(rc);
                b.accept(rc);
            };
        }
        // Common case
        @SuppressWarnings("unchecked")
        final Consumer<RecordCursor>[] arr = consumers.toArray(new Consumer[0]);
        return rc -> {
            for (int i = 0; i < arr.length; i++) {
                arr[i].accept(rc);
            }
        };
    }
}
