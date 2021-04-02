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
 * The <code>MessageProvider</code> provides QTP messages.
 * It shall be implemented by an agent of some entity to be used by standard QTP connectors.
 * Its methods are invoked when QTP connector is ready to send some messages,
 * usually from threads allocated internally by used QTP connector.
 */
public interface MessageProvider {
    /**
     * Retrieves accumulated message into specified message visitor.
     * Returns <code>true</code> if some messages still remains in the provider
     * or <code>false</code> if all accumulated messages were retrieved.
     */
    public boolean retrieveMessages(MessageVisitor visitor);

    /**
     * Sets new message listener to receive notifications about messages.
     * Only one listener at a time is supported; the former listener is discarded.
     * Use <code>null</code> to set empty message listener (no notifications).
     * <p>
     * <b>NOTE:</b> if there is accumulated data available, then specified
     * listener will be notified by this method.
     */
    public void setMessageListener(MessageListener listener);
}
