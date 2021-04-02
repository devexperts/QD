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
import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkPool;
import com.devexperts.io.ChunkedInput;
import com.devexperts.qd.DataScheme;
import com.devexperts.util.SystemProperties;

/**
 * Deprecated class that parses QTP messages in binary format from a single byte array.
 *
 * <p>Use {@link BinaryQTPParser} instead. Note, that unlike this {@code ByteArrayParser}, you
 * have to specify an input to use with {@code BinaryQTPParser} using its
 * {@link BinaryQTPParser#setInput(BufferedInput) setInput} method. The recommended choice of input is
 * {@link ChunkedInput} which work with a {@link ChunkList} of byte arrays that can come from a
 * {@link ChunkPool pool}.
 *
 * @deprecated Use {@link BinaryQTPParser}
 */
public class ByteArrayParser extends BinaryQTPParser {
    /**
     * Defines default size of the parser buffer that is kept in each parser instance. If received packet exceeds the
     * size of the buffer, then larger buffer is allocated and becomes garbage when the message is processed.
     */
    private static final int POOLED_BUFFER_SIZE =
        SystemProperties.getIntProperty("com.devexperts.qd.qtp.parserBufferSize", 20000);

    // ======================== protected instance fields ========================

    /**
     * Exposed input for backwards compatibility with code that invokes <code>in.setBuffer</code>
     * to parse array of bytes.
     */
    protected final ByteArrayInput in;

    // ======================== private instance fields ========================

    private byte[] pooledBuffer;
    private boolean schemeKnown;

    // ======================== constructor and instance methods ========================

    /**
     * Constructs parser with a specified scheme that accepts messages without record descriptions.
     *
     * @param scheme data scheme to use.
     * @deprecated Use {@link BinaryQTPParser}
     */
    public ByteArrayParser(DataScheme scheme) {
        this(scheme, false);
    }

    /**
     * Constructs parser with a specified scheme and parse description mode.
     * {@code parseDescribe} is a legacy name. Record description messages are
     * always parsed by this class, but {@code parseDescribe} is {@code false},
     * then "schemeKnown" flag is set to {@code true} and the parser will not
     * report error on records without descriptions.
     *
     * @param scheme data scheme to use.
     * @param parseDescribe parse description mode.
     * @deprecated Use {@link BinaryQTPParser}
     */
    public ByteArrayParser(DataScheme scheme, boolean parseDescribe) {
        super(scheme);
        schemeKnown = !parseDescribe;
        this.in = new ByteArrayInput();
        super.setInput(in);
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

    @Override
    protected boolean isSchemeKnown() {
        return schemeKnown;
    }

    // ------------------------ buffer management ------------------------

    /**
     * Returns number of bytes in the buffer.
     * @return number of bytes in the buffer.
     */
    public int getLimit() {
        return in.getLimit();
    }

    /**
     * Returns number of processed bytes ready to be removed.
     * @return number of processed bytes ready to be removed.
     */
    public int getProcessed() {
        return in.getPosition();
    }

    /**
     * Returns byte array buffer where data is stored. Valid index range
     * is from zero inclusive to the limit exclusive.
     * <p>
     * <b>Note:</b> the array instance used for buffering may change
     * if new messages are added or previous ones are removed.
     * @return byte array buffer.
     */
    public byte[] getBuffer() {
        return in.getBuffer();
    }

    /**
     * Removes specified number of bytes from the start of the buffer.
     *
     * @param n number of bytes to remove.
     * @throws IllegalArgumentException if specified number is negative
     *         or is larger than the number of processed bytes.
     */
    public void removeBytes(int n) {
        int processed = in.getPosition();
        int limit = in.getLimit();
        if (n < 0 || n > processed)
            throw new IllegalArgumentException();
        if (n == 0)
            return;
        // fast path to remove all bytes
        if (n == limit) {
            in.setBuffer(null);
            in.setPosition(0);
            in.setLimit(0);
            return;
        }
        // slow path to copy bytes around
        if (in.getBuffer() != pooledBuffer && limit - n < (POOLED_BUFFER_SIZE >> 1)) {
            byte[] pooledBuffer = getOrCreatePooledBuffer();
            System.arraycopy(in.getBuffer(), n, pooledBuffer, 0, limit - n);
            in.setBuffer(pooledBuffer);
        } else
            System.arraycopy(in.getBuffer(), n, in.getBuffer(), 0, limit - n);
        processed -= n;
        limit -= n;
        in.setPosition(processed);
        in.setLimit(limit);
    }

    public void resetBuffer() {
        in.setBuffer(null);
        in.setPosition(0);
        in.setLimit(0);
    }

    /**
     * Adds specified bytes to the end of byte array buffer.
     *
     * @param bytes array to get bytes from.
     * @param offset position of the first byte in <code>bytes</code>
     * @param length number of bytes to add.
     * @throws IndexOutOfBoundsException if copying would cause
     *         access of data outside specified byte array bounds.
     */
    public void addBytes(byte[] bytes, int offset, int length) {
        if ((offset | length | (offset + length) | (bytes.length - (offset + length))) < 0)
            throw new IndexOutOfBoundsException();
        if (length <= 0)
            return;
        int limit = in.getLimit();
        ensureCapacity(limit + length);
        System.arraycopy(bytes, offset, in.getBuffer(), limit, length);
        in.setLimit(limit + length);
    }

    /**
     * Sets new value for number of bytes in the buffer.
     *
     * @param newLimit new value for number of bytes in the buffer.
     * @throws IllegalArgumentException if specified limit is less than
     *         the number of processed bytes or is larger than the buffer capacity.
     */
    public void setLimit(int newLimit) {
        in.setLimit(newLimit);
    }

    /**
     * Ensures that byte array buffer has at least specified capacity.
     * <p>
     * <b>Note:</b> the array instance used for buffering may change
     * if new messages are added or previous ones are removed. This
     * means that specified capacity is valid only until next operation.
     * @param requiredCapacity capacity to ensure.
     */
    public void ensureCapacity(int requiredCapacity) {
        if (requiredCapacity <= 0)
            return;
        if (in.getBuffer().length == 0 && requiredCapacity <= POOLED_BUFFER_SIZE)
            in.setBuffer(getOrCreatePooledBuffer());
        else
            in.ensureCapacity(requiredCapacity);
    }

    private byte[] getOrCreatePooledBuffer() {
        if (pooledBuffer == null)
            pooledBuffer = new byte[POOLED_BUFFER_SIZE];
        return pooledBuffer;
    }
}
