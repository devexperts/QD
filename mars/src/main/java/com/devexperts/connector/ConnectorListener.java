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
 * The listener interface for receiving connector events.
 */
public interface ConnectorListener {

    /**
     * Invoked when a connection has been established.
     */
    public void connectionEstablished(String message);

    /**
     * Invoked when a connection has been lost.
     */
    public void connectionLost(String message, Throwable error);

    /**
     * Will be called when error occurs.
     *
     * @param message message
     * @param error is null for information messages
     */
    public void errorOccured(String message, Throwable error);

    /**
     * @param message info message
     */
    public void info(String message);

}
