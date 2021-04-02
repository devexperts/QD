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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutput;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.ByteBuffer;

/**
 * An efficient buffered implementation of data output API.
 * It is attributed with underlying byte buffer, position and limit:
 * <ul>
 * <li> The underlying byte buffer provides space for and holds data being written. It is never <code>null</code>.
 * <li> The position is an index of the next byte to be written. It is never negative and is never greater than the limit.
 * <li> The limit restricts allowed values for position. It is usually equal to underlying buffer capacity.
 * </ul>
 *
 * <p>This class is <b>not thread-safe</b>.
 */
public abstract class BufferedOutput extends OutputStream implements ObjectOutput {

    protected static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    protected byte[] buffer; // May not be 'null' - use empty buffer instead.
    protected int position;
    protected int limit;
    protected long totalPositionBase; // Defined so that (totalPosition == totalPositionBase + position) is true.

    /**
     * Creates new instance with empty buffer.
     */
    protected BufferedOutput() {
        buffer = EMPTY_BYTE_ARRAY;
    }

    /**
     * This method is invoked when output methods need more space to write data.
     * This method must ensure that expression <code>(position &lt; limit)</code>
     * is true or throw an exception. This method may block if needed.
     * This method is allowed to update {@link #buffer}, {@link #position} and
     * {@link #limit} fields as needed and should maintain {@link #totalPositionBase} accordingly.
     *
     * @throws IOException if an I/O error occurs
     */
    protected abstract void needSpace() throws IOException;

    /**
     * Throws {@link IllegalStateException} if expression <code>(position != limit)</code> is true.
     */
    protected final void checkEOB() {
        if (position != limit)
            throw new IllegalStateException(position < limit ? "buffer has unprocessed data" : "buffer position is beyond limit");
    }

    // ========== Position API ==========

    /**
     * Returns total position of this output stream.
     * The total position is defined as an offset of the next byte to be written from the beginning of the stream.
     * It is equal to the number of written bytes.
     */
    public final long totalPosition() {
        return totalPositionBase + position;
    }

    // ========== OutputStream, DataOutput and ObjectOutput API ==========

    /**
     * {@inheritDoc}
     */
    @Override
    public final void write(int b) throws IOException {
        if (position >= limit)
            needSpace();
        buffer[position++] = (byte) b;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        IOUtil.checkRange(b, off, len);
        while (len > 0) {
            if (position >= limit)
                needSpace();
            int n = Math.min(len, limit - position);
            System.arraycopy(b, off, buffer, position, n);
            position += n;
            off += n;
            len -= n;
        }
    }

    /**
     * {@inheritDoc}
     */
    public final void writeBoolean(boolean v) throws IOException {
        if (position >= limit)
            needSpace();
        buffer[position++] = v ? (byte) 1 : (byte) 0;
    }

    /**
     * {@inheritDoc}
     */
    public final void writeByte(int v) throws IOException {
        if (position >= limit)
            needSpace();
        buffer[position++] = (byte) v;
    }

    /**
     * {@inheritDoc}
     */
    public final void writeShort(int v) throws IOException {
        if (limit - position < 2) {
            writeByte(v >>> 8);
            writeByte(v);
            return;
        }
        buffer[position++] = (byte) (v >>> 8);
        buffer[position++] = (byte) v;
    }

    /**
     * {@inheritDoc}
     */
    public final void writeChar(int v) throws IOException {
        if (limit - position < 2) {
            writeByte(v >>> 8);
            writeByte(v);
            return;
        }
        buffer[position++] = (byte) (v >>> 8);
        buffer[position++] = (byte) v;
    }

    /**
     * {@inheritDoc}
     */
    public final void writeInt(int v) throws IOException {
        if (limit - position < 4) {
            writeShort(v >>> 16);
            writeShort(v);
            return;
        }
        buffer[position++] = (byte) (v >>> 24);
        buffer[position++] = (byte) (v >>> 16);
        buffer[position++] = (byte) (v >>> 8);
        buffer[position++] = (byte) v;
    }

    /**
     * {@inheritDoc}
     */
    public final void writeLong(long v) throws IOException {
        if (limit - position < 8) {
            writeInt((int) (v >>> 32));
            writeInt((int) v);
            return;
        }
        buffer[position++] = (byte) (v >>> 56);
        buffer[position++] = (byte) (v >>> 48);
        buffer[position++] = (byte) (v >>> 40);
        buffer[position++] = (byte) (v >>> 32);
        buffer[position++] = (byte) (v >>> 24);
        buffer[position++] = (byte) (v >>> 16);
        buffer[position++] = (byte) (v >>> 8);
        buffer[position++] = (byte) v;
    }

    /**
     * {@inheritDoc}
     */
    public final void writeFloat(float v) throws IOException {
        writeInt(Float.floatToIntBits(v));
    }

    /**
     * {@inheritDoc}
     */
    public final void writeDouble(double v) throws IOException {
        writeLong(Double.doubleToLongBits(v));
    }

    /**
     * {@inheritDoc}
     */
    public final void writeBytes(String str) throws IOException {
        int len = str.length();
        for (int i = 0; i < len; i++)
            writeByte(str.charAt(i));
    }

    /**
     * {@inheritDoc}
     */
    public final void writeChars(String str) throws IOException {
        int len = str.length();
        for (int i = 0; i < len; i++)
            writeChar(str.charAt(i));
    }

    /**
     * {@inheritDoc}
     */
    public final void writeUTF(String str) throws IOException {
        int len = str.length();
        int utfLen = 0;
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c > 0 && c <= 0x007F)
                utfLen++;
            else if (c <= 0x07FF)
                utfLen += 2;
            else
                utfLen += 3;
        }
        if (utfLen < len || utfLen > 65535) // Ingenious check that 'true' utfLen > Integer.MAX_VALUE.
            throw new UTFDataFormatException();

        writeShort(utfLen);
        for (int i = 0; i < len; i++) {
            char c = str.charAt(i);
            if (c > 0 && c <= 0x007F)
                writeByte(c);
            else if (c <= 0x07FF)
                writeUTF2Unchecked(c);
            else
                writeUTF3Unchecked(c);
        }
    }

    /**
     * Write an object to the underlying storage or stream in a compact encapsulation format
     * using {@link IOUtil#writeObject(DataOutput, Object) IOUtil.writeObject}.
     *
     * @param obj the object to be written.
     * @throws IOException if an I/O error occurs
     */
    public final void writeObject(Object obj) throws IOException {
        IOUtil.writeObject(this, obj);
    }

    /**
     * Writes a {@link Marshalled} object to a byte stream.
     * This method writes object length using
     * {@link Marshaller#writeMarshalledLength(BufferedOutput, int) Marshaller.writeMarshalledLength}, followed
     * by the contents of the marshalled byte array.
     *
     * <p>When the {@link Marshaller#SERIALIZATION serialization} marshaller
     * is used in marshalled object, then this method writes the same bytes as would have been been produced by
     * {@link #writeObject(Object) writeObject} method on the {@link Marshalled#getObject() object repesentation}
     * of the marshalled object. The corresponding bytes can be later read either using
     * <code>{@link BufferedInput#readMarshalled(Marshaller) BufferedInput.readMarshalled}({@link Marshaller#SERIALIZATION Marshaller.SERIALIZATION})</code>
     * or using {@link BufferedInput#readObject()}.
     *
     * @param marshalled the marshalled object to be written.
     * @throws IOException if an I/O error occurs
     */
    public final void writeMarshalled(Marshalled<?> marshalled) throws IOException {
        Marshaller<?> marshaller = marshalled.getMarshaller();
        byte[] bytes = marshalled.getBytes();
        if (bytes == null) {
            marshaller.writeMarshalledLength(this, -1);
            return;
        }
        marshaller.writeMarshalledLength(this, bytes.length);
        write(bytes);
    }

    // ========== Compact API ==========

    /**
     * Writes an <code>int</code> value in a compact format (see {@link IOUtil}).
     * Equivalent to {@link IOUtil#writeCompactInt} method.
     *
     * @param v the <code>int</code> value to be written
     * @throws IOException if an I/O error occurs
     */
    public final void writeCompactInt(int v) throws IOException {
        if (v >= 0) {
            if (v < 0x40) {
                writeByte(v);
            } else if (v < 0x2000) {
                writeShort(0x8000 | v);
            } else if (v < 0x100000) {
                writeByte(0xC0 | (v >> 16));
                writeShort(v);
            } else if (v < 0x08000000) {
                writeInt(0xE0000000 | v);
            } else {
                writeByte(0xF0);
                writeInt(v);
            }
        } else {
            if (v >= -0x40) {
                writeByte(0x7F & v);
            } else if (v >= -0x2000) {
                writeShort(0xBFFF & v);
            } else if (v >= -0x100000) {
                writeByte(0xDF & (v >> 16));
                writeShort(v);
            } else if (v >= -0x08000000) {
                writeInt(0xEFFFFFFF & v);
            } else {
                writeByte(0xF7);
                writeInt(v);
            }
        }
    }

    /**
     * Writes a <code>long</code> value in a compact format (see {@link IOUtil}).
     * Equivalent to {@link IOUtil#writeCompactLong} method.
     *
     * @param v the <code>long</code> value to be written
     * @throws IOException if an I/O error occurs
     */
    public final void writeCompactLong(long v) throws IOException {
        if (v == (long) (int) v) {
            writeCompactInt((int) v);
            return;
        }
        int hi = (int) (v >>> 32);
        if (hi >= 0) {
            if (hi < 0x04) {
                writeByte(0xF0 | hi);
            } else if (hi < 0x0200) {
                writeShort(0xF800 | hi);
            } else if (hi < 0x010000) {
                writeByte(0xFC);
                writeShort(hi);
            } else if (hi < 0x800000) {
                writeInt(0xFE000000 | hi);
            } else {
                writeByte(0xFF);
                writeInt(hi);
            }
        } else {
            if (hi >= -0x04) {
                writeByte(0xF7 & hi);
            } else if (hi >= -0x0200) {
                writeShort(0xFBFF & hi);
            } else if (hi >= -0x010000) {
                writeByte(0xFD);
                writeShort(hi);
            } else if (hi >= -0x800000) {
                writeInt(0xFEFFFFFF & hi);
            } else {
                writeByte(0xFF);
                writeInt(hi);
            }
        }
        writeInt((int) v);
    }

    /**
     * Writes an array of bytes in a compact encapsulation format.
     * This method defines length as a number of bytes.
     * Equivalent to {@link IOUtil#writeByteArray} method.
     *
     * @param bytes the byte array to be written
     * @throws IOException if an I/O error occurs
     */
    public final void writeByteArray(byte[] bytes) throws IOException {
        if (bytes == null) {
            writeCompactInt(-1);
            return;
        }
        writeCompactInt(bytes.length);
        write(bytes);
    }

    // ========== UTF API ==========

    /**
     * Writes a Unicode code point in a UTF-8 format.
     * The surrogate code points are accepted and written in a CESU-8 format.
     * Equivalent to {@link IOUtil#writeUTFChar} method.
     *
     * @param codePoint the code point to be written
     * @throws UTFDataFormatException if codePoint is not a valid Unicode character
     * @throws IOException if an I/O error occurs
     */
    public final void writeUTFChar(int codePoint) throws IOException {
        if (codePoint < 0)
            throw new UTFDataFormatException();
        if (codePoint <= 0x007F)
            writeByte(codePoint);
        else if (codePoint <= 0x07FF)
            writeUTF2Unchecked(codePoint);
        else if (codePoint <= 0xFFFF)
            writeUTF3Unchecked(codePoint);
        else if (codePoint <= 0x10FFFF)
            writeUTF4Unchecked(codePoint);
        else
            throw new UTFDataFormatException();
    }

    /**
     * Writes a string in a UTF-8 format with compact encapsulation (see {@link IOUtil}).
     * Unpaired surrogate code points are accepted and written in a CESU-8 format.
     * This method defines length as a number of bytes.
     * Equivalent to {@link IOUtil#writeUTFString} method.
     *
     * @param str the string to be written
     * @throws UTFDataFormatException if str is too long
     * @throws IOException if an I/O error occurs
     */
    public final void writeUTFString(String str) throws IOException {
        if (str == null) {
            writeCompactInt(-1);
            return;
        }
        int len = str.length();
        long utfLen = 0;
        for (int i = 0; i < len;) {
            char c = str.charAt(i++);
            if (c <= 0x007F)
                utfLen++;
            else if (c <= 0x07FF)
                utfLen += 2;
            else if (Character.isHighSurrogate(c) && i < len && Character.isLowSurrogate(str.charAt(i))) {
                i++;
                utfLen += 4;
            } else
                utfLen += 3;
        }

        writeCompactLong(utfLen);
        for (int i = 0; i < len;) {
            char c = str.charAt(i++);
            if (c <= 0x007F)
                writeByte(c);
            else if (c <= 0x07FF)
                writeUTF2Unchecked(c);
            else if (Character.isHighSurrogate(c) && i < len && Character.isLowSurrogate(str.charAt(i)))
                writeUTF4Unchecked(Character.toCodePoint(c, str.charAt(i++)));
            else
                writeUTF3Unchecked(c);
        }
    }

    // ========== Additional utility methods ==========

    /**
     * Writes data from specified input stream into this buffered output.
     * The number of bytes actually written is returned as an integer.
     *
     * @param in the source input stream to read from
     * @param length maximum number of bytes to write
     * @return total number of bytes written from the input stream
     * @throws IOException if an I/O error occurs
     */
    public long writeFromInputStream(InputStream in, long length) throws IOException {
        long result = 0;
        while (result < length) {
            if (position >= limit)
                needSpace();
            int n = (int) Math.min(length - result, limit - position);
            n = in.read(buffer, position, n);
            if (n <= 0)
                return result;
            result += n;
            position += n;
        }
        return result;
    }

    /**
     * Writes data from specified data input into this buffered output.
     *
     * @param in the source data input to read from
     * @param length number of bytes to write
     * @throws IOException if an I/O error occurs
     */
    public void writeFromDataInput(DataInput in, long length) throws IOException {
        while (length > 0) {
            if (position >= limit)
                needSpace();
            int n = (int) Math.min(length, limit - position);
            in.readFully(buffer, position, n);
            length -= n;
            position += n;
        }
    }

    /**
     * Writes data from specified byte buffer into this buffered output.
     *
     * @param buffer the source byte buffer to read from
     * @throws IOException if an I/O error occurs
     */
    public void writeFromByteBuffer(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (position >= limit)
                needSpace();
            int n = Math.min(buffer.remaining(), limit - position);
            buffer.get(this.buffer, position, n);
            position += n;
        }
    }

    /**
     * Writes data from specified chunk into this buffered output.
     * Returns the chunk into the pool (or invalidates it if it does not correspond to any pool),
     * unless the chunk was {@link Chunk#isReadOnly() read-only}.
     * A reference to the chunk is considered to become invalid after
     * invocation of this method and may no longer be used, unless the chunk was read-only.
     *
     * @param chunk the source chunk of bytes
     * @param owner current owner of the chunk
     * @throws IllegalStateException if the chunk is not
     *          {@link Chunk#isReadOnly() read-only} and its current owner differs from the one specified
     * @throws IOException if an I/O error occurs
     */
    public void writeFromChunk(Chunk chunk, Object owner) throws IOException {
        write(chunk.getBytes(), chunk.getOffset(), chunk.getLength());
        chunk.recycle(owner);
    }

    /**
     * Writes all data from specified chunk list into this buffered output.
     * Returns the chunk into the pool (or invalidates it if it does not correspond to any pool),
     * unless the chunk list was {@link ChunkList#isReadOnly() read-only}.
     * A reference to the chunk list is considered to become invalid after
     * invocation of this method and may no longer be used, unless the chunk list was read-only.
     *
     * @param chunks the source chunk list
     * @param owner current owner of the chunk list
     * @throws IllegalStateException if the chunk list is not
     *          {@link ChunkList#isReadOnly() read-only} and its current owner differs from the one specified
     * @throws IOException if an I/O error occurs
     */
    public void writeAllFromChunkList(ChunkList chunks, Object owner) throws IOException {
        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            write(chunk.getBytes(), chunk.getOffset(), chunk.getLength());
        }
        chunks.recycle(owner);
    }

    // ========== Implementation Details ==========

    // ========== Performance Note ==========
    // The inlining of methods by HotSpot compiler is limited by total method length.
    // For higher performance we shall extract rarely used code into separate methods.
    // Although it also allows code reuse, the code reuse is NOT a goal by itself.

    private void writeUTF2Unchecked(int codePoint) throws IOException {
        writeShort(0xC080 | codePoint << 2 & 0x1F00 | codePoint & 0x3F);
    }

    private void writeUTF3Unchecked(int codePoint) throws IOException {
        writeByte(0xE0 | codePoint >>> 12);
        writeShort(0x8080 | codePoint << 2 & 0x3F00 | codePoint & 0x3F);
    }

    private void writeUTF4Unchecked(int codePoint) throws IOException {
        writeInt(0xF0808080 | codePoint << 6 & 0x07000000 | codePoint << 4 & 0x3F0000 | codePoint << 2 & 0x3F00 | codePoint & 0x3F);
    }

}
