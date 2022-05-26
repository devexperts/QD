/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf;

import java.io.IOException;

/**
 * Signals that input stream does not conform to the Instrument Profile Format.
 * Please see <b>Instrument Profile Format</b> documentation for complete description.
 */
public class InstrumentProfileFormatException extends IOException {
    private static final long serialVersionUID = 0;

    /**
     * Constructs a InstrumentProfileFormatException without detail message.
     */
    public InstrumentProfileFormatException() {
    }

    /**
     * Constructs a InstrumentProfileFormatException with specified detail message.
     */
    public InstrumentProfileFormatException(String s) {
        super(s);
    }
}
