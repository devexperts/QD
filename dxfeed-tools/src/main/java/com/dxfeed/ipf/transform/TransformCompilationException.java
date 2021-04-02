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
package com.dxfeed.ipf.transform;

/**
 * Signals that input stream does not conform to the instrument profile Transform format.
 * Please see <b>Instrument Profile Format</b> documentation for Transform syntax specification.
 */
public class TransformCompilationException extends Exception {
    private static final long serialVersionUID = 0;

    /**
     * Constructs a TransformCompilationException without detail message.
     */
    public TransformCompilationException() {
    }

    /**
     * Constructs a TransformCompilationException with specified detail message.
     */
    public TransformCompilationException(String s) {
        super(s);
    }
}
