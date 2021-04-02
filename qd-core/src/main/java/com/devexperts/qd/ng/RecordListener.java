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
package com.devexperts.qd.ng;

import com.devexperts.qd.DataListener;

/**
 * Receives notifications about data availability in the corresponding record providers.
 * <p>
 * Generally, the notification is issued only if provider transforms from 'empty' state
 * into 'available' state at some moment after transition. In other cases notification
 * is not issued, though certain exclusions exist. The notification is issued in a state
 * that allows listener to perform potentially costly operations like event scheduling,
 * synchronization, or farther notification.
 */
public interface RecordListener {
    /**
     * This listener immediately retrieves and discards records from provider.
     */
    public static final RecordListener VOID = (RecordListener) DataListener.VOID;

    /**
     * Notifies this listener that records are available in the specified record provider.
     */
    public void recordsAvailable(RecordProvider provider);
}
