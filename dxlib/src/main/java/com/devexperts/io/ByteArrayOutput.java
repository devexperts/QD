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

import com.devexperts.util.ArrayUtil;

import java.io.IOException;

/**
 * An implementation of {@link BufferedOutput} using single byte array buffer.
 * Note, that most of the time, you need to use {@link ChunkedOutput} for better efficiency.
 */
public class ByteArrayOutput extends BufferedOutput {
    private static final int MIN_CAPACITY = 64;

    /**
     * This method is invoked when output methods need more space to write data.
     * This method must ensure that expression <code>(position &lt; limit)</code>
     * is true or throw an exception.
     *
     * <p>This implementation is final and invokes
     * <code>{@link #ensureCapacity(int) ensureCapacity}({@link #position position} + 1)</code>.
     * Classes that extend {@code ByteArrayOutput} shall override {@link #ensureCapacity(int)} to
     * customize their buffer allocation strategy.
     *
     * @throws IOException if buffer is already too big
     */
    @Override
    protected final void needSpace() throws IOException {
        if (position == Integer.MAX_VALUE)
            throw new IOException("Buffer overflow");
        ensureCapacity(position + 1);
    }

    /**
     * Creates a new byte array output without pre-allocated buffer.
     */
    public ByteArrayOutput() {
    }

    /**
     * Creates a new byte array output with the specified buffer capacity.
     *
     * @param size the initial buffer size
     * @throws IllegalArgumentException if size is negative
     */
    public ByteArrayOutput(int size) {
        if (size < 0)
            throw new IllegalArgumentException("Negative initial size");
        if (size > 0)
            buffer = new byte[limit = size];
    }

    /**
     * Creates a new byte array output with the specified buffer.
     *
     * @param buffer the buffer.
     * @throws NullPointerException if the buffer is null.
     */
    public ByteArrayOutput(byte[] buffer) {
        this.buffer = buffer;
        limit = buffer.length;
    }

    /**
     * Returns byte array used for buffering. Never returns null.
     */
    public byte[] getBuffer() {
        return buffer;
    }

    /**
     * Sets specified byte array to be used for buffering. Null is treated as empty array.
     * The position is set to zero, the limit is set to the capacity of new buffer.
     */
    public void setBuffer(byte[] newBuffer) {
        if (newBuffer == null)
            newBuffer = EMPTY_BYTE_ARRAY;
        buffer = newBuffer;
        position = 0;
        limit = buffer.length;
    }

    /**
     * Returns position.
     */
    public int getPosition() {
        return position;
    }

    /**
     * Sets position as specified.
     *
     * @throws IllegalArgumentException if the new position is negative or is larger than the limit
     */
    public void setPosition(int newPosition) {
        if (newPosition < 0 || newPosition > limit)
            throw new IllegalArgumentException();
        position = newPosition;
    }

    /**
     * Returns limit.
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Sets limit as specified. If the position is larger than the new limit then it is set to the new limit.
     *
     * @throws IllegalArgumentException if the new limit is negative or is larger than the capacity
     */
    public void setLimit(int newLimit) {
        if (newLimit < 0 || newLimit > buffer.length)
            throw new IllegalArgumentException();
        limit = newLimit;
        if (position > limit)
            position = limit;
    }

    /**
     * Clears this buffer. The position is set to zero, the limit is set to the capacity.
     * The {@link #totalPosition() totalPosition} is reset to 0.
     */
    public void clear() {
        totalPositionBase = 0;
        position = 0;
        limit = buffer.length;
    }

    /**
     * {@inheritDoc}
     *
     * <p>This implementation also ensures that buffer has an appropriate capacity with at most one reallocation
     * of the underlying array.
     *
     * <p>This implementation allocates an internal buffer at the size that is equal to the number of
     * written bytes at the first invocation of this method. This ensures that if all data that is
     * written into this byte array output is a result of single invocation of this write method, then
     * the underlying buffer has an appropriate size. This property is used by {@link Marshalled#getBytes()} method
     * to avoid extra copying of bytes.
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        IOUtil.checkRange(b, off, len);
        if (len == 0)
            return; // nothing to do
        if (buffer.length == 0)
            setBuffer(new byte[len]); // first invocation with empty buffer -- precise allocation
        else
            ensureCapacity(position + len); // ensure capacity in a regular way
        // now there is enough capacity
        System.arraycopy(b, off, buffer, position, len);
        position += len;
    }

    /**
     * Ensures that the byte array used for buffering has at least the specified capacity.
     * This method reallocates buffer if needed and copies content of old buffer into new one.
     * This method also sets the limit to the capacity.
     */
    public void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity > buffer.length)
            buffer = ArrayUtil.grow(buffer, Math.max(MIN_CAPACITY, requiredCapacity));
        limit = buffer.length;
    }

    /**
     * Returns a full copy of byte array buffer from zero to the position.
     */
    public byte[] toByteArray() {
        if (position == 0)
            return EMPTY_BYTE_ARRAY;
        byte[] data = new byte[position];
        System.arraycopy(buffer, 0, data, 0, position);
        return data;
    }

    /**
     * Converts the buffer's content from zero to the position into a string,
     * translating bytes into characters according to the platform's default character encoding.
     */
    public String toString() {
        return position == 0 ? "" : new String(buffer, 0, position);
    }
}
