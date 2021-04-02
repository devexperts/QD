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

import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.RecordBuffer;

/**
 * The <code>DataProvider</code> allows retrieval of accumulated data.
 * See {@link DataVisitor} and {@link DataListener} for description
 * of corresponding contracts.
 *
 * <h3>Legacy interface</h3>
 *
 * <b>FUTURE DEPRECATION NOTE:</b>
 *    New code shall not implement this interface due to its complexity and inherent slowness.
 *    Extend {@link AbstractRecordProvider} or use {@link RecordBuffer}
 *    as a high-performance implementation of it when data is available in advance.
 *    New code is also discouraged from using this interface unless it is need for interoperability with
 *    legacy code. Various legacy APIs will be gradually migrated to NG interfaces and classes.
 */
public interface DataProvider {

    /**
     * Retrieves accumulated data into specified data visitor.
     * Returns <code>true</code> if some data still remains in the provider
     * or <code>false</code> if all accumulated data were retrieved.
     */
    public boolean retrieveData(DataVisitor visitor);

    /**
     * Sets new data listener to receive notifications about data.
     * Only one listener at a time is supported; the former listener is discarded.
     * Use <code>null</code> to set empty data listener (no notifications).
     * <p>
     * <b>NOTE:</b> if there is accumulated data available, then specified
     * listener will be notified by this method.
     */
    public void setDataListener(DataListener listener);
}
