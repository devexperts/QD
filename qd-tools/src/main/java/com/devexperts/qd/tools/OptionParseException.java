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

/**
 * Thrown to indicate an error while parsing some QD tool option.
 */
public class OptionParseException extends BadToolParametersException {
    private static final long serialVersionUID = 0;

    public OptionParseException(String message) {
        super(message);
    }

    public OptionParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
