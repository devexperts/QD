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

import com.devexperts.util.SystemProperties;

import java.io.IOException;
import java.io.OutputStream;

/**
 * An implementation of {@link BufferedOutput} that writes all data to destination {@link OutputStream}.
 */
public class StreamOutput extends BufferedOutput {
    private static final int DEFAULT_SIZE = SystemProperties.getIntProperty(StreamOutput.class, "defaultSize", 8192);

    protected OutputStream out;

    /**
     * Creates a new stream output with default buffer capacity.
     */
    public StreamOutput() {
        this(null, DEFAULT_SIZE);
    }

    /**
     * Creates a new stream output with specified buffer capacity.
     *
     * @param size the initial buffer size
     * @throws IllegalArgumentException if size is not positive
     */
    @SuppressWarnings("UnusedDeclaration")
    public StreamOutput(int size) {
        this(null, size);
    }

    /**
     * Creates a new stream output with specified destination output stream and default buffer capacity.
     *
     * @param out the destination to write to
     */
    public StreamOutput(OutputStream out) {
        this(out, DEFAULT_SIZE);
    }

    /**
     * Creates a new stream output with specified destination output stream and buffer capacity.
     *
     * @param out the destination to write to
     * @param size the initial buffer size
     * @throws IllegalArgumentException if size is not positive
     */
    public StreamOutput(OutputStream out, int size) {
        if (size <= 0)
            throw new IllegalArgumentException("buffer size <= 0");
        this.out = out;
        buffer = new byte[size];
        limit = buffer.length;
    }

    /**
     * Sets new destination output stream. Accepts <code>null</code> to release destination.
     * Clears any data that was not flushed to the previous output.
     * The {@link #totalPosition() totalPosition} is reset to 0.
     *
     * @param out the destination to write to
     */
    public void setOutput(OutputStream out) {
        this.out = out;
        totalPositionBase = 0;
        position = 0;
    }

    /**
     * Resets this stream output by releasing underlying output stream
     * and clears any data that was not flushed to the previous output.
     */
    public void resetOutput() {
        setOutput(null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void flush() throws IOException {
        if (position == 0)
            return;
        out.write(buffer, 0, position);
        totalPositionBase += position;
        position = 0;
        out.flush();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() throws IOException {
        if (out == null)
            return;
        flush();
        out.close();
        resetOutput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (len - limit >= limit - position) {
            flush();
            out.write(b, off, len);
            totalPositionBase += len;
        } else
            super.write(b, off, len);
    }

    @Override
    protected void needSpace() throws IOException {
        flush();
    }
}
