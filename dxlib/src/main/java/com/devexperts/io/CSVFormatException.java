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
package com.devexperts.io;

import java.io.IOException;

/**
 * Signals that input stream does not conform to the CSV format.
 * See <a href="http://www.rfc-editor.org/rfc/rfc4180.txt">RFC 4180</a> for CSV format specification.
 */
public class CSVFormatException extends IOException {
    private static final long serialVersionUID = 0;

    /**
     * Constructs a CSVFormatException without detail message.
     */
    public CSVFormatException() {
    }

    /**
     * Constructs a CSVFormatException with specified detail message.
     */
    public CSVFormatException(String s) {
        super(s);
    }
}
