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

import java.io.EOFException;
import java.io.IOException;

/**
 * An implementation of {@link BufferedInput} using single byte array buffer.
 */
public class ByteArrayInput extends BufferedInput {
    private static final int MIN_CAPACITY = 1024;

    /**
     * This method is invoked when input methods need more bytes to read data
     * by a default implementation of {@link #readData()}.
     *
     * <p>This implementation checks internal invariant that
     * <code>{@link #position position} == {@link #limit limit}</code>
     * and returns {@code -1}.
     *
     * @deprecated This method is provided for backwards compatibility, because
     * it is extended in some legacy classes. It is always invoked with a length of 1.
     * Override {@link #readData()} instead.
     */
    protected int readData(int length) throws IOException {
        checkEOB(); // throws exception if not all data was read
        return -1;
    }

    /**
     * This method is invoked when input methods need more bytes to read data.
     * This method shall make an attempt to read some bytes into the buffer.
     * This method returns number of bytes actually read or value <code>-1</code>
     * if no bytes can be read because the end of the stream has been reached.
     * This method may block if needed.
     * This method is allowed to update buffer, position, limit and totalPositionBase fields as needed.
     *
     * <p>This method shall never throw an {@link EOFException}.
     *
     * <p>This implementation invokes
     * <code>{@link #readData(int) readData}(1)</code>
     * for backwards compatibility.
     *
     * @return the total number of bytes read into the buffer, or
     *         <code>-1</code> if there is no more data because the end of the stream has been reached
     * @throws IOException if an I/O error occurs
     */
    @Override
    protected int readData() throws IOException {
        return readData(1);
    }

    /**
     * Creates a new byte array input without pre-allocated buffer.
     */
    public ByteArrayInput() {
    }

    /**
     * Creates a new byte array input with specified buffer as its input.
     *
     * @param buf the input buffer
     */
    public ByteArrayInput(byte[] buf) {
        setBuffer(buf);
    }

    /**
     * Creates a new byte array input with specified buffer and range as its input.
     * The position is set to <code>offset</code>, the limit is set to <code>offset+length</code>.
     *
     * @param buf the input buffer
     * @param offset the offset in the buffer of the first byte to read
     * @param length the maximum number of bytes to read from the buffer
     * @throws IllegalArgumentException if buf, offset and length do not constitute a valid range
     */
    public ByteArrayInput(byte[] buf, int offset, int length) {
        setInput(buf, offset, length);
    }

    /**
     * Sets specified buffer and range as an input.
     * The total position is set to zero, the position is set to <code>offset</code>,
     * the limit is set to <code>offset+length</code>, the mark is invalidated.
     *
     * @param buf the input buffer
     * @param offset the offset in the buffer of the first byte to read
     * @param length the maximum number of bytes to read from the buffer
     * @throws IllegalArgumentException if buf, offset and length do not constitute a valid range
     */
    public void setInput(byte[] buf, int offset, int length) {
        setBuffer(buf);
        setLimit(offset + length);
        setPosition(offset);
    }

    /**
     * Returns byte array used for buffering. Never returns null.
     */
    public byte[] getBuffer() {
        return buffer;
    }

    /**
     * Sets specified byte array to be used for buffering. Null is treated as empty array.
     * The total position is set to zero, the position is set to zero,
     * the limit is set to the capacity of new buffer, the mark is invalidated.
     */
    public void setBuffer(byte[] newBuffer) {
        if (newBuffer == null)
            newBuffer = EMPTY_BYTE_ARRAY;
        buffer = newBuffer;
        position = 0;
        limit = buffer.length;
        totalPositionBase = 0;
        markPosition = -1;
    }

    /**
     * Returns position.
     */
    public int getPosition() {
        return position;
    }

    /**
     * Sets position as specified.
     * The total position is adjusted by the same distance as position.
     * If the position is smaller than the mark position then the mark is invalidated.
     *
     * @throws IllegalArgumentException if the new position is negative or is larger than the limit
     */
    public void setPosition(int newPosition) {
        if (newPosition < 0 || newPosition > limit)
            throw new IllegalArgumentException();
        position = newPosition;
        if (position < markPosition)
            markPosition = -1;
    }

    /**
     * Returns limit.
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Sets limit as specified. If the position is larger than the new limit then it is set to the new limit
     * and total position is adjusted by the same distance as position.
     * If the limit is smaller than the mark position then the mark is invalidated.
     *
     * @throws IllegalArgumentException if the new limit is negative or is larger than the capacity
     */
    public void setLimit(int newLimit) {
        if (newLimit < 0 || newLimit > buffer.length)
            throw new IllegalArgumentException();
        limit = newLimit;
        if (position > limit)
            position = limit;
        if (position < markPosition)
            markPosition = -1;
    }

    /**
     * Sets total position as specified.
     * This method does not reposition the stream, it merely changes total position value as requested.
     *
     * @throws IllegalArgumentException if the new total position is negative
     */
    public void setTotalPosition(long totalPosition) {
        if (totalPosition < 0)
            throw new IllegalArgumentException();
        totalPositionBase = totalPosition - position;
    }

    /**
     * Clears this byte array input.
     * The {@link #totalPosition() totalPosition} is set to zero,
     * the {@link #getPosition() position} is set to zero,
     * the {@link #getLimit() limit} is set to the capacity,
     * the mark is invalidated.
     */
    public void clear() {
        position = 0;
        limit = buffer.length;
        totalPositionBase = 0;
        markPosition = -1;
    }

    /**
     * Ensures that the byte array used for buffering has at least the specified capacity.
     * This method reallocates buffer if needed and copies content of old buffer into new one.
     */
    public void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity > buffer.length)
            buffer = ArrayUtil.grow(buffer, Math.max(MIN_CAPACITY, requiredCapacity));
    }

    public void rewind(long n) {
        checkRewind(n); // throws exception if cannot rewind for specified distance
        position -= n;
    }
}
