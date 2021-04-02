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

import com.devexperts.qd.ng.RecordCursor;

/**
 * The <code>QDTicker</code> represents a ticker-view of the data.
 * In the ticker-view, only last data values are important and data events are
 * generated only to bring data snapshot up-to-date. Such contract also allows
 * <code>QDTicker</code> to provide random access to current data values.
 */
public interface QDTicker extends QDCollector {
    /**
     * Determines if value of specified record is available.
     */
    public boolean isAvailable(DataRecord record, int cipher, String symbol);

    /**
     * Returns current value of specified Int-field.
     * Returns 0 if such value does not exist or is unknown.
     */
    public int getInt(DataIntField field, int cipher, String symbol);

    /**
     * Returns current value of specified Obj-field.
     * Returns null if such value does not exist or is unknown.
     */
    public Object getObj(DataObjField field, int cipher, String symbol);

    /**
     * Gets all data for the specified record, cipher, and symbol in a single call
     * and sets {@link RecordCursor RecordCursor} via caller-provided
     * {@link RecordCursor.Owner RecordCursor.Owner} to the
     * corresponding memory storage in read-only mode. This way, any fields
     * can be retried from ticker without requiring any actual data copy or memory allocation.
     */
    public void getData(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol);

    /**
     * Gets all data for the specified record, cipher, and symbol in a single call
     * and sets {@link RecordCursor RecordCursor} via caller-provided
     * {@link RecordCursor.Owner RecordCursor.Owner} to the
     * corresponding memory storage in read-only mode if the corresponding data
     * is available in the storage. If the data was available then this method returns {@code true}.
     * If the data was not available then this method returns {@code false} and the cursor is not changed.
     * @return {@code true} if the data was available, {@code false} otherwise.
     */
    public boolean getDataIfAvailable(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol);

    public boolean getDataIfSubscribed(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol);
}
