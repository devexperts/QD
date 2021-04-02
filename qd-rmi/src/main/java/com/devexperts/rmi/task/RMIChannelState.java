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
package com.devexperts.rmi.task;

/**
 *  States of {@link RMIChannel}.
 */
public enum RMIChannelState {

    /**
     * The channel was created but top-level request has not yet been sent or top-level task was not yet executed.
     */
    NEW,

    /**
     * The channel open and active.
     */

    OPEN,

    /**
     * The channel was canceled, but CancelRequest has not been sent.
     */
    CANCELLING,

    /**
     * The channel was closed with top-level request or top-level task.
     */
    CLOSED
}
