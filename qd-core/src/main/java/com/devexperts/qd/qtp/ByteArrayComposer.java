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

import com.devexperts.io.BufferedOutput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.ChunkPool;
import com.devexperts.io.ChunkedOutput;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.stats.QDStats;

/**
 * Deprecated class that composes QTP messages in binary format into a single byte array.
 *
 * <p>Use {@link BinaryQTPComposer} instead. Note, that unlike this {@code ByteArrayComposer}, you
 * have to specify an output to use with {@code BinaryQTPComposer} using its
 * {@link BinaryQTPComposer#setOutput(BufferedOutput) setOutput} method. The recommended choice of output is
 * {@link ChunkedOutput} which takes byte arrays from a {@link ChunkPool pool} and keeps a list of byte array
 * instead of reallocation and copying.
 *
 * @deprecated Use {@link BinaryQTPComposer}.
 */
public class ByteArrayComposer extends BinaryQTPComposer {

    // ======================== protected instance fields ========================

    protected byte[] pooledBuffer;
    protected ByteArrayOutput out;

    // ======================== constructor and instance methods ========================

    public ByteArrayComposer(DataScheme scheme) {
        this(scheme, false);
    }

    public ByteArrayComposer(DataScheme scheme, boolean describeRecords) {
        super(scheme, describeRecords);
        out = new ByteArrayOutput(QTPConstants.COMPOSER_BUFFER_SIZE);
        pooledBuffer = out.getBuffer();
        super.setOutput(out);
    }

    /**
     * {@inheritDoc}
     * This implementation throws {@link UnsupportedOperationException}.
     */
    @Override
    public void setOutput(BufferedOutput output) {
        throw new UnsupportedOperationException();
    }

    // ------------------------ support for bounded-size messages ------------------------

    /**
     * Returns {@code true} when the overall number of bytes composed had not exceeded the threshold yet.
     */
    @Override
    public boolean hasCapacity() {
        return out.getPosition() + getMessagePayloadSize() < QTPConstants.COMPOSER_THRESHOLD;
    }

    // ------------------------ top-level composing ------------------------

    /**
     * Composes message from the corresponding message provides and update IO bytes written in stats.
     */
    public boolean compose(MessageProvider provider, QDStats stats) {
        setStats(stats);
        try {
            return compose(provider);
        } finally {
            setStats(QDStats.VOID);
        }
    }

    // ------------------------ buffer management ------------------------

    /**
     * Removes specified number of bytes from the start of the buffer.
     * This is a shortcut to {@link #removeBytes(int, int) removeBytes(0, n)}.
     *
     * @param n number of bytes to remove.
     *
     * @throws IllegalArgumentException if specified number is negative
     *         or is larger than the number of processed bytes.
     */
    public void removeBytes(int n) {
        removeBytes(0, n);
    }

    /**
     * Removes specified number of bytes from a specified part of the buffer
     * from offset {@code off} with a length of {@code len}.
     *
     * @param off offset of bytes to remove.
     * @param len number of bytes to remove.
     *
     * @throws IllegalArgumentException if off and/or len are out of valid range.
     */
    public void removeBytes(int off, int len) {
        int processed = out.getPosition();
        if (off < 0 || len < 0 || off + len > processed)
            throw new IllegalArgumentException("off=" + off + ", len=" + len + ", processed=" + processed);
        if (len == 0)
            return;
        int newLen = processed - len;
        if (out.getBuffer() != pooledBuffer && newLen < (pooledBuffer.length >> 1)) {
            if (newLen > 0) {
                if (off > 0)
                    System.arraycopy(out.getBuffer(), 0, pooledBuffer, 0, off);
                System.arraycopy(out.getBuffer(), off + len, pooledBuffer, off, newLen - off);
            }
            out.setBuffer(pooledBuffer);
        } else
            System.arraycopy(out.getBuffer(), off + len, out.getBuffer(), off, newLen - off);
        out.setPosition(newLen);
    }

    /**
     * Returns number of processed bytes ready to be sent over and removed.
     * @return number of processed bytes ready to be sent over and removed.
     */
    public int getProcessed() {
        return out.getPosition();
    }

    /**
     * Returns byte array buffer where data is stored. Valid index range
     * is from zero inclusive to the number of processed bytes exclusive.
     * <p>
     * <b>Note:</b> the array instance used for buffering may change
     * if new messages are added or previous ones are removed.
     * @return byte array buffer.
     */
    public byte[] getBuffer() {
        return out.getBuffer();
    }
}


