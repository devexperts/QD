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

import com.devexperts.util.ArrayUtil;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.io.UTFDataFormatException;

/**
 * The <code>ByteArrayDataInput</code> implements <code>DataInput</code> interface
 * using byte array buffer.
 *
 * @deprecated Use {@link com.devexperts.io.ByteArrayInput} class instead.
 */
@Deprecated
public class ByteArrayDataInput extends ByteArrayBuffer implements DataInput {

    /**
     * Makes an attempt to read <code>n</code> bytes of data into the buffer.
     * This method may read smaller number of bytes (possibly zero) or larger
     * number of bytes depending on availability.
     * This method never throws an <code>EOFException</code>.
     * The actual number of bytes read is returned.
     * <p>
     * This method is used by {@link #skipBytes} and {@link #readLine}
     * methods instead of an {@link #expandLimit} method.
     * <p>
     * The default implementation simply returns zero.
     *
     * @throws IOException if an I/O error occurs.
     */
    protected int readAvailableBytes(int n) throws IOException {
        return 0;
    }

    // ========== DataInput Implementation ==========

    public void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    public void readFully(byte[] b, int off, int len) throws IOException {
        if ((off | len | (off + len) | (b.length - (off + len))) < 0)
            throw new IndexOutOfBoundsException();
        if (position + len > limit)
            expandLimit(position + len);
        System.arraycopy(buffer, position, b, off, len);
        position += len;
    }

    public int skipBytes(int n) throws IOException {
        if (n <= 0)
            return 0;
        if (n <= limit - position) {
            position += n;
            return n;
        }
        int total = limit - position;
        int cur = 0;
        while (total < n && (cur = readAvailableBytes(n - total)) > 0)
            total += cur;
        position += total;
        return total;
    }

    public boolean readBoolean() throws IOException {
        if (position >= limit)
            expandLimit(position + 1);
        return buffer[position++] != 0;
    }

    public byte readByte() throws IOException {
        if (position >= limit)
            expandLimit(position + 1);
        return buffer[position++];
    }

    public int readUnsignedByte() throws IOException {
        if (position >= limit)
            expandLimit(position + 1);
        return buffer[position++] & 0xFF;
    }

    public short readShort() throws IOException {
        if (position + 2 > limit)
            expandLimit(position + 2);
        return (short) ((buffer[position++] << 8) + (buffer[position++] & 0xFF));
    }

    public int readUnsignedShort() throws IOException {
        if (position + 2 > limit)
            expandLimit(position + 2);
        return ((buffer[position++] & 0xFF) << 8) + (buffer[position++] & 0xFF);
    }

    public char readChar() throws IOException {
        if (position + 2 > limit)
            expandLimit(position + 2);
        return (char) ((buffer[position++] << 8) + (buffer[position++] & 0xFF));
    }

    public int readInt() throws IOException {
        if (position + 4 > limit)
            expandLimit(position + 4);
        return (buffer[position++] << 24) + ((buffer[position++] & 0xFF) << 16) +
            ((buffer[position++] & 0xFF) << 8) + (buffer[position++] & 0xFF);
    }

    public long readLong() throws IOException {
        if (position + 8 > limit)
            expandLimit(position + 8);
        return ((buffer[position++] & 0xFFL) << 56) + ((buffer[position++] & 0xFFL) << 48) +
            ((buffer[position++] & 0xFFL) << 40) + ((buffer[position++] & 0xFFL) << 32) +
            ((buffer[position++] & 0xFFL) << 24) + ((buffer[position++] & 0xFFL) << 16) +
            ((buffer[position++] & 0xFFL) << 8) + (buffer[position++] & 0xFFL);
    }

    public float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    public double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    public String readLine() throws IOException {
        if (position >= limit && readAvailableBytes(1) <= 0)
            return null;
        char[] chars = new char[128];
        int count = 0;
        while (position < limit || readAvailableBytes(1) > 0) {
            int c = buffer[position++] & 0xFF;
            if (c == '\n')
                break;
            if (c == '\r') {
                if ((position < limit || readAvailableBytes(1) > 0) && buffer[position] == '\n')
                    position++;
                break;
            }
            if (count >= chars.length)
                chars = ArrayUtil.grow(chars, 0);
            chars[count++] = (char) c;
        }
        return new String(chars, 0, count);
    }

    public String readUTF() throws IOException {
        int utflen = readUnsignedShort();
        int end_position = position + utflen;
        if (end_position < limit)
            expandLimit(end_position);
        char[] chars = new char[utflen];
        int count = 0;
        while (position < end_position) {
            int c = buffer[position++];
            if (c < 0) { // multi-byte case
                if ((c & 0xE0) == 0xC0) { // 2-byte case
                    c = c & 0x1F;
                } else if ((c & 0xF0) == 0xE0) { // 3-byte case
                    if (position >= end_position || (buffer[position] & 0xC0) != 0x80)
                        throw new UTFDataFormatException();
                    c = ((c & 0x0F) << 6) + (buffer[position++] & 0x3F);
                } else
                    throw new UTFDataFormatException();
                if (position >= end_position || (buffer[position] & 0xC0) != 0x80)
                    throw new UTFDataFormatException();
                c = (c << 6) + (buffer[position++] & 0x3F);
            }
            chars[count++] = (char) c;
        }
        return new String(chars, 0, count);
    }

    /**
     * This implementation throws {@link EndOfBufferException}
     */
    public void expandLimit(int required_limit) throws IOException {
        if (eob_instance == null)
            eob_instance = new EndOfBufferException();
        throw eob_instance;
    }

    private static EndOfBufferException eob_instance; // try to reuse this exception's instance

    /**
     * This exception is thrown when attempt to read past limit is made.
     * This exception's stacktrace is not reliable -- it may be reused by this class.
     */
    public static class EndOfBufferException extends EOFException {
        public EndOfBufferException() {
            super("End of buffer");
        }
    }
}
