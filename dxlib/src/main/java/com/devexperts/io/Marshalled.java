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

import com.devexperts.util.LogUtil;

import java.io.DataOutput;
import java.io.IOException;
import java.util.Arrays;

/**
 * Lazily encapsulates object and its byte array representation.
 * The object representation is available via {@link #getObject()} method and
 * byte array representation via {@link #getBytes()} method.
 * Conversions from byte array to object and from object to byte array are performed lazily and
 * results of conversions are cached. The strategy the defines how the object is represented
 * in a byte array is specified by {@link Marshaller}. There is a built-in
 * {@link Marshaller#SERIALIZATION serialization} marshaller that uses Java Object Serialization.
 * It is used by default. See {@link Marshaller} class for more details.
 * <p/>
 * <p>{@code null} object is always represented by {@code null} byte array and vice versa.
 * <p/>
 * <p>{@code Marshalled} object is not serializable by itself, but {@link IOUtil#objectToBytes(Object)} and
 * {@link IOUtil#writeObject(DataOutput, Object)} methods understand this object and use the result of
 * {@link #getBytes()} invocation when {@link Marshaller#SERIALIZATION serialization} marshaller is used.
 * <p/>
 * <h3>Class loaders</h3>
 * <p/>
 * Object representation is resolved on the first invocation of {@link #getObject()} or {@link #getObject(ClassLoader)}
 * methods, unless it was explicitly specified during marshalled object creation via
 * {@link #forObject(Object)} or {@link #forObject(Object, Marshaller)} methods. The first invocation
 * of {@code getObject} defines what class loader is used. If object cannot be deserialized from the
 * corresponding byte array on the first {@code getObject} invocation, then all subsequent invocations of
 * any {@code getObject} method will throw the same {@link MarshallingException}.
 * <p/>
 * <h3>Threads and locks</h3>
 * <p/>
 * This class is thread-safe. Conversion operations to and from byte array are synchronized internally to ensure
 * that there is at most one conversion operation in progress at any time.
 *
 * @param <T> the type of the marshalled object.
 */
public final class Marshalled<T> {

    /**
     * The null object for default {@link Marshaller#SERIALIZATION SERIALIZATION} marshaller.
     * This object the the result of <code>{@link #forObject(Object) forObject}(<b>null</b>)</code>
     * invocation and of the <code>{@link #forBytes(byte[]) forBytes}(<b>null</b>)</code> invocation.
     * Both {@link #getObject() getObject} and {@link #getBytes() getBytes} methods on this
     * instance return {@code null}.
     */
    public static final Marshalled<Object> NULL = Marshaller.SERIALIZATION.getNullObjectMarshalled();

    /**
     * Unwraps {@link Marshalled} argument via {@link #getObject} call or returns object unmodified.
     */
    @SuppressWarnings("rawtypes")
    public static Object unwrap(Object object) {
        return object instanceof Marshalled ? ((Marshalled) object).getObject() : object;
    }

    /**
     * Wraps object into marshalled with a {@link Marshaller#SERIALIZATION serialization} marshaller.
     * The result of this method is not {@code null}. It returns {@link #NULL NULL} object
     * when the argument is {@code null}.
     *
     * @param object the object.
     * @param <T>    the type of the object.
     * @return marshalled object.
     */
    @SuppressWarnings("unchecked")
    public static <T> Marshalled<T> forObject(T object) {
        // Note, that we don't need an instance of type-checking serialization marshaller here, because we know
        // that the object is of correct type and we're going only to use serialize it to byte array when needed.
        return object == null ? (Marshalled<T>) NULL :
            new Marshalled<>(object, UNDEFINED, (Marshaller<T>) Marshaller.SERIALIZATION, null);
    }

    /**
     * Wraps object into marshalled with a specified marshaller.
     * The result of this method is not {@code null}.
     *
     * @param object     the object.
     * @param marshaller the marshaller.
     * @param <T>        the type of the object.
     * @return marshalled object.
     * @throws NullPointerException if marshaller does not
     *                              {@link Marshaller#supportsNullObject() support null object} and object is {@code null}.
     */
    public static <T> Marshalled<T> forObject(T object, Marshaller<T> marshaller) {
        return object == null ? marshaller.getNullObjectMarshalled() :
            new Marshalled<>(object, UNDEFINED, marshaller, null);
    }

    /**
     * Returns marshalled object for a specified byte array assuming that
     * The result of this method is not {@code null}. It returns {@link #NULL NULL} object
     * when the argument is {@code null}.
     * <p/>
     * {@link Marshaller#SERIALIZATION serialization} marshaller was used to create the specified byte array.
     *
     * @param bytes the byte array.
     * @return marshalled object.
     */
    @SuppressWarnings("unchecked")
    public static Marshalled<Object> forBytes(byte[] bytes) {
        return bytes == null ? NULL :
            new Marshalled<>(UNDEFINED, bytes, Marshaller.SERIALIZATION, SerialClassContext.getDefaultSerialContext(null));
    }

    /**
     * Returns marshalled object for a specified byte array and marshaller.
     * The result of this method is not {@code null}.
     *
     * @param bytes the byte array.
     * @return marshalled object.
     */
    public static <T> Marshalled<T> forBytes(byte[] bytes, Marshaller<T> marshaller) {
        return bytes == null ? marshaller.getNullBytesMarshalled() :
            new Marshalled<>((T) UNDEFINED, bytes, marshaller, SerialClassContext.getDefaultSerialContext(null));
    }

    /**
     * Returns marshalled object for a specified byte array and marshaller.
     * The result of this method is not {@code null}.
     *
     * @param bytes the byte array.
     * @param serialContext  the serial class context for load class.
     * @return marshalled object.
     */
    public static <T> Marshalled<T> forBytes(byte[] bytes, Marshaller<T> marshaller, SerialClassContext serialContext) {
        return bytes == null ? marshaller.getNullBytesMarshalled() :
            new Marshalled<>((T) UNDEFINED, bytes, marshaller, serialContext);
    }

    private static final byte[] UNDEFINED = new byte[0]; // Marker that field value is not defined yet.

    private final SerialClassContext serialContext;
    private volatile T object;
    private volatile byte[] bytes;
    private volatile MarshallingException exception;
    private final Marshaller<T> marshaller;

    Marshalled(T object, byte[] bytes, Marshaller<T> marshaller, SerialClassContext serialContext) {
        this.object = object;
        this.bytes = bytes;
        this.marshaller = marshaller;
        this.serialContext = serialContext;
    }

    /**
     * Returns object representation of this marshalled object.
     * This methods loads classes using a context class loader
     * (see {@link Thread#getContextClassLoader() Thread.getContextClassLoader}) or
     * using the same class loader that loaded classes for this {@code com.devexperts.io} package when
     * context class loader is not defined.
     * This is a shortcut for <code>{@link #getObject(ClassLoader) getObject}(null)</code>.
     *
     * @return object representation of this marshalled object.
     * @throws MarshallingException if object cannot be deserialized from its byte array
     */
    @SuppressWarnings("unchecked")
    public T getObject() {
        T object = this.object;
        if (object != UNDEFINED)
            return object;
        MarshallingException exception = this.exception;
        if (exception != null)
            throw exception;
        return getObjectSync(serialContext);
    }

    /**
     * Returns object representation of this marshalled object with a specified class loader.
     *
     * @param cl the ClassLoader that will be used to load classes;
     *           <code>null</code> for {@link Thread#getContextClassLoader() context} class loader.
     * @return object representation of this marshalled object with a specified class loader.
     * @throws MarshallingException if object cannot be deserialized from its byte array
     * @deprecated use {@link #getObject()}
     */
    public T getObject(ClassLoader cl) {
        T object = this.object;
        if (object != UNDEFINED)
            return object;
        MarshallingException exception = this.exception;
        if (exception != null)
            throw exception;
        return getObjectSync(SerialClassContext.getDefaultSerialContext(cl));
    }

    private synchronized T getObjectSync(SerialClassContext serialContext) {
        T object = this.object;
        if (object != UNDEFINED)
            return object;
        MarshallingException exception = this.exception;
        if (exception != null)
            throw exception;
        try {
            ByteArrayInput in = new ByteArrayInput(bytes);
            return this.object = marshaller.readObjectFrom(in, bytes.length, serialContext);
        } catch (IOException e) {
            throw this.exception = new MarshallingException(e);
        }
    }

    /**
     * Ensures that object is converted into byte representation.
     *
     * @throws MarshallingException if object cannot be converted to byte representation
     */
    public void ensureBytes() {
        getBytes();
    }

    /**
     * Returns byte array representation of this marshalled object.
     *
     * @return byte array representation of this marshalled object.
     */
    @SuppressWarnings("unchecked")
    public byte[] getBytes() {
        byte[] bytes = this.bytes;
        if (bytes != UNDEFINED)
            return bytes;
        MarshallingException exception = this.exception;
        if (exception != null)
            throw exception;
        return getBytesSync();
    }

    private synchronized byte[] getBytesSync() {
        byte[] bytes = this.bytes;
        if (bytes != UNDEFINED)
            return bytes;
        MarshallingException exception = this.exception;
        if (exception != null)
            throw exception;
        try {
            /*
             * Important performance consideration here:
             *
             * Typical implementations of Marshaller.writeObjectTo method work via a single invocation of
             * ObjectSerializer.writeBody or ObjectSerializer.writeBodiesWithType methods which, in turn
             * writes a pre-processed array of bytes into the output using a single invocation of
             * BufferedOutput.write(byte[] b, int off, int len) method.
             *
             * This method is overridden in ByteArrayOutput to allocate the buffer for precisely the number
             * of bytes that are being written. Thus, the length of ByteArrayOutput's buffer is usually equal
             * to the number of bytes written and this buffer is returned without extra copying of bytes.
             */
            ByteArrayOutput out = new ByteArrayOutput();
            marshaller.writeObjectTo(out, object);
            bytes = out.getBuffer();
            if (bytes.length != out.getPosition())
                bytes = out.toByteArray(); // create an appropriately sized byte array instead of internal buffer
            return this.bytes = bytes;
        } catch (IOException e) {
            throw this.exception = new MarshallingException(e);
        }
    }

    /**
     * Returns a strategy that defines how an object is represented in a byte array.
     *
     * @return a strategy that defines how an object is represented in a byte array.
     */
    public Marshaller<T> getMarshaller() {
        return marshaller;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * Two marshalled objects are equal when their
     * {@link #getMarshaller() marshallers} and {@link #getBytes() bytes} are equal.
     *
     * @return {@code true} if this object is the same as the obj
     * argument; {@code false} otherwise.
     * @throws MarshallingException if object cannot be serialized to byte array
     */
    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(Object obj) {
        return obj instanceof Marshalled &&
            getMarshaller().equals(((Marshalled) obj).getMarshaller()) &&
            Arrays.equals(getBytes(), ((Marshalled) obj).getBytes());
    }

    /**
     * Returns a hash code value for the object that is composed of the hashcode of its
     * {@link #getMarshaller() marshaller} and {@link #getBytes() bytes}.
     *
     * @return a hash code value for this object.
     * @throws MarshallingException if object cannot be serialized to byte array
     */
    @Override
    public int hashCode() {
        return Arrays.hashCode(getBytes()) + getMarshaller().hashCode() * 31;
    }

    /**
     * Returns a compact string representation of the {@link #getObject() object} represented by this marshalled object.
     * This method is designed to be used in logs.
     * If object cannot be deserialized from its byte array or there is an exception in its {@code toString} method,
     * then it returns error message.
     *
     * @return a string representation of the object.
     * @see LogUtil#deepToString(Object)
     */
    @Override
    public String toString() {
        try {
            T object = getObject();
            return LogUtil.deepToString(object);
        } catch (Throwable e) {
            return "Could not unmarshall: " + e;
        }
    }
}
