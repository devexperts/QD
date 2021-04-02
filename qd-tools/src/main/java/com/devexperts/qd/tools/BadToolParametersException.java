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
package com.devexperts.qd.tools;

import com.devexperts.util.InvalidFormatException;

/**
 * Thrown to indicate an error while parsing or processing some QD tool arguments.
 */
public class BadToolParametersException extends InvalidFormatException {
    private static final long serialVersionUID = 0;

    public BadToolParametersException(String message) {
        super(message);
    }

    public BadToolParametersException(String message, Throwable cause) {
        super(message, cause);
    }
}
