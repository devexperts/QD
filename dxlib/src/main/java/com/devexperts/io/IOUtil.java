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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.UTFDataFormatException;
import java.util.zip.DataFormatException;

/**
 * Utility class that provides algorithms for data serialization and deserialization.
 * It defines several compact data formats and provides clean and convenient API to use them.
 *
 * <h3><a name="compact-int">CompactInt</a></h3>
 *
 * The <b>CompactInt</b> is a serialization format for integer numbers. It uses encoding scheme
 * with variable-length two's complement big-endian format capable to encode 64-bits signed numbers.
 * <p>
 * The following table defines used serial format (the first byte is given in bits
 * with 'x' representing payload bit; the remaining bytes are given in bit count):
 * <pre>
 * 0xxxxxxx     - for -64 &lt;= N &lt; 64
 * 10xxxxxx  8x - for -8192 &lt;= N &lt; 8192
 * 110xxxxx 16x - for -1048576 &lt;= N &lt; 1048576
 * 1110xxxx 24x - for -134217728 &lt;= N &lt; 134217728
 * 11110xxx 32x - for -17179869184 &lt;= N &lt; 17179869184 (includes whole range of signed int)
 * 111110xx 40x - for -2199023255552 &lt;= N &lt; 2199023255552
 * 1111110x 48x - for -281474976710656 &lt;= N &lt; 281474976710656
 * 11111110 56x - for -36028797018963968 &lt;= N &lt; 36028797018963968
 * 11111111 64x - for -9223372036854775808 &lt;= N &lt; 9223372036854775808 (the range of signed long)
 * </pre>
 *
 * <h3><a name="compact-encapsulation">Compact Encapsulation</a></h3>
 *
 * The <b>Compact Encapsulation</b> is a method of wrapping serial data for representation on another layer.
 * There is little dedicated API for compact encapsulation exist - it is a technique used implicitly by other API.
 * <p>
 * This method first writes length of encapsulated data in a compact format and then writes data itself.
 * By convention values of length lesser than <code>-1</code> are illegal (reserved for future use);
 * length value of <code>-1</code> indicates special case of <code>null</code> data (if applicable);
 * length value of <code>0</code> indicates empty data (as applicable; e.g. no data elements); and
 * positive values of length indicate either number of data elements or number of bytes they occupy.
 * <p>
 * <b>Note:</b> the length of encapsulated data is formally declared as <code>long</code> value;
 * readers shall read full 64-bit length value and report overflow if they cannot handle large values.
 *
 * <h3><a name="utf">UTF</a></h3>
 *
 * The <b>UTF</b> API works with Unicode character data in several formats:
 * Unicode code point (<code>int</code> value),
 * UTF-16 encoding ({@link String} class) and
 * UTF-8 encoding (serial format).
 * The UTF API uses compact encapsulation with length defined as number of UTF-8 encoded bytes.
 * <p>
 * <b>Note:</b> the UTF API uses official UTF-8 encoding format while Java serialization uses
 * <a href="DataInput.html#modified-utf-8">modified UTF-8</a> format. This results with several
 * APIs that seemingly work with same UTF-8 encoding, yet they differ in encoding and encapsulation.
 *
 * <h3><a name="object">Object</a></h3>
 *
 * The <b>Object</b> API helps to serialize and deserialize objects.
 * The serial form of individual object is defined as a byte array those content is a result of independent
 * serialization of that object by new instance of {@link ObjectOutputStream}. When this byte array is written
 * to the output it uses compact encapsulation.
 *
 * <h3><a name="compression">Compression</a></h3>
 *
 * The <b>Compression</b> API allows serial data to be compressed in order to reduce space it occupies
 * and save resources needed to store or transmit the data.
 * <p>
 * The compression API uses <i>Deflate</i> algorithm (see <a href="http://tools.ietf.org/html/rfc1951">RFC 1951</a>) and
 * wraps compressed data blocks using <i>ZLIB</i> format (see <a href="http://tools.ietf.org/html/rfc1950">RFC 1950</a>).
 * It can be used arbitrarily and it is also intended to be used transparently by serialization API.
 */
public class IOUtil {

    private IOUtil() {} // Prevents unintentional instantiation.

    /**
     * Throws {@link IndexOutOfBoundsException} if parameters are out of range.
     */
    public static void checkRange(byte[] b, int off, int len) {
        if ((off | len | (off + len) | (b.length - (off + len))) < 0)
            throw new IndexOutOfBoundsException();
    }

    // ---------- CompactInt API ----------

    /**
     * Returns number of bytes that are needed to write specified number in a compact format.
     *
     * @param n the number those compact length is returned
     * @return number of bytes that are needed to write specified number in a compact format
     */
    public static int getCompactLength(long n) {
        if (n >= 0) {
            if (n < 0x40) {
                return 1;
            } else if (n < 0x2000) {
                return 2;
            } else if (n < 0x100000) {
                return 3;
            } else if (n < 0x08000000) {
                return 4;
            } else if (n < 0x0400000000L) {
                return 5;
            } else if (n < 0x020000000000L) {
                return 6;
            } else if (n < 0x01000000000000L) {
                return 7;
            } else if (n < 0x80000000000000L) {
                return 8;
            } else {
                return 9;
            }
        } else {
            if (n >= -0x40) {
                return 1;
            } else if (n >= -0x2000) {
                return 2;
            } else if (n >= -0x100000) {
                return 3;
            } else if (n >= -0x08000000) {
                return 4;
            } else if (n >= -0x0400000000L) {
                return 5;
            } else if (n >= -0x020000000000L) {
                return 6;
            } else if (n >= -0x01000000000000L) {
                return 7;
            } else if (n >= -0x80000000000000L) {
                return 8;
            } else {
                return 9;
            }
        }
    }

    /**
     * Writes an <code>int</code> value to the data output in a compact format.
     *
     * @param out the destination to write to
     * @param v the <code>int</code> value to be written
     * @throws IOException if an I/O error occurs
     */
    public static void writeCompactInt(DataOutput out, int v) throws IOException {
        if (v >= 0) {
            if (v < 0x40) {
                out.writeByte(v);
            } else if (v < 0x2000) {
                out.writeShort(0x8000 | v);
            } else if (v < 0x100000) {
                out.writeByte(0xC0 | (v >> 16));
                out.writeShort(v);
            } else if (v < 0x08000000) {
                out.writeInt(0xE0000000 | v);
            } else {
                out.writeByte(0xF0);
                out.writeInt(v);
            }
        } else {
            if (v >= -0x40) {
                out.writeByte(0x7F & v);
            } else if (v >= -0x2000) {
                out.writeShort(0xBFFF & v);
            } else if (v >= -0x100000) {
                out.writeByte(0xDF & (v >> 16));
                out.writeShort(v);
            } else if (v >= -0x08000000) {
                out.writeInt(0xEFFFFFFF & v);
            } else {
                out.writeByte(0xF7);
                out.writeInt(v);
            }
        }
    }

    /**
     * Writes a <code>long</code> value to the data output in a compact format.
     *
     * @param out the destination to write to
     * @param v the <code>long</code> value to be written
     * @throws IOException if an I/O error occurs
     */
    public static void writeCompactLong(DataOutput out, long v) throws IOException {
        if (v == (long) (int) v) {
            writeCompactInt(out, (int) v);
            return;
        }
        int hi = (int) (v >>> 32);
        if (hi >= 0) {
            if (hi < 0x04) {
                out.writeByte(0xF0 | hi);
            } else if (hi < 0x0200) {
                out.writeShort(0xF800 | hi);
            } else if (hi < 0x010000) {
                out.writeByte(0xFC);
                out.writeShort(hi);
            } else if (hi < 0x800000) {
                out.writeInt(0xFE000000 | hi);
            } else {
                out.writeByte(0xFF);
                out.writeInt(hi);
            }
        } else {
            if (hi >= -0x04) {
                out.writeByte(0xF7 & hi);
            } else if (hi >= -0x0200) {
                out.writeShort(0xFBFF & hi);
            } else if (hi >= -0x010000) {
                out.writeByte(0xFD);
                out.writeShort(hi);
            } else if (hi >= -0x800000) {
                out.writeInt(0xFEFFFFFF & hi);
            } else {
                out.writeByte(0xFF);
                out.writeInt(hi);
            }
        }
        out.writeInt((int) v);
    }

    /**
     * Reads an <code>int</code> value from the data input in a compact format.
     * If actual encoded value does not fit into an <code>int</code> data type,
     * then it is truncated to <code>int</code> value (only lower 32 bits are returned);
     * the number is read entirely in this case.
     *
     * @param in the source to read from
     * @return the <code>int</code> value read
     * @throws IOException if an I/O error occurs
     */
    public static int readCompactInt(DataInput in) throws IOException {
        // The ((n << k) >> k) expression performs two's complement.
        int n = in.readUnsignedByte();
        if (n < 0x80)
            return (n << 25) >> 25;
        if (n < 0xC0)
            return (((n << 8) + in.readUnsignedByte()) << 18) >> 18;
        if (n < 0xE0)
            return (((n << 16) + in.readUnsignedShort()) << 11) >> 11;
        if (n < 0xF0)
            return (((n << 24) + (in.readUnsignedByte() << 16) + in.readUnsignedShort()) << 4) >> 4;
        // The encoded number is possibly out of range, some bytes have to be skipped.
        // The skipBytes(...) does the strange thing, thus readUnsignedByte() is used.
        while (((n <<= 1) & 0x10) != 0)
            in.readUnsignedByte();
        return in.readInt();
    }

    /**
     * Reads a <code>long</code> value from the data input in a compact format.
     *
     * @param in the source to read from
     * @return the <code>long</code> value read
     * @throws IOException if an I/O error occurs
     */
    public static long readCompactLong(DataInput in) throws IOException {
        // The ((n << k) >> k) expression performs two's complement.
        int n = in.readUnsignedByte();
        if (n < 0x80)
            return (n << 25) >> 25;
        if (n < 0xC0)
            return (((n << 8) + in.readUnsignedByte()) << 18) >> 18;
        if (n < 0xE0)
            return (((n << 16) + in.readUnsignedShort()) << 11) >> 11;
        if (n < 0xF0)
            return (((n << 24) + (in.readUnsignedByte() << 16) + in.readUnsignedShort()) << 4) >> 4;
        if (n < 0xF8) {
            n = (n << 29) >> 29;
        } else if (n < 0xFC) {
            n = (((n << 8) + in.readUnsignedByte()) << 22) >> 22;
        } else if (n < 0xFE) {
            n = (((n << 16) + in.readUnsignedShort()) << 15) >> 15;
        } else if (n < 0xFF) {
            n = (in.readByte() << 16) + in.readUnsignedShort();
        } else {
            n = in.readInt();
        }
        return ((long) n << 32) + (in.readInt() & 0xFFFFFFFFL);
    }

    // ---------- Compact Encapsulation API ----------

    /**
     * Writes an array of bytes to the data output in a compact encapsulation format.
     * This method defines length as a number of bytes.
     *
     * @param out the destination to write to
     * @param bytes the byte array to be written
     * @throws IOException if an I/O error occurs
     */
    public static void writeByteArray(DataOutput out, byte[] bytes) throws IOException {
        if (bytes == null) {
            writeCompactInt(out, -1);
            return;
        }
        writeCompactInt(out, bytes.length);
        out.write(bytes);
    }

    /**
     * Reads an array of bytes from the data input in a compact encapsulation format.
     * This method defines length as a number of bytes.
     *
     * @param in the source to read from
     * @return the byte array read
     * @throws IOException if an I/O error occurs
     */
    public static byte[] readByteArray(DataInput in) throws IOException {
        long length = readCompactLong(in);
        checkEncapsulatedLength(in, length);
        if (length == -1)
            return null;
        if (length == 0)
            return BufferedInput.EMPTY_BYTE_ARRAY;
        byte[] bytes = new byte[(int) length];
        in.readFully(bytes);
        return bytes;
    }

    /**
     * Writes an array of characters to the data output in a CESU-8 format with compact encapsulation.
     * This method defines length as a number of characters.
     *
     * @param out the destination to write to
     * @param chars the char array to be written
     * @throws IOException if an I/O error occurs
     */
    public static void writeCharArray(DataOutput out, char[] chars) throws IOException {
        int length = chars == null ? -1 : chars.length;
        writeCompactInt(out, length);
        for (int i = 0; i < length; i++)
            writeUTFChar(out, chars[i]);
    }

    /**
     * Writes an array of characters to the data output in a CESU-8 format with compact encapsulation.
     * This method defines length as a number of characters.
     * This is a bridge method that accepts {@link String} and treats it as char array.
     *
     * @param out the destination to write to
     * @param str the string to be written
     * @throws IOException if an I/O error occurs
     */
    public static void writeCharArray(DataOutput out, String str) throws IOException {
        int length = str == null ? -1 : str.length();
        writeCompactInt(out, length);
        for (int i = 0; i < length; i++)
            writeUTFChar(out, str.charAt(i));
    }

    /**
     * Reads an array of characters from the data input in a CESU-8 format with compact encapsulation.
     * Overlong UTF-8 and CESU-8-encoded surrogates are accepted and read without errors.
     * This method defines length as a number of characters.
     *
     * @param in the source to read from
     * @return the char array read
     * @throws UTFDataFormatException if the bytes do not represent a valid CESU-8 encoding of a character
     *         or if resulting code point is beyond Basic Multilingual Plane (BMP)
     * @throws IOException if an I/O error occurs
     */
    public static char[] readCharArray(DataInput in) throws IOException {
        long length = readCompactLong(in);
        checkEncapsulatedLength(in, length);
        if (length == -1)
            return null;
        char[] chars = new char[(int) length];
        for (int i = 0; i < length; i++) {
            int codePoint = readUTFChar(in);
            if (codePoint > 0xFFFF)
                throw new UTFDataFormatException("Code point is beyond BMP.");
            chars[i] = (char) codePoint;
        }
        return chars;
    }

    /**
     * Reads an array of characters from the data input in a CESU-8 format with compact encapsulation.
     * Overlong UTF-8 and CESU-8-encoded surrogates are accepted and read without errors.
     * This method defines length as a number of characters.
     *
     * @param in the source to read from
     * @return the char array read
     * @throws UTFDataFormatException if the bytes do not represent a valid CESU-8 encoding of a character
     *         or if resulting code point is beyond Basic Multilingual Plane (BMP)
     * @throws IOException if an I/O error occurs
     *
     * @deprecated
     */
    public static String readCharArrayString(DataInput in) throws IOException {
        char[] chars = readCharArray(in);
        return chars == null ? null : new String(chars);
    }

    // ---------- UTF API ----------

    /**
     * Writes a Unicode code point to the data output in a UTF-8 format.
     * The surrogate code points are accepted and written in a CESU-8 format.
     *
     * @param out the destination to write to
     * @param codePoint the code point to be written
     * @throws UTFDataFormatException if codePoint is not a valid Unicode character
     * @throws IOException if an I/O error occurs
     */
    public static void writeUTFChar(DataOutput out, int codePoint) throws IOException {
        if (codePoint < 0)
            throw new UTFDataFormatException();
        if (codePoint <= 0x007F)
            out.writeByte(codePoint);
        else if (codePoint <= 0x07FF)
            CompactSerializer.writeUTF2Unchecked(out, codePoint);
        else if (codePoint <= 0xFFFF)
            CompactSerializer.writeUTF3Unchecked(out, codePoint);
        else if (codePoint <= 0x10FFFF)
            CompactSerializer.writeUTF4Unchecked(out, codePoint);
        else
            throw new UTFDataFormatException();
    }

    /**
     * Reads Unicode code point from the data input in a UTF-8 format.
     * Overlong UTF-8 and CESU-8-encoded surrogates are accepted and read without errors.
     *
     * @param in the source to read from
     * @return the Unicode code point read
     * @throws UTFDataFormatException if the bytes do not represent a valid UTF-8 encoding of a character
     * @throws IOException if an I/O error occurs
     */
    public static int readUTFChar(DataInput in) throws IOException {
        int c = in.readByte();
        if (c >= 0)
            return (char) c;
        if ((c & 0xE0) == 0xC0)
            return CompactSerializer.readUTF2(in, c);
        if ((c & 0xF0) == 0xE0)
            return CompactSerializer.readUTF3(in, c);
        if ((c & 0xF8) == 0xF0)
            return CompactSerializer.readUTF4(in, c);
        throw new UTFDataFormatException();
    }

    /**
     * Writes a string to the data output in a UTF-8 format with compact encapsulation.
     * Unpaired surrogate code points are accepted and written in a CESU-8 format.
     * This method defines length as a number of bytes.
     *
     * @param out the destination to write to
     * @param str the string to be written
     * @throws UTFDataFormatException if str is too long
     * @throws IOException if an I/O error occurs
     */
    public static void writeUTFString(DataOutput out, String str) throws IOException {
        if (str == null) {
            writeCompactInt(out, -1);
            return;
        }
        int strLen = str.length();
        long utfLen = 0;
        for (int i = 0; i < strLen;) {
            char c = str.charAt(i++);
            if (c <= 0x007F)
                utfLen++;
            else if (c <= 0x07FF)
                utfLen += 2;
            else if (Character.isHighSurrogate(c) && i < strLen && Character.isLowSurrogate(str.charAt(i))) {
                i++;
                utfLen += 4;
            } else
                utfLen += 3;
        }

        writeCompactLong(out, utfLen);
        for (int i = 0; i < strLen;) {
            char c = str.charAt(i++);
            if (c <= 0x007F)
                out.writeByte(c);
            else if (c <= 0x07FF)
                CompactSerializer.writeUTF2Unchecked(out, c);
            else if (Character.isHighSurrogate(c) && i < strLen && Character.isLowSurrogate(str.charAt(i)))
                CompactSerializer.writeUTF4Unchecked(out, Character.toCodePoint(c, str.charAt(i++)));
            else
                CompactSerializer.writeUTF3Unchecked(out, c);
        }
    }

    /**
     * Reads Unicode string from the data input in a UTF-8 format with compact encapsulation.
     * Overlong UTF-8 and CESU-8-encoded surrogates are accepted and read without errors.
     * This method defines length as a number of bytes.
     *
     * @param in the source to read from
     * @return the Unicode string read
     * @throws UTFDataFormatException if the bytes do not represent a valid UTF-8 encoding of a string
     * @throws IOException if an I/O error occurs
     */
    public static String readUTFString(DataInput in) throws IOException {
        if (in instanceof BufferedInput)
            return ((BufferedInput) in).readUTFString(); // Fewer garbage due to char buffer reuse.
        long utfLen = readCompactLong(in);
        if (utfLen < -1 || utfLen > Integer.MAX_VALUE * 4L)
            throw new IOException("Illegal length: " + utfLen);
        if (utfLen == -1)
            return null;
        if (utfLen == 0)
            return "";
        char[] chars = new char[(int) Math.min(utfLen, Integer.MAX_VALUE)];
        return String.valueOf(chars, 0, CompactSerializer.readUTFBody(in, utfLen, chars, 0));
    }

    /**
     * Reads Unicode body from the data input in a UTF-8 format.
     * Overlong UTF-8 and CESU-8-encoded surrogates are accepted and read without errors.
     * This method defines length as a number of bytes.
     *
     * @param in the source to read from
     * @param utfLength the number of bytes to read
     * @param chars the char array in which read characters are stored in UTF-16 format
     * @param offset the start offset into the {@code chars} array where read characters are stored
     * @return the number of read characters stored in the {@code chars} array
     * @throws ArrayIndexOutOfBoundsException if the {@code chars} array is too small to accommodate read characters
     * @throws UTFDataFormatException if the bytes do not represent a valid UTF-8 encoding of a string
     * @throws IOException if an I/O error occurs
     */
    public static int readUTFBody(DataInput in, long utfLength, char[] chars, int offset) throws IOException {
        return CompactSerializer.readUTFBody(in, utfLength, chars, offset);
    }

    // ---------- Object API ----------

    /**
     * Serializes an object to an array of bytes with Java Serialization.
     * This method understands non-serializable {@link Marshalled} objects with
     * {@link Marshaller#SERIALIZATION serialization}  marshaller as a special case and
     * returns the result of its {@link Marshalled#getBytes} method.
     *
     * @param object the object to be serialized
     * @return the byte array with serialized object
     * @throws IOException if object cannot be serialized
     */
    public static byte[] objectToBytes(Object object) throws IOException {
        return ObjectSerializer.toBytes(object);
    }

    /**
     * Deserializes an array of bytes into object with Java Serialization.
     * This methods loads classes using a context class loader
     * (see {@link Thread#getContextClassLoader() Thread.getContextClassLoader}) or
     * using the same classloader that loaded classes for this {@code com.devexperts.io} package when
     * context class loader is not defined.
     * This is a shortcut for
     * <code>{@link #bytesToObject(byte[], SerialClassContext) bytesToObject(bytes, SerialClassContext.getDefaultSerialContext(null))}</code>.
     *
     * @param bytes the byte array to be deserialized
     * @return the deserialized object
     * @throws IOException if object cannot be deserialized of bytes into object with Java Serialization.
     */
    public static Object bytesToObject(byte[] bytes) throws IOException {
        return ObjectDeserializer.toObject(bytes, SerialClassContext.getDefaultSerialContext(null));
    }

    /**
     * Deserializes an array of bytes into object with Java Serialization.
     * This is a shortcut for
     * <code>{@link #bytesToObject(byte[], SerialClassContext) bytesToObject(bytes, SerialClassContext.getDefaultSerialContext(cl))}</code>.
     *
     * @param bytes the byte array to be deserialized
     * @param cl the ClassLoader that will be used to load classes;
     *           <code>null</code> for {@link Thread#getContextClassLoader() context} class loader.
     * @return the deserialized object
     * @throws IOException if object cannot be deserialized
     */
    public static Object bytesToObject(byte[] bytes, ClassLoader cl) throws IOException {
        return ObjectDeserializer.toObject(bytes, SerialClassContext.getDefaultSerialContext(cl));
    }

    /**
     * Deserializes an array of bytes into object with Java Serialization in a given {@link SerialClassContext}.
     *
     * @param bytes the byte array to be deserialized
     * @param serialContext the serial class context
     * @return the deserialized object
     * @throws IOException if object cannot be deserialized
     */
    public static Object bytesToObject(byte[] bytes, SerialClassContext serialContext) throws IOException {
        if (serialContext == null)
            throw new NullPointerException();
        return ObjectDeserializer.toObject(bytes, serialContext);
    }

    /**
     * Writes an object to the data output as a Java-serialized byte array with compact encapsulation.
     * This method understands non-serializable {@link Marshalled} objects with
     * {@link Marshaller#SERIALIZATION serilization} marshaller as a special case and
     * writes the result of its {@link Marshalled#getBytes} method with
     * {@link #writeByteArray(DataOutput, byte[]) writeByteArray}.
     *
     * @param out the destination to write to
     * @param object the object to be written
     * @throws IOException if an I/O error occurs or if object cannot be serialized
     */
    public static void writeObject(DataOutput out, Object object) throws IOException {
        ObjectSerializer.writeCompact(out, object);
    }

    /**
     * Reads an object from the data input as a Java-serialized byte array with compact encapsulation.
     * This method loads classes using a context class loader
     * (see {@link Thread#getContextClassLoader() Thread.getContextClassLoader}) or
     * using the same classloader that loaded classes for this {@code com.devexperts.io} package when
     * context class loader is not defined.
     * This is a shortcut for
     * <code>{@link #readObject(DataInput, SerialClassContext) readObject (in, SerialClassContext.getDefaultSerialContext(null)}</code>.
     *
     * @param in the source to read from
     * @return the object read
     * @throws IOException if an I/O error occurs or if object cannot be deserialized
     */
    public static Object readObject(DataInput in) throws IOException {
        return ObjectDeserializer.readCompact(in, SerialClassContext.getDefaultSerialContext(null));
    }

    /**
     * Reads an object from the data input as a Java-serialized byte array with compact encapsulation.
     * This is a shortcut for
     * {@link #readObject(DataInput, SerialClassContext) readObject(in, SerialClassContext.getDefaultSerialContext(null))}.
     *
     * @param in the source to read from
     * @param cl the ClassLoader that will be used to load classes;
     *           <code>null</code> for {@link Thread#getContextClassLoader() context} class loader.
     * @return the object read
     * @throws IOException if an I/O error occurs or if object cannot be deserialized
     */
    public static Object readObject(DataInput in, ClassLoader cl) throws IOException {
        return ObjectDeserializer.readCompact(in, SerialClassContext.getDefaultSerialContext(cl));
    }

    /**
     * Reads an object from the data input as a Java-serialized byte array with compact encapsulation
     * in a given {@link SerialClassContext}.
     *
     * @param in the source to read from
     * @param serialContext the serial class context
     * @return the object read
     * @throws IOException if an I/O error occurs or if object cannot be deserialized
     */
    public static Object readObject(DataInput in, SerialClassContext serialContext) throws IOException {
        if (serialContext == null)
            throw new NullPointerException();
        return ObjectDeserializer.readCompact(in, serialContext);
    }

    /**
     * Validate <a href="IOUtil.html#compact-encapsulation">encapsulated content length</a> according to
     * theoretical bounds and stream limits.
     * <p>
     * Data retrieval methods getting variable amount of data can invoke this method to check an upper bound
     * of available data for validation.
     *
     * @param length encapsulated length value to be checked
     * @throws IOException if {@code length < -1 || length > Integer.MAX_VALUE} or {@code length} bytes definitely
     *     cannot be retrieved
     * @see BufferedInput#checkEncapsulatedLength
     */
    private static void checkEncapsulatedLength(DataInput in, long length) throws IOException {
        if (in instanceof BufferedInput) {
            ((BufferedInput) in).checkEncapsulatedLength(length, -1, Integer.MAX_VALUE);
            return;
        }
        if (length < -1 || length > Integer.MAX_VALUE)
            throw new IOException("Illegal length: " + length);
    }

    // ---------- Compression API ----------

    private static boolean compressionEnabled = false;
    static {
        try {
            compressionEnabled = "true".equalsIgnoreCase(System.getProperty("com.devexperts.io.compressionEnabled", String.valueOf(compressionEnabled)));
        } catch (Throwable ignored) {}
    }

    /**
     * Compresses an array of bytes using Deflate algorithm with specified compression level.
     *
     * @param bytes the byte array to be compressed
     * @param level the compression level from <code>0</code> to <code>9</code> inclusive; <code>-1</code> for default
     * @return the compressed byte array
     */
    public static byte[] deflate(byte[] bytes, int level) {
        ByteArrayOutput out = new ByteArrayOutput(Math.max(bytes.length / 4, 8192));
        Compression.deflate(bytes, 0, bytes.length, level, out);
        return out.toByteArray();
    }

    /**
     * Decompresses an array of bytes using Inflate algorithm (reverse of Deflate algorithm).
     *
     * @param bytes the byte array to be decompressed
     * @return the decompressed byte array
     * @throws DataFormatException if data format error has occurred
     */
    public static byte[] inflate(byte[] bytes) throws DataFormatException {
        ByteArrayOutput out = new ByteArrayOutput(Math.max(bytes.length * 2, 8192));
        Compression.inflate(bytes, 0, bytes.length, out);
        return out.toByteArray();
    }

    /**
     * Returns value of compression strategy.
     *
     * @return <code>true</code> if compression is enabled, <code>false</code> otherwise
     */
    public static boolean isCompressionEnabled() {
        return compressionEnabled;
    }

    /**
     * Sets new value for compression strategy.
     *
     * @param compressionEnabled the value of compression flag
     */
    public static void setCompressionEnabled(boolean compressionEnabled) {
        IOUtil.compressionEnabled = compressionEnabled;
    }

    /**
     * Compresses an array of bytes using Deflate algorithm as appropriate.
     * Unlike {@link #deflate} method this method determines if compression is necessary or not based on
     * compression strategy (see {@link #isCompressionEnabled}) and heuristics related to specified byte array.
     * If decided negative it returns original byte array, otherwise it uses fastest compression level.
     * <p>
     * This method is intended for transparent compression of serialized data and similar cases.
     *
     * @param bytes the byte array to be compressed as appropriate
     * @return original byte array or compressed byte array depending on decision
     */
    public static byte[] compress(byte[] bytes) {
        if (bytes == null || !Compression.shallCompress(bytes, 0, bytes.length))
            return bytes;
        ByteArrayOutput out = new ByteArrayOutput(Math.max(bytes.length / 4, 8192));
        Compression.deflate(bytes, 0, bytes.length, 1, out);
        // Use compressed block only if compression ratio is better than 94% - otherwise use original.
        return out.getPosition() < bytes.length - (bytes.length >> 4) ? out.toByteArray() : bytes;
    }

    /**
     * Decompresses an array of bytes using Inflate algorithm repeatedly as appropriate.
     * <p>
     * This method is intended for transparent decompression of serialized data and similar cases.
     *
     * @param bytes the byte array to be decompressed as appropriate
     * @return the decompressed byte array
     */
    public static byte[] decompress(byte[] bytes) {
        try {
            while (bytes != null && Compression.isCompressed(bytes, 0, bytes.length))
                bytes = inflate(bytes);
        } catch (DataFormatException e) {
            // This exception is treated as indication that bytes were not really compressed.
        }
        return bytes;
    }
}
