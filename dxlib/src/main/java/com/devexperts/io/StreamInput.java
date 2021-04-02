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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/**
 * An implementation of {@link BufferedInput} that reads all data from source {@link InputStream}.
 */
public class StreamInput extends BufferedInput {
    private static final int DEFAULT_SIZE =
        SystemProperties.getIntProperty(StreamInput.class, "defaultSize", 8192);

    protected InputStream in;

    /**
     * Creates a new stream input with default buffer size.
     */
    @SuppressWarnings("UnusedDeclaration")
    public StreamInput() {
        this(null, DEFAULT_SIZE);
    }

    /**
     * Creates a new stream input with specified buffer size.
     *
     * @param size the initial buffer size
     * @throws IllegalArgumentException if size is not positive
     */
    @SuppressWarnings("UnusedDeclaration")
    public StreamInput(int size) {
        this(null, size);
    }

    /**
     * Creates a new stream input with specified source input stream and default buffer size.
     *
     * @param in the source to read from
     */
    public StreamInput(InputStream in) {
        this(in, DEFAULT_SIZE);
    }

    /**
     * Creates a new stream input with specified source input stream and buffer size.
     *
     * @param in the source to read from
     * @param size the initial buffer size
     * @throws IllegalArgumentException if size is not positive
     */
    public StreamInput(InputStream in, int size) {
        if (size <= 0)
            throw new IllegalArgumentException("buffer size <= 0");
        this.in = in;
        buffer = new byte[size];
    }

    /**
     * Sets new source input stream. Accepts <code>null</code> to release source.
     *
     * @param in the source to read from
     */
    public void setInput(InputStream in) {
        this.in = in;
        position = 0;
        limit = 0;
        totalPositionBase = 0;
        markPosition = -1;
    }

    /**
     * Resets this stream input by releasing source input stream and resetting position.
     */
    public void resetInput() {
        setInput(null);
    }

    /**
     * Closes the source input stream and resets this {@code StreamInput} with
     * {@link #resetInput() resetInput} method.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
        if (in == null)
            return;
        in.close();
        resetInput();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasAvailable() throws IOException {
        return position < limit || in.available() > 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasAvailable(int bytes) throws IOException {
        return limit - position >= bytes || available() >= bytes;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int available() throws IOException {
        return (int) Math.min(limit - position + (long) in.available(), Integer.MAX_VALUE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        IOUtil.checkRange(b, off, len);
        if (len == 0)
            return 0;
        int buffered = limit - position;
        if (len <= buffered) {
            System.arraycopy(buffer, position, b, off, len);
            position += len;
            return len;
        }
        System.arraycopy(buffer, position, b, off, buffered);
        position = limit;
        resetPositionIfNotMarked();
        if (markPosition < 0 && len - buffered > buffer.length >> 1) {
            int k = in.read(b, off + buffered, len - buffered);
            if (k <= 0)
                return buffered > 0 ? buffered : -1;
            totalPositionBase += k;
            return buffered + k;
        }
        int k = readData();
        if (k <= 0)
            return buffered > 0 ? buffered : -1;
        k = Math.min(k, len - buffered);
        System.arraycopy(buffer, position, b, off + buffered, k);
        position += k;
        return buffered + k;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(long n) throws IOException {
        if (n <= 0)
            return 0;
        int buffered = limit - position;
        if (n <= buffered) {
            position += n;
            return n;
        }
        position = limit;
        resetPositionIfNotMarked();
        if (markPosition < 0) {
            long k = in.skip(n - buffered);
            if (k <= 0)
                return buffered;
            totalPositionBase += k;
            return buffered + k;
        }
        long k = readData();
        if (k <= 0)
            return buffered;
        k = Math.min(k, n - buffered);
        position += k;
        return buffered + k;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rewind(long n) {
        checkRewind(n); // throws exception if cannot rewind for specified distance
        position -= n;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int readData() throws IOException {
        checkEOB(); // throws exception if not all data was read
        resetPositionIfNotMarked();
        if (markPosition >= 0 && limit > buffer.length >> 1) {
            int markPos = (int) (markPosition - totalPositionBase);
            int marked = limit - markPos;
            byte[] newBuffer;
            // Chain if-else-if below selects best choice to minimize underlying reads and copying.
            if (markPos > marked)
                newBuffer = buffer;
            else if (buffer.length < Integer.MAX_VALUE)
                newBuffer = new byte[Math.max((int) Math.min((long) buffer.length << 1, Integer.MAX_VALUE), 1024)];
            else if (markPos > 0)
                newBuffer = buffer;
            else if (limit == buffer.length)
                throw new IOException("Buffer overflow.");
            else
                newBuffer = null;
            if (newBuffer != null) {
                System.arraycopy(buffer, markPos, newBuffer, 0, marked);
                buffer = newBuffer;
                position -= markPos;
                limit -= markPos;
                totalPositionBase += markPos;
            }
        }
        int result = in.read(buffer, limit, buffer.length - limit);
        if (result > 0)
            limit += result;
        return result;
    }

    @Override
    protected void throwEOFException() throws EOFException {
        throw new EOFException();
    }

    private void resetPositionIfNotMarked() {
        assert position == limit;
        if (markPosition >= 0)
            return;
        totalPositionBase = totalPosition();
        position = 0;
        limit = 0;
    }
}
