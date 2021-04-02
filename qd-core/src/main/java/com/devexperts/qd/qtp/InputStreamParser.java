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

import com.devexperts.io.BufferedInput;
import com.devexperts.io.StreamInput;
import com.devexperts.qd.DataScheme;

import java.io.InputStream;

/**
 * This parser reads and filters data and subscription from a specified {@link java.io.InputStream} in binary QTP format.
 * It serves as a convenience object that combines input buffer and parser together.
 * It can be used to read snapshots of data from a file, e.g. the one written with {@link OutputStreamComposer}.
 * For more advanced file reading capabilities (to read event tapes) see <b>com.devexperts.qd.qtp.file</b> package.
 *
 * @see OutputStreamComposer
 */
public class InputStreamParser extends BinaryQTPParser {

    // ======================== private instance fields ========================

    private final StreamInput input = new StreamInput();

    // ======================== constructor and instance methods ========================

    /**
     * Creates parser for the specified data scheme.
     * You must {@link #init(InputStream) init} this parser before using it.
     */
    public InputStreamParser(DataScheme scheme) {
        super(scheme);
        super.setInput(input);
    }

    // ------------------------ configuration methods ------------------------

    /**
     * {@inheritDoc}
     * This implementation throws {@link UnsupportedOperationException}.
     */
    @Override
    public void setInput(BufferedInput input) {
        throw new UnsupportedOperationException();
    }

    /**
     * Initializes parser with a specified input stream.
     * It resets session state from previous parsing session.
     * Both parameters could be <b>null</b> to deinitialize parser and release object references.
     */
    public void init(InputStream input) {
        this.input.setInput(input);
        resetSession();
    }
}
