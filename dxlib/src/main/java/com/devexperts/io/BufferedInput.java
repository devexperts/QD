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

import com.devexperts.util.ArrayUtil;

import java.io.DataOutput;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.OutputStream;
import java.io.UTFDataFormatException;
import java.nio.ByteBuffer;

/**
 * An efficient buffered implementation of data input API.
 * It is attributed with underlying byte buffer, position and limit:
 * <ul>
 * <li> The underlying byte buffer holds data being read. It is never <code>null</code>.
 * <li> The position is an index of the next byte to be read. It is never negative and is never greater than the limit.
 * <li> The limit restricts allowed values for position. It usually points to the end of available data.
 * </ul>
 * The <b>BufferedInput</b> supports the notion of <b>total position</b> - an offset of the next byte to be read
 * from the beginning of the stream. It is equal to the number of bytes read from the stream adjusted according to
 * reposition methods like {@link #seek}, {@link #skip}, {@link #rewind} and other.
 *
 * <p>All <b>BufferedInput</b> implementations are required to support marking because proper marking
 * plays a vital role in definition and implementation of certain reposition methods.
 *
 * <p>This class is <b>not thread-safe</b>.
 */
public abstract class BufferedInput extends InputStream implements ObjectInput {

    protected static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    protected byte[] buffer; // May not be 'null' - use empty buffer instead.
    protected int position;
    protected int limit;
    protected long totalPositionBase; // Defined so that (totalPosition == totalPositionBase + position) is true.
    protected long markPosition = -1; // Total position when the stream was marked; negative value means not marked.

    /**
     * Creates new instance with empty buffer.
     */
    protected BufferedInput() {
        buffer = EMPTY_BYTE_ARRAY;
    }

    /**
     * This method is invoked when input methods need more bytes to read data.
     * This method must ensure that expression <code>(position &lt; limit)</code> is true or throw an exception.
     * This method may block if needed.
     * This method is allowed to update buffer, position, limit and totalPositionBase fields as needed.
     * <p>
     * This method shall throw an {@link EOFException} if the end of stream is reached.
     *
     * @throws IOException if an I/O error occurs or end of stream is reached
     */
    protected void needData() throws IOException {
        if (position >= limit && readData() <= 0)
            throwEOFException();
    }

    /**
     * This method is invoked when input methods need more bytes to read data.
     * This method shall make an attempt to read some bytes into the buffer.
     * This method returns number of bytes actually read or value <code>-1</code>
     * if no bytes can be read because the end of the stream has been reached.
     * This method may block if needed.
     * This method is allowed to update buffer, position, limit and totalPositionBase fields as needed.
     * <p>
     * This method shall never throw an {@link EOFException}.
     *
     * @return the total number of bytes read into the buffer, or
     *         <code>-1</code> if there is no more data because the end of the stream has been reached
     * @throws IOException if an I/O error occurs
     */
    protected abstract int readData() throws IOException;

    /**
     * Throws {@link EOFException} when requested by {@link #needData} method.
     * This implementation throws same reused instance with truncated stack trace to avoid garbage.
     */
    protected void throwEOFException() throws EOFException {
        throw EOF_EXCEPTION;
    }

    /**
     * Throws {@link IllegalStateException} if expression <code>(position != limit)</code> is true.
     */
    protected final void checkEOB() {
        if (position != limit)
            throw new IllegalStateException(position < limit ? "buffer has unprocessed data" : "buffer position is beyond limit");
    }

    /**
     * Validate <a href="IOUtil.html#compact-encapsulation">encapsulated content length</a> according to
     * specified bounds and stream limits.
     * <p>
     * Data retrieval methods getting variable amount of data can invoke this method to check an upper bound
     * of available data for validation.
     *
     * @param length encapsulated length value to be checked
     * @param min minimal theoretically valid length value
     * @param max maximal theoretically valid length value
     * @throws IOException if {@code length < min || length > max} or {@code length} bytes definitely cannot
     *     be retrieved
     */
    protected void checkEncapsulatedLength(long length, long min, long max) throws IOException {
        if (length < min || length > max)
            throw new IOException("Illegal length: " + length);
    }


    // ========== InputStream, DataInput and ObjectInput API ==========

    /**
     * {@inheritDoc}
     */
    @Override
    public final int read() throws IOException {
        if (position >= limit && readData() <= 0)
            return -1;
        return buffer[position++] & 0xFF;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        IOUtil.checkRange(b, off, len);
        if (len == 0)
            return 0;
        if (position >= limit && readData() <= 0)
            return -1;
        len = Math.min(len, limit - position);
        System.arraycopy(buffer, position, b, off, len);
        position += len;
        return len;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long skip(long n) throws IOException {
        long remaining = n;
        while (remaining > 0) {
            if (position >= limit && readData() <= 0)
                break;
            int skipping = (int) Math.min(remaining, limit - position);
            position += skipping;
            remaining -= skipping;
        }
        return n - remaining;
    }

    /**
     * Checks if more bytes can be read from this input.
     * This is a faster-working shortcut for
     * <code>{@link #available available}() &gt; 0</code> check.
     * This implementation returns
     * <code>{@link #hasAvailable(int) hasAvailable}(1)</code>
     *
     * @return {@code true} if more bytes can be read from this input.
     * @throws IOException if an I/O error occurs.
     */
    public boolean hasAvailable() throws IOException {
        return hasAvailable(1);
    }

    /**
     * Checks if the specified number of bytes can be read from this input.
     * This is a faster-working shortcut for
     * <code>{@link #available available}() &gt;= bytes</code> check.
     * This implementation returns
     * <code>{@link #available() available}() &gt;= bytes</code>
     *
     * @param bytes the number of bytes.
     * @return {@code true} if the specified number of bytes can be read from this input.
     * @throws IOException if an I/O error occurs.
     */
    public boolean hasAvailable(int bytes) throws IOException {
        return available() >= bytes;
    }

    /**
     * Returns the number of bytes that can be read without blocking.
     *
     * @return the number of available bytes.
     * @throws IOException If an I/O error has occurred.
     */
    @Override
    public int available() throws IOException {
        return limit - position;
    }

    /**
     * {@inheritDoc}
     */
    public final void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    /**
     * {@inheritDoc}
     */
    public void readFully(byte[] b, int off, int len) throws IOException {
        IOUtil.checkRange(b, off, len);
        while (len > 0) {
            int n = read(b, off, len);
            if (n <= 0)
                throw new EOFException();
            off += n;
            len -= n;
        }
    }

    /**
     * {@inheritDoc}
     */
    public final int skipBytes(int n) throws IOException {
        return (int) skip(n);
    }

    /**
     * {@inheritDoc}
     */
    public final boolean readBoolean() throws IOException {
        if (position >= limit)
            needData();
        return buffer[position++] != 0;
    }

    /**
     * {@inheritDoc}
     */
    public final byte readByte() throws IOException {
        if (position >= limit)
            needData();
        return buffer[position++];
    }

    /**
     * {@inheritDoc}
     */
    public final int readUnsignedByte() throws IOException {
        if (position >= limit)
            needData();
        return buffer[position++] & 0xFF;
    }

    /**
     * {@inheritDoc}
     */
    public final short readShort() throws IOException {
        if (limit - position < 2)
            return (short) ((readUnsignedByte() << 8) | readUnsignedByte());
        return (short) ((buffer[position++] << 8) | (buffer[position++] & 0xFF));
    }

    /**
     * {@inheritDoc}
     */
    public final int readUnsignedShort() throws IOException {
        if (limit - position < 2)
            return (readUnsignedByte() << 8) | readUnsignedByte();
        return ((buffer[position++] & 0xFF) << 8) | (buffer[position++] & 0xFF);
    }

    /**
     * {@inheritDoc}
     */
    public final char readChar() throws IOException {
        if (limit - position < 2)
            return (char) ((readUnsignedByte() << 8) | readUnsignedByte());
        return (char) ((buffer[position++] << 8) | (buffer[position++] & 0xFF));
    }

    /**
     * {@inheritDoc}
     */
    public final int readInt() throws IOException {
        if (limit - position < 4)
            return (readUnsignedShort() << 16) | readUnsignedShort();
        return (buffer[position++] << 24) | ((buffer[position++] & 0xFF) << 16) |
            ((buffer[position++] & 0xFF) << 8) | (buffer[position++] & 0xFF);
    }

    /**
     * {@inheritDoc}
     */
    public final long readLong() throws IOException {
        if (limit - position < 8)
            return ((long) readInt() << 32) | (readInt() & 0xFFFFFFFFL);
        return ((buffer[position++] & 0xFFL) << 56) | ((buffer[position++] & 0xFFL) << 48) |
            ((buffer[position++] & 0xFFL) << 40) | ((buffer[position++] & 0xFFL) << 32) |
            ((buffer[position++] & 0xFFL) << 24) | ((buffer[position++] & 0xFFL) << 16) |
            ((buffer[position++] & 0xFFL) << 8) | (buffer[position++] & 0xFFL);
    }

    /**
     * {@inheritDoc}
     */
    public final float readFloat() throws IOException {
        return Float.intBitsToFloat(readInt());
    }

    /**
     * {@inheritDoc}
     */
    public final double readDouble() throws IOException {
        return Double.longBitsToDouble(readLong());
    }

    /**
     * {@inheritDoc}
     */
    public final String readLine() throws IOException {
        if (position >= limit && readData() <= 0)
            return null;
        char[] chars = getCharBuffer(128);
        int count = 0;
        while (position < limit || readData() > 0) {
            int c = buffer[position++] & 0xFF;
            if (c == '\n')
                break;
            if (c == '\r') {
                if ((position < limit || readData() > 0) && buffer[position] == '\n')
                    position++;
                break;
            }
            if (count >= chars.length)
                chars = ArrayUtil.grow(chars, 0);
            chars[count++] = (char) c;
            if (count >= Integer.MAX_VALUE)
                break;
        }
        return new String(chars, 0, count);
    }

    /**
     * {@inheritDoc}
     */
    public final String readUTF() throws IOException {
        int utfLen = readUnsignedShort();
        if (utfLen == 0)
            return "";
        checkEncapsulatedLength(utfLen, 0, Integer.MAX_VALUE);
        char[] chars = getCharBuffer(utfLen);
        return String.valueOf(chars, 0, readUTFBody(utfLen, chars, 0));
    }

    /**
     * Read and return an object.
     * This methods loads classes using a context class loader
     * (see {@link Thread#getContextClassLoader() Thread.getContextClassLoader}) or
     * using the same classloader that loaded classes for this {@code com.devexperts.io} package when
     * context class loader is not defined.
     * This is a shortcut for <code>{@link #readObject(ClassLoader) readObject}(null)</code>.
     *
     * @return the object read from the stream
     * @throws IOException if an I/O error occurs or if object cannot be deserialized
     */
    public final Object readObject() throws IOException {
        return IOUtil.readObject(this);
    }

    /**
     * Read and return an object with a specified class loader.
     *
     * @param cl the ClassLoader that will be used to load classes;
     *           <code>null</code> for {@link Thread#getContextClassLoader() context} class loader.
     * @return the object read from the stream
     * @throws IOException if an I/O error occurs or if object cannot be deserialized
     */
    public final Object readObject(ClassLoader cl) throws IOException {
        return IOUtil.readObject(this, cl);
    }

    /**
     * Read and return an object with a specified serial class context.
     *
     * @param serialContext the serial class context.
     * @return the object read from the stream
     * @throws IOException if an I/O error occurs or if object cannot be deserialized
     */
    public final Object readObject(SerialClassContext serialContext) throws IOException {
        return IOUtil.readObject(this, serialContext);
    }

    /**
     * Reads {@link Marshalled} object from a byte stream with a specified marshaller.
     * This method determines the number of marshalled bytes using
     * {@link Marshaller#readMarshalledLength(BufferedInput) Marshaller.readMarshalledLength},
     * reads the corresponding number of bytes, and wraps them into {@link Marshalled} object instance.
     * The result of this method is not {@code null}.
     *
     * <p>When the {@link Marshaller#SERIALIZATION serialization} marshaller is used, then this method
     * reads the same bytes as would have been been read by {@link #readObject() readObject} method,
     * so <code>in.{@link #readObject() readObject}()</code> is mostly equivalent to
     * <code>in.readMarshalled({@link Marshaller#SERIALIZATION Marshaller.SERIALIZATION}).{@link Marshalled#getObject() getObject}()</code>.
     *
     * <p>To read an arbitrary marshalled object, this method must specify the same {@code marshaller} that was
     * used in a marshalled object that was written by a corresponding
     * {@link BufferedOutput#writeMarshalled(Marshalled) BufferedOutput.writeMarshalled} method invocation.
     *
     * @param marshaller a strategy that defines how an object is represented in a byte array
     * @return the marshalled object
     * @throws IOException if an I/O error occurs
     */
    public final <T> Marshalled<T> readMarshalled(Marshaller<T> marshaller) throws IOException {
        return readMarshalled(marshaller, SerialClassContext.getDefaultSerialContext(null));
    }

    /**
     * Reads {@link Marshalled} object from a byte stream with a specified marshaller.
     * This method determines the number of marshalled bytes using
     * {@link Marshaller#readMarshalledLength(BufferedInput) Marshaller.readMarshalledLength},
     * reads the corresponding number of bytes, and wraps them into {@link Marshalled} object instance.
     * The result of this method is not {@code null}.
     *
     * <p>When the {@link Marshaller#SERIALIZATION serialization} marshaller is used, then this method
     * reads the same bytes as would have been been read by {@link #readObject() readObject} method,
     * so <code>in.{@link #readObject() readObject}()</code> is mostly equivalent to
     * <code>in.readMarshalled({@link Marshaller#SERIALIZATION Marshaller.SERIALIZATION}).{@link Marshalled#getObject() getObject}()</code>.
     *
     * <p>To read an arbitrary marshalled object, this method must specify the same {@code marshaller} that was
     * used in a marshalled object that was written by a corresponding
     * {@link BufferedOutput#writeMarshalled(Marshalled) BufferedOutput.writeMarshalled} method invocation.
     *
     * @param marshaller a strategy that defines how an object is represented in a byte array
     * @param serialContext a serial class context that will be used to load classes;
     * @return the marshalled object
     * @throws IOException if an I/O error occurs
     */
    public final <T> Marshalled<T> readMarshalled(Marshaller<T> marshaller, SerialClassContext serialContext) throws IOException {
        long length = marshaller.readMarshalledLength(this);
        checkEncapsulatedLength(length, -1, Integer.MAX_VALUE);
        if (serialContext == null)
            serialContext = SerialClassContext.getDefaultSerialContext(null);
        if (length == -1)
            return Marshalled.forBytes(null, marshaller, serialContext);
        if (length == 0)
            return Marshalled.forBytes(EMPTY_BYTE_ARRAY, marshaller, serialContext);
        byte[] bytes = new byte[(int) length];
        readFully(bytes);
        return Marshalled.forBytes(bytes, marshaller, serialContext);
    }

    // ========== Compact API ==========

    /**
     * Reads an <code>int</code> value in a compact format (see {@link IOUtil}).
     * If actual encoded value does not fit into an <code>int</code> data type,
     * then it is truncated to <code>int</code> value (only lower 32 bits are returned);
     * the number is read entirely in this case.
     * Equivalent to {@link IOUtil#readCompactInt} method.
     *
     * @return the <code>int</code> value read
     * @throws IOException if an I/O error occurs
     */
    public final int readCompactInt() throws IOException {
        // The ((n << k) >> k) expression performs two's complement.
        int n = readUnsignedByte();
        if (n < 0x80)
            return (n << 25) >> 25;
        if (n < 0xC0)
            return (((n << 8) | readUnsignedByte()) << 18) >> 18;
        if (n < 0xE0)
            return (((n << 16) | readUnsignedShort()) << 11) >> 11;
        if (n < 0xF0)
            return (((n << 24) | (readUnsignedByte() << 16) | readUnsignedShort()) << 4) >> 4;
        // The encoded number is possibly out of range, some bytes have to be skipped.
        // The skipBytes(...) does the strange thing, thus readUnsignedByte() is used.
        while (((n <<= 1) & 0x10) != 0)
            readUnsignedByte();
        return readInt();
    }

    /**
     * Reads a <code>long</code> value in a compact format (see {@link IOUtil}).
     * Equivalent to {@link IOUtil#readCompactLong} method.
     *
     * @return the <code>long</code> value read
     * @throws IOException if an I/O error occurs
     */
    public final long readCompactLong() throws IOException {
        // The ((n << k) >> k) expression performs two's complement.
        int n = readUnsignedByte();
        if (n < 0x80)
            return (n << 25) >> 25;
        if (n < 0xC0)
            return (((n << 8) | readUnsignedByte()) << 18) >> 18;
        if (n < 0xE0)
            return (((n << 16) | readUnsignedShort()) << 11) >> 11;
        if (n < 0xF0)
            return (((n << 24) | (readUnsignedByte() << 16) | readUnsignedShort()) << 4) >> 4;
        if (n < 0xF8) {
            n = (n << 29) >> 29;
        } else if (n < 0xFC) {
            n = (((n << 8) | readUnsignedByte()) << 22) >> 22;
        } else if (n < 0xFE) {
            n = (((n << 16) | readUnsignedShort()) << 15) >> 15;
        } else if (n < 0xFF) {
            n = (readByte() << 16) | readUnsignedShort();
        } else {
            n = readInt();
        }
        return ((long) n << 32) | (readInt() & 0xFFFFFFFFL);
    }

    /**
     * Reads an array of bytes in a compact encapsulation format.
     * This method defines length as a number of bytes.
     * Equivalent to {@link IOUtil#readByteArray} method.
     *
     * @return the byte array read
     * @throws IOException if an I/O error occurs
     */
    public final byte[] readByteArray() throws IOException {
        long length = readCompactLong();
        checkEncapsulatedLength(length, -1, Integer.MAX_VALUE);
        if (length == -1)
            return null;
        if (length == 0)
            return EMPTY_BYTE_ARRAY;
        byte[] bytes = new byte[(int) length];
        readFully(bytes);
        return bytes;
    }

    // ========== UTF API ==========

    /**
     * Reads Unicode code point in a UTF-8 format.
     * Overlong UTF-8 and CESU-8-encoded surrogates are accepted and read without errors.
     * Equivalent to {@link IOUtil#readUTFChar} method.
     *
     * @return the Unicode code point read
     * @throws UTFDataFormatException if the bytes do not represent a valid UTF-8 encoding of a character
     * @throws IOException if an I/O error occurs
     */
    public final int readUTFChar() throws IOException {
        int c = readByte();
        if (c >= 0)
            return (char) c;
        if ((c & 0xE0) == 0xC0)
            return readUTF2(c);
        if ((c & 0xF0) == 0xE0)
            return readUTF3(c);
        if ((c & 0xF8) == 0xF0)
            return readUTF4(c);
        throw new UTFDataFormatException();
    }

    /**
     * Reads Unicode string in a UTF-8 format with compact encapsulation (see {@link IOUtil}).
     * Overlong UTF-8 and CESU-8-encoded surrogates are accepted and read without errors.
     * This method defines length as a number of bytes.
     * Equivalent to {@link IOUtil#readUTFString} method.
     *
     * @return the Unicode string read
     * @throws UTFDataFormatException if the bytes do not represent a valid UTF-8 encoding of a string
     * @throws IOException if an I/O error occurs
     */
    public final String readUTFString() throws IOException {
        long utfLen = readCompactLong();
        checkEncapsulatedLength(utfLen, -1, Integer.MAX_VALUE * 4L);
        if (utfLen == -1)
            return null;
        if (utfLen == 0)
            return "";
        char[] chars = getCharBuffer((int) Math.min(utfLen, Integer.MAX_VALUE));
        return String.valueOf(chars, 0, readUTFBody(utfLen, chars, 0));
    }

    /**
     * Reads Unicode body in a UTF-8 format.
     * Overlong UTF-8 and CESU-8-encoded surrogates are accepted and read without errors.
     * This method defines length as a number of bytes.
     * Equivalent to {@link IOUtil#readUTFBody} method.
     *
     * @param utfLength the number of bytes to read
     * @param chars the char array in which read characters are stored in UTF-16 format
     * @param offset the start offset into the {@code chars} array where read characters are stored
     * @return the number of read characters stored in the {@code chars} array
     * @throws ArrayIndexOutOfBoundsException if the {@code chars} array is too small to accommodate read characters
     * @throws UTFDataFormatException if the bytes do not represent a valid UTF-8 encoding of a string
     * @throws IOException if an I/O error occurs
     */
    public final int readUTFBody(long utfLength, char[] chars, int offset) throws IOException {
        int index = offset;
        while (utfLength > 0) {
            int c = readByte();
            if (c >= 0) {
                utfLength--;
                chars[index++] = (char) c;
            } else if ((c & 0xE0) == 0xC0) {
                utfLength -= 2;
                chars[index++] = readUTF2(c);
            } else if ((c & 0xF0) == 0xE0) {
                utfLength -= 3;
                chars[index++] = readUTF3(c);
            } else if ((c & 0xF8) == 0xF0) {
                utfLength -= 4;
                index += Character.toChars(readUTF4(c), chars, index);
            } else
                throw new UTFDataFormatException();
        }
        if (utfLength < 0)
            throw new UTFDataFormatException();
        return index - offset;
    }

    // ========== position/seek/mark/reset/rewind API ==========

    /**
     * Returns total position of this input stream.
     * The total position is defined as an offset of the next byte to be read from the beginning of the stream.
     * It is equal to the number of read bytes adjusted according to outcome of repositioning methods
     * like {@link #seek}, {@link #skip}, {@link #rewind}, {@link #reset}, etc.
     */
    public final long totalPosition() {
        return totalPositionBase + position;
    }

    /**
     * Repositions this buffered input to specified total position.
     * If specified total position is smaller than current total position, then {@link #rewind} operation
     * for appropriate distance is performed; the stream has to be properly marked to support this operation.
     * If specified total position is larger than current total position, then {@link #skip} operation
     * for appropriate distance is performed until requested total position is reached; if end of stream
     * happens before this operation can be completed then {@link EOFException} is thrown.
     *
     * @throws IllegalArgumentException if total position is negative
     * @throws IllegalStateException if this stream is not marked or if attempting to rewind past the marked position
     * @throws IOException if an I/O error occurs
     * @throws EOFException if end of stream is reached
     */
    public final void seek(long totalPosition) throws IOException {
        if (totalPosition < 0)
            throw new IllegalArgumentException();
        long n = totalPosition - totalPosition();
        if (n < 0)
            rewind(-n);
        while (n > 0) {
            long skipped = skip(n);
            if (skipped <= 0)
                throw new EOFException();
            n -= skipped;
        }
    }

    /**
     * All BufferedInput implementations support {@link #mark() mark} and {@link #reset() reset},
     * so this method always returns true.
     */
    @Override
    public final boolean markSupported() {
        return true;
    }

    /**
     * Marks the current position in this buffered input. A subsequent call to
     * the {@link #reset() reset} method repositions this input at the last marked
     * position so that subsequent reads re-read the same bytes.
     * A subsequent call to {@link #unmark() unmark} method invalidates mark
     * without changing input position.
     */
    public void mark() {
        markPosition = totalPosition();
    }

    /**
     * Invalidates {@link #mark() mark} position in this buffered input.
     * This method does nothing is this input was not marked.
     */
    public void unmark() {
        markPosition = -1;
    }

    /**
     * Marks the current position in this input stream. A subsequent call to
     * the {@link #reset} method repositions this stream at the last marked
     * position so that subsequent reads re-read the same bytes.
     *
     * <p> The <code>readLimit</code> argument tells this input stream to allow
     * that many bytes to be read before the mark position gets invalidated.
     * Negative value for <code>readLimit</code> may be used in order to invalidate the mark.
     *
     * <p>This implementation calls {@link #mark()} when {@code readLimit >= 0} and
     * {@link #unmark()} otherwise.
     *
     * @deprecated This method is for compatibility with interface of {@link InputStream} class only,
     *             do not use it, because the actual value of {@code readLimit} is irrelevant for
     *             all implementations of {@code BufferedInput}.
     *             Use {@link #mark() mark} and {@link #unmark() unmark} methods instead.
     */
    @Override
    public final void mark(int readLimit) {
        mark((long) readLimit);
    }

    /**
     * Marks the current position in this input stream. A subsequent call to
     * the {@link #reset} method repositions this stream at the last marked
     * position so that subsequent reads re-read the same bytes.
     *
     * <p> The <code>readLimit</code> argument tells this input stream to allow
     * that many bytes to be read before the mark position gets invalidated.
     * Negative value for <code>readLimit</code> may be used in order to invalidate the mark.
     *
     * <p>This implementation calls {@link #mark()} when {@code readLimit >= 0} and
     * {@link #unmark()} otherwise.
     *
     * @deprecated This method is for conceptual compatibility with interface of {@link InputStream} class only,
     *             do not use it, because the actual value of {@code readLimit} is irrelevant for
     *             all implementations of {@code BufferedInput}.
     *             Use {@link #mark() mark} and {@link #unmark() unmark} methods instead.
     */
    public final void mark(long readLimit) {
        if (readLimit >= 0)
            mark();
        else
            unmark();
    }

    /**
     * Repositions this buffered input to the position at the time the
     * {@link #mark() mark} method was last called on this input.
     * If the {@link #mark() mark} method has not been called since
     * the buffered input was created, then an
     * {@link IllegalStateException} is thrown.
     * Otherwise the input is reset to a state such that all the bytes read since the
     * most recent call to {@link #mark() mark} will be resupplied
     * to subsequent callers of the <code>read</code> methods, followed by
     * any bytes that otherwise would have been the next input data as of
     * the time of the call to <code>reset</code>.
     *
     * <p><b>NOTE:</b> This buffered input continues to be marked.
     * Use {@link #unmark()} after reset if mark on this input is no longer needed.
     *
     * @throws IllegalStateException if this stream is not marked
     */
    @Override
    public final void reset() throws IllegalStateException {
        if (markPosition < 0)
            throw new IllegalStateException("mark is invalid");
        rewind(totalPosition() - markPosition);
    }


    /**
     * Rewinds specified number of bytes. The input cannot be rewound past the marked position.
     *
     * @param n the number of bytes to rewind
     * @throws IllegalStateException if this stream is not marked or if attempting to rewind past the marked position
     */
    public abstract void rewind(long n) throws IllegalStateException;

    /**
     * Throws appropriate exception if rewind for specified distance is impossible.
     */
    protected final void checkRewind(long n) {
        if (n < 0)
            throw new IllegalArgumentException("n is negative");
        if (markPosition < 0)
            throw new IllegalStateException("mark is invalid");
        if (n > totalPosition() - markPosition)
            throw new IllegalStateException("cannot rewind past mark");
    }

    // ========== Additional utility methods ==========

    /**
     * Reads data from this buffered input into specified output stream.
     * The number of bytes actually read is returned as an integer.
     *
     * @param out the destination output stream to write to
     * @param length maximum number of bytes to read
     * @return total number of bytes read to the output stream
     * @throws IOException if an I/O error occurs
     */
    public long readToOutputStream(OutputStream out, long length) throws IOException {
        long result = 0;
        while (result < length) {
            if (position >= limit && readData() <= 0)
                return result;
            int n = (int) Math.min(length - result, limit - position);
            out.write(buffer, position, n);
            position += n;
            result += n;
        }
        return result;
    }

    /**
     * Reads data from this buffered input into specified data output.
     * The number of bytes actually read is returned as an integer.
     *
     * @param out the destination data output to write to
     * @param length maximum number of bytes to read
     * @return total number of bytes read to the data output
     * @throws IOException if an I/O error occurs
     */
    public long readToDataOutput(DataOutput out, long length) throws IOException {
        long result = 0;
        while (result < length) {
            if (position >= limit && readData() <= 0)
                return result;
            int n = (int) Math.min(length - result, limit - position);
            out.write(buffer, position, n);
            position += n;
            result += n;
        }
        return result;
    }

    /**
     * Reads data from this buffered input into specified byte buffer.
     *
     * @param buffer the destination byte buffer to write to
     */
    public void readToByteBuffer(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            if (position >= limit && readData() <= 0)
                return;
            int n = Math.min(buffer.remaining(), limit - position);
            buffer.put(this.buffer, position, n);
            position += n;
        }
    }

    /** @deprecated internal API */
    @Deprecated
    static interface CaptureBytes {
        public void captureBytes(byte[] buffer, int offset, int length);
    }

    /**
     * Reads data from this buffered input into specified byte output.
     * This method is designed to capture buffered bytes for direct processing.
     * It is currently limited to single chunk of bytes held by this buffered input.
     *
     * @deprecated internal API for ObjectDeserializer only
     */
    @Deprecated
    boolean readToCaptureBytes(CaptureBytes out, int length) {
        if (length <= limit - position) {
            out.captureBytes(buffer, position, length);
            position += length;
            return true;
        }
        return false;
    }

    // ========== Implementation Details ==========

    private static final EOFException EOF_EXCEPTION = new EOFException("End of buffer."); // Reused to avoid garbage; has truncated stack trace.
    static {
        if (EOF_EXCEPTION.getStackTrace().length > 1)
            EOF_EXCEPTION.setStackTrace(new StackTraceElement[] {EOF_EXCEPTION.getStackTrace()[0]});
    }

    // ========== Performance Note ==========
    // The inlining of methods by HotSpot compiler is limited by total method length.
    // For higher performance we shall extract rarely used code into separate methods.
    // Although it also allows code reuse, the code reuse is NOT a goal by itself.

    private char[] charBuffer; // Used for reading strings; lazy initialized.

    private char[] getCharBuffer(int capacity) {
        if (capacity > 256)
            return new char[capacity];
        return charBuffer != null ? charBuffer : (charBuffer = new char[256]);
    }

    private char readUTF2(int first) throws IOException {
        int second = readByte();
        if ((second & 0xC0) != 0x80)
            throw new UTFDataFormatException();
        return (char) (((first & 0x1F) << 6) | (second & 0x3F));
    }

    private char readUTF3(int first) throws IOException {
        int tail = readShort();
        if ((tail & 0xC0C0) != 0x8080)
            throw new UTFDataFormatException();
        return (char) (((first & 0x0F) << 12) | ((tail & 0x3F00) >> 2) | (tail & 0x3F));
    }

    private int readUTF4(int first) throws IOException {
        int second = readByte();
        int tail = readShort();
        if ((second & 0xC0) != 0x80 || (tail & 0xC0C0) != 0x8080)
            throw new UTFDataFormatException();
        int codePoint = ((first & 0x07) << 18) | ((second & 0x3F) << 12) | ((tail & 0x3F00) >> 2) | (tail & 0x3F);
        if (codePoint > 0x10FFFF)
            throw new UTFDataFormatException();
        return codePoint;
    }
}
