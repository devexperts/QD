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
package com.devexperts.qd.util;

import java.io.IOException;
import java.io.Serializable;

/**
 * The <code>ByteArrayBuffer</code> is a linear byte buffer designed for stream
 * implementations. Aside from its content, the essential properties of a buffer
 * are its position and limit.
 * <p>
 * <blockquote>
 *   A buffer's <i>position</i> is the index of the next byte to be read or written.
 *   A buffer's position is never negative and is never greater than its limit.
 *   A buffer's position changes as individual bytes are being read or written.
 *   <p>
 *   A buffer's <i>limit</i> is the index of the first byte that should not be read or written.
 *   A buffer's limit is never negative and is never greater than its current capacity.
 *   A buffer's limit changes as required or as external bulk operations command.
 * </blockquote>
 *
 * @deprecated No replacement
 */
public class ByteArrayBuffer implements Serializable {

    protected byte[] buffer;
    protected int position;
    protected int limit;

    // ========== Public API ==========

    /**
     * Returns the byte array that backs this buffer. May return null.
     */
    public byte[] getBuffer() {
        return buffer;
    }

    /**
     * Sets specified byte array to be used for buffering. Accepts null.
     * The position is set to zero, the limit is set to the capacity of new buffer.
     */
    public void setBuffer(byte[] new_buffer) {
        buffer = new_buffer;
        position = 0;
        limit = buffer == null ? 0 : buffer.length;
    }

    /**
     * Returns this buffer's position.
     */
    public int getPosition() {
        return position;
    }

    /**
     * Sets this buffer's position.
     *
     * @throws IllegalArgumentException if the new position is negative
     *         or is larger than the current limit.
     */
    public void setPosition(int new_position) {
        if (new_position < 0 || new_position > limit)
            throw new IllegalArgumentException();
        position = new_position;
    }

    /**
     * Returns this buffer's limit.
     */
    public int getLimit() {
        return limit;
    }

    /**
     * Sets this buffer's limit. If the position is larger than the new limit
     * then it is set to the new limit.
     *
     * @throws IllegalArgumentException if the new limit is negative
     *         or is larger than the current capacity.
     */
    public void setLimit(int new_limit) {
        int capacity = buffer == null ? 0 : buffer.length;
        if (new_limit < 0 || new_limit > capacity)
            throw new IllegalArgumentException();
        limit = new_limit;
        if (position > limit)
            position = limit;
    }

    /**
     * Ensures that the byte array has at least the specified capacity.
     * By default, this method reallocates byte array if needed and
     * copies content of old array into new one.
     */
    public void ensureCapacity(int required_capacity) {
        int capacity = buffer == null ? 0 : buffer.length;
        if (required_capacity <= capacity)
            return;
        int new_capacity = Math.max(Math.max(1024, capacity << 1), required_capacity);
        byte[] new_buffer = new byte[new_capacity];
        if (buffer != null && capacity > 0)
            System.arraycopy(buffer, 0, new_buffer, 0, capacity);
        buffer = new_buffer;
    }

    /**
     * Clears this buffer.
     * The position is set to zero, the limit is set to the current capacity.
     */
    public void clear() {
        position = 0;
        limit = buffer == null ? 0 : buffer.length;
    }

    /**
     * Copies a region of bytes in the buffer.
     * The position and limit remain unaffected.
     *
     * @throws IndexOutOfBoundsException if copying would cause
     *         access of data outside current buffer array bounds.
     */
    public void copy(int source_position, int dest_position, int length) {
        int capacity = buffer == null ? 0 : buffer.length;
        if ((source_position | dest_position | length |
            (source_position + length) | (dest_position + length) |
            (capacity - (source_position + length)) |
            (capacity - (dest_position + length))) < 0)
        {
            throw new IndexOutOfBoundsException();
        }
        if (buffer != null && source_position != dest_position && length > 0)
            System.arraycopy(buffer, source_position, buffer, dest_position, length);
    }

    /**
     * Expands byte array to the specified limit (or more). By default, this method
     * reallocates byte array if needed and copies content of old array into new one.
     * <p>
     * This method shall be used internally when position reaches limit.
     * This method may be overriden to change memory management behavior.
     *
     * @throws IOException if an I/O error occurs (expansion impossible).
     */
    public void expandLimit(int required_limit) throws IOException {
        if (required_limit <= limit)
            return;
        ensureCapacity(required_limit);
        limit = buffer == null ? 0 : buffer.length;
    }
}
