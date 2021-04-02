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
package com.devexperts.qd;

import com.devexperts.qd.ng.RecordConsumer;

/**
 * The <code>DataConsumer</code> processes incoming data.
 * See {@link DataIterator} for description of corresponding contracts.
 * @deprecated Use {@link RecordConsumer}.
 */
public interface DataConsumer {
    public static DataConsumer VOID = Void.VOID;

    /**
     * Processes data from specified data iterator. It is recommended that {@link DataIterator}
     * also implements {@link com.devexperts.qd.ng.RecordSource RecordSource} interface
     * ({@link com.devexperts.qd.ng.RecordBuffer RecordBuffer} is such default and high-performance implementation),
     * since it yields better performance in certain cases and enables some new features that are not supported with
     * legacy {@link DataIterator} implementations.
     * @deprecated Use {@link RecordConsumer#process} method.
     */
    public void processData(DataIterator iterator);
}
