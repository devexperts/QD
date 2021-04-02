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
package com.devexperts.connector;

/**
 * ConnectionAdapterListener is used to receive notification about corresponding {@link ConnectionAdapter} state changes.
 */
public interface ConnectionAdapterListener {

    /**
     * Invoked to notify that specified {@link ConnectionAdapter} has data available for writing.
     * This method never blocks for prolonged time period.
     */
    public void dataAvailable(ConnectionAdapter adapter);

    /**
     * Invoked to notify that specified {@link ConnectionAdapter} was closed.
     * This method never blocks for prolonged time period.
     */
    public void adapterClosed(ConnectionAdapter adapter);
}
