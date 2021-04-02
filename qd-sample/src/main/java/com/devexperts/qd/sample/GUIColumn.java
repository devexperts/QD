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
package com.devexperts.qd.sample;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.ng.RecordCursor;

/**
 * The <code>GUIColumn</code> defines common GUI API to data field.
 */
public interface GUIColumn {

    /**
     * Returns data record that contains corresponding field.
     * To be used for subscription.
     */
    public DataRecord getRecord();

    /**
     * Returns GUI name of this column.
     */
    public String getName();

    /**
     * Returns properly formatted value from specified QDTicker.
     */
    public Object getValue(QDTicker ticker, int cipher, String symbol);

    /**
     * Returns properly formatted value from specified DataBuffer.
     * @param cur
     */
    public Object getValue(RecordCursor cur);
}
