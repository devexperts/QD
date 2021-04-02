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

/**
 * The <code>MessageListener</code> is used to receive notifications about
 * QTP messages availability.
 */
public interface MessageListener {

    /**
     * Notifies this listener that some messages are available in the
     * specified QTP message provider.
     */
    public void messagesAvailable(MessageProvider provider);
}
