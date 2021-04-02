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
import java.io.UTFDataFormatException;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;

/**
 * The utility class that provide helper methods for data serialization and deserialization.
 * It is a "utility for utility" class that is strictly package-private and shall remain as such.
 */
class CompactSerializer {
    private CompactSerializer() {}

    // ========== UTF Utility API ==========

    static void writeUTF2Unchecked(DataOutput out, int codePoint) throws IOException {
        out.writeShort(0xC080 | codePoint << 2 & 0x1F00 | codePoint & 0x3F);
    }

    static void writeUTF3Unchecked(DataOutput out, int codePoint) throws IOException {
        out.writeByte(0xE0 | codePoint >>> 12);
        out.writeShort(0x8080 | codePoint << 2 & 0x3F00 | codePoint & 0x3F);
    }

    static void writeUTF4Unchecked(DataOutput out, int codePoint) throws IOException{
        out.writeInt(0xF0808080 | codePoint << 6 & 0x07000000 | codePoint << 4 & 0x3F0000 | codePoint << 2 & 0x3F00 | codePoint & 0x3F);
    }

    static char readUTF2(DataInput in, int first) throws IOException {
        int second = in.readByte();
        if ((second & 0xC0) != 0x80)
            throw new UTFDataFormatException();
        return (char) (((first & 0x1F) << 6) | (second & 0x3F));
    }

    static char readUTF3(DataInput in, int first) throws IOException {
        int tail = in.readShort();
        if ((tail & 0xC0C0) != 0x8080)
            throw new UTFDataFormatException();
        return (char) (((first & 0x0F) << 12) | ((tail & 0x3F00) >> 2) | (tail & 0x3F));
    }

    static int readUTF4(DataInput in, int first) throws IOException {
        int second = in.readByte();
        int tail = in.readShort();
        if ((second & 0xC0) != 0x80 || (tail & 0xC0C0) != 0x8080)
            throw new UTFDataFormatException();
        int codePoint = ((first & 0x07) << 18) | ((second & 0x3F) << 12) | ((tail & 0x3F00) >> 2) | (tail & 0x3F);
        if (codePoint > 0x10FFFF)
            throw new UTFDataFormatException();
        return codePoint;
    }

    static int readUTFBody(DataInput in, long utfLen, char[] chars, int offset) throws IOException {
        int count = offset;
        while (utfLen > 0) {
            int c = in.readByte();
            if (c >= 0) {
                utfLen--;
                chars[count++] = (char) c;
            } else if ((c & 0xE0) == 0xC0) {
                utfLen -= 2;
                chars[count++] = readUTF2(in, c);
            } else if ((c & 0xF0) == 0xE0) {
                utfLen -= 3;
                chars[count++] = readUTF3(in, c);
            } else if ((c & 0xF8) == 0xF0) {
                utfLen -= 4;
                count += Character.toChars(readUTF4(in, c), chars, count);
            } else
                throw new UTFDataFormatException();
        }
        if (utfLen < 0)
            throw new UTFDataFormatException();
        return count - offset;
    }

    // ========== Compact Primitives Utility API ==========

    /**
     * Returns <code>true</code> if specified class can be compactly serialized.
     * Compact serialization is provided for <code>void</code> class, all primitive classes,
     * {@link String} class, and arrays (multi-dimensional) based on all these classes.
     *
     * @param clazz class to check
     * @return <code>true</code> if specified class can be compactly serialized
     */
    static boolean isCompact(Class<?> clazz) {
        return TYPES_MAP.containsKey(clazz) || (clazz.isArray() && isCompact(clazz.getComponentType()));
    }

    /**
     * Writes a value into the output in a compact format.
     *
     * @param out the destination to write to
     * @param clazz the exact {@link Class} of a value
     * @param value the value to be written
     * @throws IllegalArgumentException
     * @throws ClassCastException
     * @throws IOException if an I/O error occurs,
     *         or if clazz is not a compactly serializable class, or if value is not an instance of clazz
     */
    static void writeCompact(BufferedOutput out, Class<?> clazz, Object value) throws IOException {
        writeCompact(out, clazz, getCompactType(clazz), value);
    }

    /**
     * Reads a value from the input in a compact format.
     *
     * @param in the source to read from
     * @param clazz the exact {@link Class} of a value
     * @return the value read
     * @throws IllegalArgumentException if clazz is not a compactly serializable class
     * @throws IOException if an I/O error occurs
     */
    static Object readCompact(BufferedInput in, Class<?> clazz) throws IOException {
        return readCompact(in, clazz, getCompactType(clazz));
    }

    // ========== Implementation Details ==========

    /**
     *
     * @throws IOException if an I/O error occurs,
     *         or if clazz is not a compactly serializable class, or if value is not an instance of clazz.
     */
    private static void writeCompact(BufferedOutput out, Class<?> clazz, CompactType type, Object value) throws IOException {
        try {
            if (clazz.isArray()) {
                if (value == null) {
                    out.writeCompactInt(-1);
                    return;
                }
                Class<?> componentType = clazz.getComponentType();
                if (componentType.isArray()) {
                    Object[] array = (Object[]) value;
                    out.writeCompactInt(array.length);
                    for (Object element : array)
                        writeCompact(out, componentType, type, element);
                } else {
                    writePrimitiveArray(out, type, value);
                }
            } else {
                writePrimitiveValue(out, type, value);
            }
        } catch (ClassCastException e) {
            throw new IOException(e);
        }
    }

    private static Object readCompact(BufferedInput in, Class<?> clazz, CompactType type) throws IOException {
        if (clazz.isArray()) {
            long length = in.readCompactLong();
            if (length < -1 || length > Integer.MAX_VALUE)
                throw new IOException("Illegal length.");
            if (length == -1)
                return null;
            Class<?> componentType = clazz.getComponentType();
            if (componentType.isArray()) {
                Object[] array = (Object[]) Array.newInstance(componentType, (int) length);
                for (int i = 0; i < length; i++)
                    array[i] = readCompact(in, componentType, type);
                return array;
            } else {
                return readPrimitiveArray(in, type, (int) length);
            }
        } else {
            return readPrimitiveValue(in, type);
        }
    }

    // Can throw ClassCastException
    private static void writePrimitiveArray(BufferedOutput out, CompactType type, Object value) throws IOException {
        switch (type) {
        case BOOLEAN: {
            boolean[] array = (boolean[]) value;
            out.writeCompactInt(array.length);
            for (boolean element : array)
                out.writeBoolean(element);
            break;
        }
        case BYTE: {
            byte[] array = (byte[]) value;
            out.writeCompactInt(array.length);
            out.write(array);
            break;
        }
        case CHAR: {
            char[] array = (char[]) value;
            out.writeCompactInt(array.length);
            for (char element : array)
                out.writeUTFChar(element);
            break;
        }
        case SHORT: {
            short[] array = (short[]) value;
            out.writeCompactInt(array.length);
            for (short element : array)
                out.writeCompactInt(element);
            break;
        }
        case INT: {
            int[] array = (int[]) value;
            out.writeCompactInt(array.length);
            for (int element : array)
                out.writeCompactInt(element);
            break;
        }
        case LONG: {
            long[] array = (long[]) value;
            out.writeCompactInt(array.length);
            for (long element : array)
                out.writeCompactLong(element);
            break;
        }
        case FLOAT: {
            float[] array = (float[]) value;
            out.writeCompactInt(array.length);
            for (float element : array)
                out.writeFloat(element);
            break;
        }
        case DOUBLE: {
            double[] array = (double[]) value;
            out.writeCompactInt(array.length);
            for (double element : array)
                out.writeDouble(element);
            break;
        }
        case STRING: {
            String[] array = (String[]) value;
            out.writeCompactInt(array.length);
            for (String element : array)
                out.writeUTFString(element);
            break;
        }
        default:
            throw new IllegalArgumentException("Unknown primitive type.");
        }
    }

    private static Object readPrimitiveArray(BufferedInput in, CompactType type, int length) throws IOException {
        switch (type) {
        case BOOLEAN: {
            boolean[] array = new boolean[length];
            for (int i = 0; i < length; i++)
                array[i] = in.readBoolean();
            return array;
        }
        case BYTE: {
            byte[] array = new byte[length];
            in.readFully(array);
            return array;
        }
        case CHAR: {
            char[] array = new char[length];
            for (int i = 0; i < length; i++)
                array[i] = (char) in.readUTFChar();
            return array;
        }
        case SHORT: {
            short[] array = new short[length];
            for (int i = 0; i < length; i++)
                array[i] = (short) in.readCompactInt();
            return array;
        }
        case INT: {
            int[] array = new int[length];
            for (int i = 0; i < length; i++)
                array[i] = in.readCompactInt();
            return array;
        }
        case LONG: {
            long[] array = new long[length];
            for (int i = 0; i < length; i++)
                array[i] = in.readCompactLong();
            return array;
        }
        case FLOAT: {
            float[] array = new float[length];
            for (int i = 0; i < length; i++)
                array[i] = in.readFloat();
            return array;
        }
        case DOUBLE: {
            double[] array = new double[length];
            for (int i = 0; i < length; i++)
                array[i] = in.readDouble();
            return array;
        }
        case STRING: {
            String[] array = new String[length];
            for (int i = 0; i < length; i++)
                array[i] = in.readUTFString();
            return array;
        }
        default:
            throw new IllegalArgumentException("Unknown primitive type.");
        }
    }

    // can throw ClassCastException
    private static void writePrimitiveValue(BufferedOutput out, CompactType type, Object value) throws IOException {
        switch (type) {
        case VOID:
            break;
        case BOOLEAN:
            out.writeBoolean(((Boolean) value).booleanValue());
            break;
        case BYTE:
            out.writeByte(((Byte) value).byteValue());
            break;
        case CHAR:
            out.writeUTFChar((Character) value);
            break;
        case SHORT:
            out.writeCompactInt((Short) value);
            break;
        case INT:
            out.writeCompactInt((Integer) value);
            break;
        case LONG:
            out.writeCompactLong((Long) value);
            break;
        case FLOAT:
            out.writeFloat(((Float) value).floatValue());
            break;
        case DOUBLE:
            out.writeDouble(((Double) value).doubleValue());
            break;
        case STRING:
            out.writeUTFString((String) value);
            break;
        default:
            throw new IllegalArgumentException("Unknown primitive type.");
        }
    }

    private static Object readPrimitiveValue(BufferedInput in, CompactType type) throws IOException {
        switch (type) {
        case VOID:
            return null;
        case BOOLEAN:
            return in.readBoolean();
        case BYTE:
            return in.readByte();
        case CHAR:
            return (char) in.readUTFChar();
        case SHORT:
            return (short) in.readCompactInt();
        case INT:
            return in.readCompactInt();
        case LONG:
            return in.readCompactLong();
        case FLOAT:
            return in.readFloat();
        case DOUBLE:
            return in.readDouble();
        case STRING:
            return in.readUTFString();
        default:
            throw new IllegalArgumentException("Unknown primitive type.");
        }
    }

    private static CompactType getCompactType(Class<?> clazz) {
        CompactType type = TYPES_MAP.get(clazz);
        if (type != null)
            return type;
        if (clazz.isArray())
            return getCompactType(clazz.getComponentType());
        throw new IllegalArgumentException("Cannot compactly serialize " + clazz + ".");
    }

    private enum CompactType {
        VOID,
        BOOLEAN,
        BYTE,
        CHAR,
        SHORT,
        INT,
        LONG,
        FLOAT,
        DOUBLE,
        STRING,
    }

    private static final Map<Class<?>, CompactType> TYPES_MAP = new HashMap<Class<?>, CompactType>();
    static {
        TYPES_MAP.put(void.class, CompactType.VOID);
        TYPES_MAP.put(boolean.class, CompactType.BOOLEAN);
        TYPES_MAP.put(byte.class, CompactType.BYTE);
        TYPES_MAP.put(char.class, CompactType.CHAR);
        TYPES_MAP.put(short.class, CompactType.SHORT);
        TYPES_MAP.put(int.class, CompactType.INT);
        TYPES_MAP.put(long.class, CompactType.LONG);
        TYPES_MAP.put(float.class, CompactType.FLOAT);
        TYPES_MAP.put(double.class, CompactType.DOUBLE);
        TYPES_MAP.put(String.class, CompactType.STRING);

        TYPES_MAP.put(boolean[].class, CompactType.BOOLEAN);
        TYPES_MAP.put(byte[].class, CompactType.BYTE);
        TYPES_MAP.put(char[].class, CompactType.CHAR);
        TYPES_MAP.put(short[].class, CompactType.SHORT);
        TYPES_MAP.put(int[].class, CompactType.INT);
        TYPES_MAP.put(long[].class, CompactType.LONG);
        TYPES_MAP.put(float[].class, CompactType.FLOAT);
        TYPES_MAP.put(double[].class, CompactType.DOUBLE);
        TYPES_MAP.put(String[].class, CompactType.STRING);
    }
}
