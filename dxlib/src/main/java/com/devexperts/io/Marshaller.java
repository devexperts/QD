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

import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectOutputStream;

/**
 * A strategy that defines how an object is represented in a byte array inside of a
 * {@link Marshalled Marshalled} object. Marshallers should write some magic number at the
 * beginning of their byte array as a fail-safe check, so if, by mistake, an array of bytes that was
 * created by one marshaller is given the different marshaller to deserialize it fails quickly
 * and cleanly.
 *
 * <p>There is a number of built-in marshallers provided:
 * <ul>
 * <li>{@link #SERIALIZATION} marshaller uses Java Object Serialization to convert object to/from bytes.
 * Its type-safe version that also performs run-time type check is available via
 * {@link #serialization(Class)} method. The corresponding byte array always starts with two bytes
 * 0xAC and 0xED or with ZLIB compression header.
 * <li>Typed marshaller that uses compact serialization of primitive data types and arrays is
 * available via {@link #forClasses(Class[])} and {@link #forTypes(String)} methods.
 * The corresponding byte array always starts with 0xE8 byte or with ZLIB compression header.
 * </ul>
 *
 * @param <T> the marshalled object's type.
 */
public abstract class Marshaller<T> {
    private volatile Marshalled<T> nullObjectMarshalled; // created on first need
    private volatile Marshalled<T> nullBytesMarshalled;  // created on first need

    /**
     * Converts object to/from bytes using Java Object Serialization.
     * The marshaller works via
     * {@link IOUtil#objectToBytes(Object) IOUtil.objectToBytes} and
     * {@link IOUtil#bytesToObject(byte[], ClassLoader) IOUtil.bytesToObject}
     * methods. For a type-safe generic version of this marshaller,
     * use {@link #serialization(Class)}.
     */
    public static final Marshaller<Object> SERIALIZATION =
        new SerializationMarshallerImpl<>(SerializationMarshallerImpl.OBJECT_TYPE);

    protected Marshaller() {
    }

    /**
     * Returns marshaller that converts object to/from bytes using Java Object Serialization.
     * The marshaller works via
     * {@link IOUtil#objectToBytes(Object) IOUtil.objectToBytes} and
     * {@link IOUtil#bytesToObject(byte[], ClassLoader) IOUtil.bytesToObject}
     * methods. It performs run-time check that serialized and deserialized object is of a specified type.
     * Serialization marshallers with different types produce the same byte arrays which are binary compatible.
     *
     * @param clazz the type of the object to serialize and to expect after deserialization.
     * @return marshaller that converts object to bytes using Java Object Serialization.
     */
    @SuppressWarnings("unchecked")
    public static <T> Marshaller<T> serialization(Class<T> clazz) {
        return new SerializationMarshallerImpl<>(clazz);
    }

    /**
     * Returns marshaller that converts object to/from bytes using Java Object Serialization.
     * The marshaller works via
     * {@link IOUtil#objectToBytes(Object) IOUtil.objectToBytes},
     * {@link IOUtil#bytesToObject(byte[], ClassLoader) IOUtil.bytesToObject} and
     * {@link IOUtil#bytesToObject(byte[], SerialClassContext) IOUtil.bytesToObject}
     * methods. It performs run-time check that serialized and deserialized object is of a specified type.
     * Serialization marshallers with different types produce the same byte arrays which are binary compatible.
     *
     * @return marshaller that converts object to bytes using Java Object Serialization.
     */
    @SuppressWarnings("unchecked")
    public static Marshaller<Object> serialization() {
        return new SerializationMarshallerImpl<>(SerializationMarshallerImpl.OBJECT_TYPE);
    }


    /**
     * Returns typed marshaller for the given class.
     * If the class corresponds to primitive type
     * or array, then the object is converted to byte array in the compact form described in {@link IOUtil}.
     * Otherwise, Java Object Serialization is used.
     * Unlike {@link #serialization(Class)} serialization} marshallers, typed marshallers for different
     * types are not the same and are not compatible on a byte-array level even if the actual serialized objects
     * are the same. The same exact marshaller shall be used for writing and for reading.
     *
     * @param clazz the class.
     * @return typed marshaller.
     */
    public static <T> Typed<T> forClass(Class<? extends T> clazz) {
        return new TypedMarshallerImpl<>(true, clazz);
    }

    /**
     * Returns typed marshaller for the given array of classes.
     * If the class corresponds to primitive type
     * or array, then the object is converted to byte array in the compact form described in {@link IOUtil}.
     * Otherwise, Java Object Serialization is used.
     * Unlike {@link #serialization(Class)} serialization} marshallers, typed marshallers for different
     * types are not the same and are not compatible on a byte-array level even if the actual serialized objects
     * are the same. The same exact marshaller shall be used for writing and for reading.
     *
     * @param classes the array of classes.
     * @return typed marshaller.
     */
    @SafeVarargs
    public static <T> Typed<T[]> forClasses(Class<? extends T>... classes) {
        return new TypedMarshallerImpl<>(false, classes);
    }

    /**
     * Returns typed marshaller for the given type name.
     * If the type corresponds to primitive type
     * or array, then the object is converted to byte array in the compact form described in {@link IOUtil}.
     * Otherwise, Java Object Serialization is used.
     * Unlike {@link #serialization(Class)} serialization} marshallers, typed marshallers for different
     * types are not the same and are not compatible on a byte-array level even if the actual serialized objects
     * are the same. The same exact marshaller shall be used for writing and for reading.
     *
     * @param type the type name.
     * @return typed marshaller.
     */
    public static Typed<?> forType(String type) {
        return new TypedMarshallerImpl<>(true, type);
    }

    /**
     * Returns typed marshaller for the given comma-separated list of type names.
     * If the type corresponds to primitive type
     * or array, then the object is converted to byte array in the compact form described in {@link IOUtil}.
     * Otherwise, Java Object Serialization is used.
     * Unlike {@link #serialization(Class)} serialization} marshallers, typed marshallers for different
     * types are not the same and are not compatible on a byte-array level even if the actual serialized objects
     * are the same. The same exact marshaller shall be used for writing and for reading.
     *
     * @param types the comma-separated list of type names.
     * @return typed marshaller.
     */
    public static Typed<Object[]> forTypes(String types) {
        return new TypedMarshallerImpl<>(false, types);
    }

    /**
     * Writes the length of an object in serialized form to the output according to this marshalling strategy.
     * This implementation uses {@link BufferedOutput#writeCompactInt(int) writeCompactInt}.
     *
     * @param out    the destination to write to.
     * @param length of an object in serialized form to be written or -1 when the object is null.
     * @throws IOException if an I/O error occurs.
     */
    public void writeMarshalledLength(BufferedOutput out, int length) throws IOException {
        out.writeCompactInt(length);
    }

    /**
     * Reads the length of an object in serialized form from the input according to this marshalling strategy.
     * This implementation uses {@link BufferedInput#readCompactInt() readCompactInt}.
     *
     * @param in the source to read from.
     * @return the length of an object in serialized form or {@code -1} if the marshalled object is null.
     * @throws IOException if an I/O error occurs.
     */
    public int readMarshalledLength(BufferedInput in) throws IOException {
        return in.readCompactInt();
    }

    /**
     * Writes byte representation of the object according to this marshalling strategy into
     * the specified output. This method should not write a length of the representation
     * at the beginning, but should write some kind of a magic signature so that
     * {@link #readObjectFrom(BufferedInput, int, SerialClassContext) readObjectFrom} can fail fast
     * when trying to read wrong representation that was created with different marshaller.
     *
     * @param out the output.
     * @param object the object to be serialized.
     * @throws NullPointerException if object is null.
     * @throws IllegalArgumentException if this marshaller does not support the specified object.
     * @throws IOException if object cannot be serialized.
     */
    public abstract void writeObjectTo(BufferedOutput out, T object) throws IOException;

    /**
     * Reads object representation according to this marshalling strategy from the specified input.
     *
     * @param in            the input.
     * @param length        the length in bytes of object representation.
     * @param serialContext the serial class context.
     * @return the deserialized object.
     * @throws IllegalArgumentException if length is negative.
     * @throws IOException if object cannot be deserialized.
     */
    public abstract T readObjectFrom(BufferedInput in, int length, SerialClassContext serialContext) throws IOException;

    /**
     * Returns {@code true} if this marshaller support marshalling of {@code null} objects in
     * {@link #writeObjectTo(BufferedOutput, Object) writeObjectTo} method.
     * If this method returns {@code false}, then
     * an attempt to create marshalled object with
     * <code>{@link Marshalled#forObject(Object, Marshaller) Marshalled.forObject}(null, this)</code> throws
     * {@link NullPointerException}.
     * <p/>
     * <p>This implementation returns {@code true}.
     *
     * @return {@code true} if this marshaller support marshalling of {@code null} objects in
     */
    public boolean supportsNullObject() {
        return true;
    }

    /**
     * Returns {@code true} if this marshaller represents {@code null} object with {@code null} byte array.
     * This method is never called if {@link #supportsNullObject()} returns {@code false}.
     * If this method returns {@code true}, then {@link #writeObjectTo(BufferedOutput, Object) writeObjectTo}
     * is never invoked for null object.
     * <p/>
     * <p>This implementation returns {@code true}.
     *
     * @return {@code true} if this marshaller represents {@code null} object with {@code null} byte array.
     */
    public boolean representsNullObjectAsNullBytes() {
        return true;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * This implementation return true when the other object's class is equal to this one.
     *
     * @return {@code true} if this object is the same as the obj
     * argument; {@code false} otherwise.
     */
    @Override
    public boolean equals(Object obj) {
        return obj != null && getClass().equals(obj.getClass());
    }

    /**
     * Returns a hash code value for the object.
     * This implementation return hashCode of this object's class name.
     *
     * @return a hash code value for this object.
     */
    @Override
    public int hashCode() {
        return getClass().getName().hashCode();
    }

    // ================= helper methods for Marshalled =================

    Marshalled<T> getNullObjectMarshalled() {
        if (!supportsNullObject())
            throw new NullPointerException("Null object is not supported by " + this);
        Marshalled<T> nullObjectMarshalled = this.nullObjectMarshalled;
        if (nullObjectMarshalled != null)
            return nullObjectMarshalled;
        return getNullObjectMarshalledSync();
    }

    private synchronized Marshalled<T> getNullObjectMarshalledSync() {
        Marshalled<T> nullObjectMarshalled = this.nullObjectMarshalled;
        if (nullObjectMarshalled != null)
            return nullObjectMarshalled;
        if (representsNullObjectAsNullBytes())
            nullObjectMarshalled = getNullBytesMarshalledSync();
        else {
            ByteArrayOutput out = new ByteArrayOutput();
            try {
                writeObjectTo(out, null);
            } catch (IOException e) {
                throw new RuntimeException("Failed to represent null object with " + this, e);
            }
            nullObjectMarshalled = new Marshalled<>(null, out.toByteArray(), this, null);
        }
        return this.nullObjectMarshalled = nullObjectMarshalled;
    }

    Marshalled<T> getNullBytesMarshalled() {
        if (!supportsNullObject())
            throw new NullPointerException("Null object is not supported by " + this);
        Marshalled<T> nullBytesMarshalled = this.nullBytesMarshalled;
        if (nullBytesMarshalled != null)
            return nullBytesMarshalled;
        return getNullBytesMarshalledSync();
    }

    private synchronized Marshalled<T> getNullBytesMarshalledSync() {
        Marshalled<T> nullBytesMarshalled = this.nullBytesMarshalled;
        if (nullBytesMarshalled != null)
            return nullBytesMarshalled;
        nullBytesMarshalled = new Marshalled<>(null, null, this, null);
        return this.nullBytesMarshalled = nullBytesMarshalled;
    }

    /**
     * Typed marshaller that marshals instances of a single type or an array of types.
     * Instances of this abstract class are obtained using
     * {@link #forTypes(String) Marshaller.forTypes} and {@link #forClasses(Class[]) Marshaller.forClasses}
     * methods for multiple types, or using
     * {@link #forType(String) Marshaller.forType} and {@link #forClass(Class) Marshaller.forClass}
     * for a single type.
     * <p/>
     * <p>The serial form of typed marshaller uses efficient serialization of primitive data
     * (individual and arrays) and single {@link ObjectOutputStream} for remaining non-primitive data.
     * Unlike {@link #serialization(Class)} serialization} marshallers, typed marshallers for different
     * types are not the same and are not compatible on a byte-array level even if the actual serialized objects
     * are the same. The same exact marshaller shall be used for writing and for reading.
     *
     * @param <T> the marshalled object's type.
     */
    public abstract static class Typed<T> extends Marshaller<T> {
        // is implemented only in this package. No 3rd party implementations, please
        Typed() {
        }

        /**
         * Returns a comma-separated list of type names of this typed marshaller.
         * Type names are the same as returned by {@link Class#getName() Class.getName()} method.
         *
         * @return a comma-separated list of type names.
         */
        public abstract String getTypes();

        /**
         * Returns an array of classes of this typed marshaller. {@code ClassLoader} argument
         * is used only by the first successful invocation of this method. The resolved classes
         * are stored in the instance of this class afterwards.
         *
         * @param cl Classloader to resolve classes that were specified by their types names.
         * @return an array of classes of this typed marshaller.
         * @throws InvalidClassException if classes cannot be found/loaded.
         */
        public abstract Class<?>[] getClasses(ClassLoader cl) throws InvalidClassException;

        /**
         * Indicates whether some other object is "equal to" this one.
         * Typed marshallers are equal when their {@link #getTypes() types} are the same and
         * they were both produced by
         * {@link Marshaller#forTypes(String) Marshaller.forTypes} and
         * {@link Marshaller#forClasses(Class[]) Marshaller.forClasses} or by
         * {@link Marshaller#forType(String) Marshaller.forType} and
         * {@link Marshaller#forClass(Class) Marshaller.forClass} methods.
         * <p/>
         * <p>Note, that comparison for equality does not check the equality of actual classes, so
         * typed marshallers for different classes with the same name (from different class loaders)
         * are considered to be equal, even though they are not necessary interchangeable between each other.
         *
         * @return {@code true} if this object is the same as the obj
         * argument; {@code false} otherwise.
         */
        public abstract boolean equals(Object obj);

        /**
         * Returns a hash code value for the object that is consistent with {@link #equals(Object) equals}.
         *
         * @return a hash code value for this object.
         */
        public abstract int hashCode();
    }
}
