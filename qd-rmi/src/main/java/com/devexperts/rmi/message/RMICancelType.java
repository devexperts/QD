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
package com.devexperts.rmi.message;

import com.devexperts.rmi.impl.RMIRequestImpl;

/**
 * Types of cancellation for {@link RMIRequestImpl}.
 */
public enum RMICancelType {

    /**
     * Cancels the request.
     */
    DEFAULT(0),

    /**
     *  Aborts the request.
     */
    ABORT_RUNNING(1);

    public int getId() {
        return id;
    }

    private final int id;

    RMICancelType(int id) {
        this.id = id;
    }
}
