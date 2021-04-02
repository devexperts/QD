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

import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordSink;

/**
 * The <code>QDHistory</code> represents a history-view of the data.
 * In the history-view, all data events in specified time period are
 * important and are delivered to the consumers. Such contract also allows
 * <code>QDHistory</code> to provide random access to available data values.
 */
public interface QDHistory extends QDCollector {
    /**
     * Returns minimal time of available records for specified parameters.
     * Returns 0 if none are available.
     */
    public long getMinAvailableTime(DataRecord record, int cipher, String symbol);

    /**
     * Returns maximal time of available records for specified parameters.
     * Returns 0 if none are available.
     */
    public long getMaxAvailableTime(DataRecord record, int cipher, String symbol);

    /**
     * Returns the number of available records for specified parameters.
     * The records from time interval <code>[startTime, endTime]</code> inclusive
     * are counted in the specified order (from startTime to endTime).
     * Returns 0 if none are available.
     */
    public int getAvailableCount(DataRecord record, int cipher, String symbol, long startTime, long endTime);

    /**
     * Examines available records for specified parameters via specified data visitor.
     * The records from time interval <code>[startTime, endTime]</code> inclusive
     * are examined in the specified order (from startTime to endTime).
     * Returns <code>true</code> if not all available data from specified interval
     * were examined or <code>false</code> if all available data were examined.
     *
     * @deprecated Use {@link #examineData(DataRecord, int, String, long, long, RecordSink)}
     */
    public boolean examineData(DataRecord record, int cipher, String symbol, long startTime, long endTime, DataVisitor visitor);

    /**
     * Examines available records for specified parameters via specified data visitor.
     * The records from time interval <code>[startTime, endTime]</code> inclusive
     * are examined in the specified order (from startTime to endTime).
     * Returns <code>true</code> if not all available data from specified interval
     * were examined or <code>false</code> if all available data were examined.
     *
     * <p>This method adds an empty record with {@link EventFlag#REMOVE_SYMBOL REMOVE_SYMBOL} flag as needed to indicate
     * where and if the range of times contains a snapshot. If there is a data snapshot in the indicated range of times,
     * then this method visits at least one record. {@link EventFlag#TX_PENDING TX_PENDING} flag is used to indicate
     * records that are affected by the ongoing transaction.
     */
    public boolean examineData(DataRecord record, int cipher, String symbol, long startTime, long endTime, RecordSink sink);
}
