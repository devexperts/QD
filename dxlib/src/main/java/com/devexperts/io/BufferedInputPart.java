/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.io;

import java.io.IOException;

/**
 * A size-delimited part of {@link BufferedInput} for parsing of size-tagged packets.
 * The {@link #totalPosition() totalPosition} of the {@code BufferedInputPart} is the same as the {@code totalPosition} of the
 * underlying {@code BufferedInput}, but the number of bytes {@link #available() available} in the {@code BufferedInputPart}
 * is limited by the {@code length} specified in its {@link #BufferedInputPart(BufferedInput, long) constructor} or
 * in {@link #setInput(BufferedInput, long) setInput} method.
 *
 * <p>Reading from this {@code BufferedInputPart} does not
 * automatically advance position in the underlying input, but it does not stay fixed either. The position of
 * the underlying {@code BufferedInput} is advanced at unspecified times.
 * Use {@link #syncInputPosition() syncInputPosition} method to advance it to the current position of
 * this {@code BufferedInputPart} when needed.
 */
public final class BufferedInputPart extends BufferedInput {

    private BufferedInput in;
    private int start; // start of the data in the buffer
    private long availableSurplus; // bytes available beyond current limit

    /**
     * Creates {@code BufferedInputPart} object that needs to be bound to a particular underlying input
     * by {@link #setInput(BufferedInput, long) setInput} method before use.
     */
    public BufferedInputPart() {}

    /**
     * Creates {@code BufferedInputPart} object that is bound the part of the specified input and
     * is limited by the particular length. See {@link #setInput(BufferedInput, long) setInput} method
     * for all preconditions.
     *
     * @param in the underlying input.
     * @param length the length limit.
     * @throws NullPointerException if {@code in} is null.
     * @throws IllegalStateException if the underlying input is not marked properly.
     */
    public BufferedInputPart(BufferedInput in, long length) {
        setInput(in, length);
    }

    /**
     * Binds this {@code BufferedInputPart} object to the part of the specified input with
     * a limit by the particular length. This method can be called multiple times
     * to reuse this instance by binding it differently. The underlying {@link BufferedInput}
     * this method was previously bound to is ignored by this method for a predictable
     * reuse behavior regardless of how the previous use of this instance has terminated.
     * The state of this {@code BufferedInputPart} is completely reset as if it was created from scratch.
     *
     * <p>The underlying {@link BufferedInput} has to be {@link #mark() marked} before invocation of this method.
     *
     * <p>The behavior of this {@code BufferedInputPart} is undefined if the underlying {@code BufferedInput}
     * is changed in any way directly by reading from it or otherwise changing its position or mark before
     * this {@code BufferedInputPart} is disposed of or {@link #resetInput() unbound}.
     *
     * @param in the underlying input.
     * @param length the length limit.
     * @throws NullPointerException if {@code in} is null.
     * @throws IllegalStateException if the underlying input is not marked.
     */
    public void setInput(BufferedInput in, long length) {
        if (length < 0)
            throw new IllegalArgumentException("length=" + length);
        if (in.markPosition < 0)
            throw new IllegalStateException("underlying input is not marked");
        this.in = in;
        initBuffer(length);
        markPosition = -1;
    }

    /**
     * Updates underlying {@code BufferedInput} position so that it corresponds to the position that this
     * {@code BufferedInputPart} was read to. Unlike {@link #resetInput() resetInput} method,
     * this {@code BufferedInputPart} continues to be bound.
     * @throws NullPointerException if this {@code BufferedInputPart} is not bound.
     */
    public void syncInputPosition() {
        in.position = position; // pretend that parent stream was read up to same position as our
    }

    /**
     * Unbinds this {@code BufferedInputPart} object from the underlying input. This method
     * does nothing if this instance is not currently bound. This method <b>does not</b>
     * synchronize the underlying {@code BufferedInput} position with
     * {@link #syncInputPosition() syncInputPosition} method.
     */
    public void resetInput() {
        if (in == null)
            return;
        in = null;
        buffer = EMPTY_BYTE_ARRAY;
        position = 0;
        limit = 0;
        totalPositionBase = 0;
        markPosition = -1;
        start = 0;
        availableSurplus = 0;
    }

    /**
     * Unbinds this {@code BufferedInputPart} object from the underlying input with {@link #resetInput() resetInput} method.
     * The underlying {@code BufferedInput} is <b>not</b> closed.
     */
    @Override
    public void close() {
        resetInput();
    }

    /**
     * Checks if more bytes can be read from this input. This is a faster-working shortcut for available() &gt; 0 check.
     * This method returns {@code false} when this object is not bound.
     *
     * @return {@code true} if more bytes can be read from this input.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public boolean hasAvailable() throws IOException {
        if (position < limit)
            return true;
        if (availableSurplus > 0) {
            syncInputPosition(); // sync input position before calling to "in" (!!!)
            if (in.hasAvailable())
                return true;
        }
        return false;
    }

    /**
     * Checks if the specified number of bytes can be read from this input.
     * This is a faster-working shortcut for
     * <code>{@link #available available}() &gt;= bytes</code> check.
     * This method returns {@code bytes <= 0} when this object is not bound.
     *
     * @param bytes the number of bytes.
     * @return {@code true} if the specified number of bytes can be read from this input.
     * @throws NullPointerException if this object is not bound with {@link #setInput(BufferedInput, long) setInput}.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public boolean hasAvailable(int bytes) throws IOException {
        if (limit - position >= bytes)
            return true;
        if (limit - position + availableSurplus < bytes)
            return false;
        syncInputPosition(); // sync input position before calling to "in" (!!!)
        return in.hasAvailable(bytes);
    }

    /**
     * Returns the number of bytes that can be read without blocking.
     * The resulting number will not exceed {@code length} specified when binding this object
     * with {@link #setInput(BufferedInput, long) setInput} method.
     * This method returns {@code 0} when this object is not bound.
     *
     * @return the number of available bytes.
     * @throws IOException If an I/O error has occurred.
     */
    @Override
    public int available() throws IOException {
        if (availableSurplus == 0)
            return limit - position;
        syncInputPosition(); // sync input position before calling to "in" (!!!)
        return (int) Math.min(in.available(), limit - position + availableSurplus);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        IOUtil.checkRange(b, off, len);
        if (len == 0)
            return 0;
        if (len <= limit - position) {
            System.arraycopy(buffer, position, b, off, len);
            position += len;
            return len;
        } else {
            long available = limit - position + availableSurplus;
            if (available == 0)
                return -1;
            syncInputPosition(); // sync input position before calling to "in" (!!!)
            int result = in.read(b, off, (int) Math.min(len, available));
            initBuffer(available - Math.max(result, 0));
            return result;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(long n) throws IOException {
        long available = limit - position + availableSurplus;
        n = Math.min(n, available);
        if (n <= limit - position) {
            position += n;
        } else {
            syncInputPosition(); // sync input position before calling to "in" (!!!)
            n = in.skip(n);
            initBuffer(available - n);
        }
        return n;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rewind(long n) {
        checkRewind(n); // throws exception if cannot rewind for specified distance
        if (n <= position - start) {
            position -= n;
        } else {
            long available = limit - position + availableSurplus;
            syncInputPosition(); // sync input position before calling to "in" (!!!)
            in.rewind(n);
            initBuffer(available + n);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected int readData() throws IOException {
        checkEOB(); // throws exception if not all data was read
        if (availableSurplus <= 0)
            return -1; // no more bytes available beyond limit
        syncInputPosition(); // sync input position before calling to "in" (!!!)
        int result = in.readData();
        initBuffer(availableSurplus);
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void checkEncapsulatedLength(long length, long min, long max) throws IOException {
        if (length < min || length > max || length > limit - position + availableSurplus)
            throw new IOException("Illegal length: " + length);
    }

    private void initBuffer(long length) {
        buffer = in.buffer;
        position = in.position;
        limit = in.limit - position < length ? in.limit : (int) (position + length); // beware of overflows near MAX_VALUE
        start = position;
        availableSurplus = length + position - limit;
        totalPositionBase = in.totalPositionBase;
    }
}
