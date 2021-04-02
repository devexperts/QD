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

import java.io.DataOutput;
import java.io.IOException;
import java.io.UTFDataFormatException;

/**
 * The <code>ByteArrayDataOutput</code> implements <code>DataOutput</code> interface
 * using byte array buffer.
 *
 * @deprecated Use {@link com.devexperts.io.ByteArrayOutput} class instead.
 */
@Deprecated
public class ByteArrayDataOutput extends ByteArrayBuffer implements DataOutput {

    /**
     * Returns full copy of bytes buffer from zero to current position.
     * @return full copy of bytes buffer from zero to current position.
     */
    public byte[] toByteArray() {
        byte[] data = new byte[position];
        if (position > 0) // otherwise NPE may be thrown
            System.arraycopy(buffer, 0, data, 0, position);
        return data;
    }

    // ========== DataOutput Implementation ==========

    public void write(int b) throws IOException {
        if (position >= limit)
            expandLimit(position + 1);
        buffer[position++] = (byte) b;
    }

    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    public void write(byte[] b, int off, int len) throws IOException {
        if ((off | len | (off + len) | (b.length - (off + len))) < 0)
            throw new IndexOutOfBoundsException();
        if (position + len > limit)
            expandLimit(position + len);
        System.arraycopy(b, off, buffer, position, len);
        position += len;
    }

    public void writeBoolean(boolean v) throws IOException {
        if (position >= limit)
            expandLimit(position + 1);
        buffer[position++] = v ? (byte) 1 : (byte) 0;
    }

    public void writeByte(int v) throws IOException {
        if (position >= limit)
            expandLimit(position + 1);
        buffer[position++] = (byte) v;
    }

    public void writeShort(int v) throws IOException {
        if (position + 2 > limit)
            expandLimit(position + 2);
        buffer[position++] = (byte) (v >>> 8);
        buffer[position++] = (byte) v;
    }

    public void writeChar(int v) throws IOException {
        if (position + 2 > limit)
            expandLimit(position + 2);
        buffer[position++] = (byte) (v >>> 8);
        buffer[position++] = (byte) v;
    }

    public void writeInt(int v) throws IOException {
        if (position + 4 > limit)
            expandLimit(position + 4);
        buffer[position++] = (byte) (v >>> 24);
        buffer[position++] = (byte) (v >>> 16);
        buffer[position++] = (byte) (v >>> 8);
        buffer[position++] = (byte) v;
    }

    public void writeLong(long v) throws IOException {
        if (position + 8 > limit)
            expandLimit(position + 8);
        buffer[position++] = (byte) (v >>> 56);
        buffer[position++] = (byte) (v >>> 48);
        buffer[position++] = (byte) (v >>> 40);
        buffer[position++] = (byte) (v >>> 32);
        buffer[position++] = (byte) (v >>> 24);
        buffer[position++] = (byte) (v >>> 16);
        buffer[position++] = (byte) (v >>> 8);
        buffer[position++] = (byte) v;
    }

    public void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    public void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    public void writeBytes(String str) throws IOException {
        int strlen = str.length();
        if (position + strlen > limit)
            expandLimit(position + strlen);
        for (int i = 0; i < strlen; i++)
            buffer[position++] = (byte) str.charAt(i);
    }

    public void writeChars(String str) throws IOException {
        int strlen = str.length();
        if (position + (strlen << 1) > limit)
            expandLimit(position + (strlen << 1));
        for (int i = 0; i < strlen; i++) {
            int c = str.charAt(i);
            buffer[position++] = (byte) (c >>> 8);
            buffer[position++] = (byte) c;
        }
    }

    public void writeUTF(String str) throws IOException {
        int strlen = str.length();
        int utflen = 0;
        for (int i = strlen; --i >= 0;) {
            int c = str.charAt(i);
            if (c <= 0x007F && c > 0)
                utflen++;
            else if (c <= 0x07FF)
                utflen += 2;
            else
                utflen += 3;
        }
        if (utflen > 65535)
            throw new UTFDataFormatException();

        if (position + utflen + 2 > limit)
            expandLimit(position + utflen + 2);
        buffer[position++] = (byte) (utflen >>> 8);
        buffer[position++] = (byte) utflen;
        for (int i = 0; i < strlen; i++) {
            int c = str.charAt(i);
            if (c <= 0x007F && c > 0) {
                buffer[position++] = (byte) c;
            } else if (c <= 0x07FF) {
                buffer[position++] = (byte) (0xC0 | (c >> 6));
                buffer[position++] = (byte) (0x80 | (c & 0x3F));
            } else {
                buffer[position++] = (byte) (0xE0 | (c >> 12));
                buffer[position++] = (byte) (0x80 | ((c >> 6) & 0x3F));
                buffer[position++] = (byte) (0x80 | (c & 0x3F));
            }
        }
    }
}
