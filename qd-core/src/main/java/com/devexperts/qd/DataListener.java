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

import com.devexperts.qd.ng.RecordListener;

/**
 * The <code>DataListener</code> is used to receive notifications about data availability
 * in the corresponding data providers.
 * <p>
 * Generally, the notification is issued only if provider transforms from 'empty' state
 * into 'available' state at some moment after transition. In other cases notification
 * is not issued, though certain exclusions exist. The notification is issued in a state
 * that allows listener to perform potentially costly operations like event scheduling,
 * synchronization, or farther notification.
 *
 * <h3>Legacy interface</h3>
 *
 * <b>FUTURE DEPRECATION NOTE:</b>
 *    New code shall not implement this interface.
 *    Implement {@link RecordListener}.
 *    New code is also discouraged from using this interface unless it is need for interoperability with
 *    legacy code. Various legacy APIs will be gradually migrated to NG interfaces and classes.
 */
public interface DataListener {
    /**
     * This listener immediately retrieves and discards data from provider.
     */
    public static final DataListener VOID = Void.VOID;

    /**
     * Notifies this listener that data is available in the specified data provider.
     */
    public void dataAvailable(DataProvider provider);
}
