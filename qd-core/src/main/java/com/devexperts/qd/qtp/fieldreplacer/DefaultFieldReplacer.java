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

import java.util.function.Consumer;

/**
 * Default {@link FieldReplacer} implementation that caches field replacers for all records
 * in the specified {@link DataScheme}.
 */
public class DefaultFieldReplacer implements FieldReplacer {

    private final DataScheme scheme;
    private final Consumer<RecordCursor>[] replacers;

    public DefaultFieldReplacer(DataScheme scheme, Consumer<RecordCursor>[] replacers) {
        this.scheme = scheme;
        this.replacers = replacers;
    }

    @Override
    public Consumer<RecordCursor> createFieldReplacer(DataRecord record) {
        if (record.getScheme() != scheme)
            throw new IllegalArgumentException("Unknown record");
        return replacers[record.getId()];
    }
}
