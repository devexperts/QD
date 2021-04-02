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
package com.devexperts.qd.qtp;


import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.services.Service;

import java.util.function.Consumer;
import javax.annotation.Nullable;

/**
 * This interface allows to update fields in data read through {@link AbstractQTPParser QTP parser}.
 */
public interface FieldReplacer {

    /**
     * Creates consumer to be used to update fields in {@link RecordCursor}
     * with specified {@link DataRecord data record}.
     *
     * @param dataRecord specified data record
     * @return consumer which updates fields according to {@code FieldReplacer's} specification
     * or {@code null} if no updates are needed for specified {@link DataRecord data record}.
     * @see Factory
     */
    @Nullable
    Consumer<RecordCursor> createFieldReplacer(DataRecord dataRecord);

    /**
     * The {@code Factory} performs to create {@link FieldReplacer field replacers}.
     * <p>All implementations should be specified in {@code META-INF/com.devexperts.qd.qtp.FieldReplacer$Factory} file.
     */
    @Service
    interface Factory {

        /**
         * Creates {@link FieldReplacer field replacer} for specified {@link DataScheme data scheme}
         * from specification.
         * <p><b>NOTE: the only one factory can support specification.</b>
         *
         * @param fieldReplacerSpec specification of {@link FieldReplacer}. The specification should start with
         *                          {@code "<factory_name>:"} to detect should factory support the specification or not.
         * @param dataScheme        current {@link DataScheme data scheme}
         * @return new {@link FieldReplacer field replacer} if this factory supports specification,
         * {@code null} otherwise.
         */
        @Nullable
        public FieldReplacer createFieldReplacer(String fieldReplacerSpec, DataScheme dataScheme);
    }

}
