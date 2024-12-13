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

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.FieldReplacer;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.services.Services;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

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
        List<FieldReplacer.Factory> factories = (List<FieldReplacer.Factory>) Services.createServices(
            FieldReplacer.Factory.class, scheme.getClass().getClassLoader());

        List<String> fieldReplacerSpecs = QDConfig.splitParenthesisSeparatedString(spec);

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
            DataRecord record = scheme.getRecord(rid);
            replacers[rid] = createComposite(allReplacers.stream()
                .map(r -> r.createFieldReplacer(record))
                .filter(Objects::nonNull)
                .collect(Collectors.toList())
            );
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
        Consumer<RecordCursor> replacer = replacers[cursor.getRecord().getId()];
        if (replacer == null)
            return;
        replacer.accept(cursor);
    }

    public static Consumer<RecordCursor> createComposite(List<Consumer<RecordCursor>> consumers) {
        if (consumers == null || consumers.isEmpty()) {
            return null;
        } else if (consumers.size() == 1) {
            return consumers.get(0);
        } else if (consumers.size() == 2) {
            Consumer<RecordCursor> a = consumers.get(0);
            Consumer<RecordCursor> b = consumers.get(1);
            return rc -> {
                a.accept(rc);
                b.accept(rc);
            };
        } else {
            // Common case
            @SuppressWarnings("unchecked")
            Consumer<RecordCursor>[] arr = consumers.toArray(new Consumer[0]);
            return rc -> {
                for (Consumer<RecordCursor> c : arr) {
                    c.accept(rc);
                }
            };
        }
    }
}
