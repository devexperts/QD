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
package com.dxfeed.event.market.impl;

import com.devexperts.qd.DataRecord;
import com.dxfeed.event.market.OrderSource;

public class OrderBaseMapping extends MarketEventMapping {
    public static final char SOURCE_ID_SEPARATOR = '#';

    protected final OrderSource recordSource;

    public OrderBaseMapping(DataRecord record) {
        super(record);
        String name = record.getName();
        int i = name.lastIndexOf(SOURCE_ID_SEPARATOR);
        String sourceName = i < 0 ? "" : name.substring(i + 1);
        if (sourceName.length() > 4) // catch usages of builtin sources aka DEFAULT or COMPOSITE_BID
            throw new IllegalArgumentException("Invalid source name: " + sourceName);
        recordSource = i < 0 ? OrderSource.DEFAULT : OrderSource.valueOf(sourceName);
    }

    public final int getRecordSourceId() {
        return getRecordSource().id();
    }

    public final OrderSource getRecordSource() {
        return recordSource;
    }
}
