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
package com.dxfeed.promise;

/**
 * Indicates that promised computation has completed exceptionally.
 */
public class PromiseException extends RuntimeException {
    /**
     * Constructs exception with a given cause.
     * @param cause the cause.
     */
    public PromiseException(Throwable cause) {
        super(cause);
    }
}
