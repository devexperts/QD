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
package com.devexperts.qd.kit;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.io.IOUtil;
import com.devexperts.qd.SerialFieldType;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * The <code>ByteArrayField</code> represents a linear byte array field
 * with plain serialized form. It uses <code>CompactInt</code> to encode
 * byte array length first, then serializes bytes themselves. The value -1
 * for length is used as a marker to distinguish 'null' array from empty one.
 * Default representation of the value is <code>byte[]</code> as returned by {@link #readObj},
 * but <code>String</code>, <code>char[]</code> and arbitrary serializable objects are also
 * supported by {@link #writeObj} and {@link #toString(Object)}.
 *
 * <p>Note: extension of this class is not supported because of the high-performance architecture for
 *    binary protocol reading/writing.
 */
public final class ByteArrayField extends AbstractDataObjField {
    public ByteArrayField(int index, String name) {
        super(index, name, SerialFieldType.BYTE_ARRAY);
    }

    /**
     * This method is a hook to convert byte arrays into objects.
     * This implementation returns bytes.
     * This method is invoked from {@link #readObj(java.io.DataInput)}.
     *
     * <p>This method can be overridden to provide custom conversion of byte arrays to objects.
     * If you override this method, then you also typically need to override {@link #toByteArray(Object)}
     * and consider overriding {@link #toString(Object)}.
     * For example, if you need to convert byte arrays to {@code MyObject} instances, write:
     * <pre>
     * public Object fromByteArray(byte[] bytes) {
     *     return MyObject.forByteArray(bytes);
     * }
     * </pre>
     * It is recommended that all such MyObject classes lazily reconstruct themselves from byte array to avoid
     * potentially costly deserialization in multiplexor nodes.
     *
     * @param bytes Byte array to convert to object.
     * @return Resulting object.
     */
    public Object fromByteArray(byte[] bytes) {
        return bytes;
    }

    /**
     * This method is a hook to provide custom conversion of objects to byte arrays for serialization.
     * This implementation works depending on the value class:
     * <ul>
     * <li>{@code byte[]} is returned as is.
     * <li>{@code String} and {@code char[]} are converted to UTF8 bytes.
     * <li>For other other objects <code>null</code> is returned. In this case callee if this method
     * uses {@link IOUtil#objectToBytes(Object)} or {@link IOUtil#writeObject(DataOutput, Object)}.
     * </ul>
     * This method is invoked from {@link #equals(Object, Object)}, {@link #toString(Object)}, and
     * {@link #writeObj(DataOutput, Object)}.
     *
     * <p>This method can be overridden to provide custom conversion of objects to byte arrays.
     * If you override this method, then you also typically need to override {@link #fromByteArray(byte[])}
     * and consider overriding {@link #toString(Object)}.
     * For example, if you need to convert {@code MyObject} instances to byte arrays, write:
     * <pre>
     * public byte[] toByteArray(Object value) {
     *     if (value instanceof MyObject)
     *         return ((MyObject) value).toByteArray();
     *     else
     *         super.toByteArray(value);
     * }
     * </pre>
     * It is recommended that all such MyObject classes cache their produced byte arrays to avoid
     * potentially costly serialization in multiplexor nodes.
     *
     * @param value The object to convert to byte array.
     * @return array of bytes or {@code null} if default conversion via {@link IOUtil#objectToBytes} or
     * {@link IOUtil#writeObject(DataOutput, Object)} shall be used.
     */
    public byte[] toByteArray(Object value) {
        if (value instanceof byte[])
            return (byte[]) value;
        if (value instanceof String)
            return ((String) value).getBytes(StandardCharsets.UTF_8);
        else if (value instanceof char[])
            return new String((char[]) value).getBytes(StandardCharsets.UTF_8);
        else
            return null;
    }

    private byte[] toByteArrayAlways(Object value) {
        try {
            byte[] bytes = toByteArray(value);
            return bytes == null ? IOUtil.objectToBytes(value) : bytes;
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot convert object to bytes", e);
        }
    }

    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /**
     * Returns string representation of the specified field value.
     * This method is used for debugging purposes.
     * This implementation coverts object to byte array via {@link #toByteArray(Object)},
     * if that returns null, then it uses {@link IOUtil#objectToBytes(Object)}; then returns a hex
     * representation of the resulting byte array.
     */
    @Override
    public String toString(Object value) {
        if (value == null)
            return null;
        byte[] bytes = toByteArrayAlways(value);
        StringBuilder sb = new StringBuilder(2 + 2 * bytes.length);
        sb.append("0x");
        for (byte b : bytes) {
            int x = b & 0xff;
            sb.append(HEX[x >> 4]).append(HEX[x & 0x0f]);
        }
        return sb.toString();
    }

    @Override
    public Object parseString(String value) {
        if (value == null)
            return null;
        String s = value.toUpperCase(Locale.US);
    hex_decode:
        if (s.startsWith("0X") && s.length() % 2 == 0) {
            byte[] b = new byte[s.length() / 2 - 1];
            for (int i = 0; i < b.length; i++) {
                int hi = Arrays.binarySearch(HEX, s.charAt(2 * i + 2));
                int lo = Arrays.binarySearch(HEX, s.charAt(2 * i + 3));
                if (hi < 0 || lo < 0)
                    break hex_decode;
                b[i] = (byte) ((hi << 4) + lo);
            }
            return b;
        }
        // failed to decode hex -- return just string bytes
        return value.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object value1, Object value2) {
        if (value1 == value2)
            return true;
        else if (value1 == null || value2 == null)
            return false;
        else if (value1 instanceof byte[] && value2 instanceof byte[])
            return Arrays.equals((byte[]) value1, (byte[]) value2);
        else if (value1 instanceof String && value2 instanceof String)
            return value1.equals(value2);
        else if (value1 instanceof char[] && value2 instanceof char[])
            return Arrays.equals((char[]) value1, (char[]) value2);
        else
            return Arrays.equals(toByteArrayAlways(value1), toByteArrayAlways(value2));
    }

    @Override
    public void writeObj(DataOutput out, Object value) throws IOException {
        byte[] bytes = toByteArray(value);
        if (bytes != null)
            IOUtil.writeByteArray(out, bytes);
        else
            IOUtil.writeObject(out, value);
    }

    @Override
    public void writeObj(BufferedOutput out, Object value) throws IOException {
        byte[] bytes = toByteArray(value);
        if (bytes != null)
            out.writeByteArray(bytes);
        else
            out.writeObject(value);
    }

    @Override
    public Object readObj(DataInput in) throws IOException {
        return fromByteArray(IOUtil.readByteArray(in));
    }

    @Override
    public Object readObj(BufferedInput in) throws IOException {
        return in.readByteArray();
    }
}
