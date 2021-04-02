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

import com.devexperts.logging.Logging;
import com.devexperts.util.LockFreePool;
import com.devexperts.util.SystemProperties;

import java.io.DataInput;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.StreamCorruptedException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.zip.DataFormatException;

/**
 * The utility class that provides methods for object deserialization.
 * It uses pooling of {@link ObjectInputStream} for better efficiency.
 * It provides better resolution of classes using specified and context class loaders.
 */
class ObjectDeserializer extends ObjectInputStream {
    private static final Logging log = Logging.getLogging(ObjectDeserializer.class);

    private static final boolean DUMP_ERRORS =
        SystemProperties.getBooleanProperty(ObjectDeserializer.class, "dumpErrors", false);
    private static final int MAX_BYTES_TO_LOG =
        SystemProperties.getIntProperty(ObjectDeserializer.class, "maxBytesToLog", 1024);

    private static final char[] DIGITS = "0123456789ABCDEF".toCharArray();

    // ========== pooling implementation ==========

    private static final int MAX_POOLED_BUFFER =
        SystemProperties.getIntProperty(ObjectDeserializer.class, "maxPooledBuffer", 16384);

    private static final LockFreePool<ObjectDeserializer> POOL =
        new LockFreePool<>(ObjectDeserializer.class.getName(), 16);

    private static ObjectDeserializer allocate(SerialClassContext serialContext) throws IOException {
        ObjectDeserializer od = POOL.poll();
        if (od == null)
            od = new ObjectDeserializer(new ByteArrayInput());
        od.serialContext = Objects.requireNonNull(serialContext);
        return od;
    }

    private static void release(ObjectDeserializer od) throws IOException {
        // return to pool only deserializers which successfully (no exceptions) read all data and reset stream.
        if (od.bai.getPosition() != od.bai.getLimit())
            throw new IOException("Some input data was not deserialized");
        // if serialized object was too large then drop OIS cause it has large hash-tables inside.
        if (od.bai.getPosition() > MAX_POOLED_BUFFER)
            return;
        // try to reset stream before releasing to pool
        od.bai.setBuffer(ObjectIOImplUtil.STREAM_RESET);
        try {
            if (od.readLong() != ObjectIOImplUtil.RESET_MAGIC)
                return;
        } catch (IOException e) {
            return;
        }
        if (od.bai.getPosition() != od.bai.getLimit())
            return;
        // return to pool
        od.bai.setBuffer(null);
        od.bao.setBuffer(null);
        od.dumpBytes = null;
        od.serialContext = null;
        POOL.offer(od);
    }

    // ========== static package-private interface methods ==========

    static Object toObject(byte[] bytes, SerialClassContext serialContext) throws IOException {
        if (bytes == null)
            return null;
        ObjectDeserializer od = allocate(serialContext);
        try {
            od.decompressAndSetInput(bytes, 0, bytes.length);
            od.checkObjectHeader();
            Object object;
            try {
                object = od.readObject();
            } catch (ClassNotFoundException e) {
                throw new InvalidClassException("Class not found: " + e.getMessage());
            }
            release(od); // Release only after successful read to the last byte.
            return object;
        } catch (Throwable t) {
            od.dumpError(null, t);
            return rethrowIOException(t);
        }
    }

    static Object readCompact(DataInput in, SerialClassContext serialContext) throws IOException {
        long length = IOUtil.readCompactLong(in);
        if (length < -1 || length > Integer.MAX_VALUE)
            throw new IOException("Illegal length: " + length);
        if (length == -1)
            return null;
        return readBody(in, (int) length, serialContext);
    }

    static Object readBody(DataInput in, int length, SerialClassContext serialContext) throws IOException {
        ObjectDeserializer od = allocate(serialContext);
        try {
            od.decompressAndSetInput(in, length);
            od.checkObjectHeader();
            Object object;
            try {
                object = od.readObject();
            } catch (ClassNotFoundException e) {
                throw new InvalidClassException("Class not found: " + e.getMessage());
            }
            release(od); // Release only after successful read to the last byte.
            return object;
        } catch (Throwable t) {
            od.dumpError(null, t);
            return rethrowIOException(t);
        }
    }

    @SuppressWarnings("rawtypes")
    static Object[] readBodiesWithTypes(DataInput in, int length, Class<?>[] types, SerialClassContext serialContext) throws IOException {
        ObjectDeserializer od = allocate(serialContext);
        try {
            od.decompressAndSetInput(in, length);
            int expectedSignature = ObjectIOImplUtil.getDeclaredTypesSignature(types);
            int actualSignature = od.bai.readInt();
            if (actualSignature != expectedSignature)
                throw new IOException(actualSignature >>> 24 != expectedSignature >>> 24 ?
                    "Not a declared types stream." :
                    "Wrong declared types signature, expected: " + Integer.toHexString(expectedSignature) +
                        ", got " + Integer.toHexString(actualSignature));
            Object[] objects = new Object[types.length];
            boolean objectsPresent = false;
            for (int i = 0; i < types.length; i++)
                if (CompactSerializer.isCompact(types[i]))
                    objects[i] = CompactSerializer.readCompact(od.bai, types[i]);
                else
                    objectsPresent = true;
            if (objectsPresent) {
                od.checkObjectHeader();
                for (int i = 0; i < types.length; i++)
                    if (!CompactSerializer.isCompact(types[i]))
                        try {
                            objects[i] = od.readObject();
                            if (objects[i] != null && !types[i].isInstance(objects[i]))
                                throw new InvalidClassException("Invalid type " + objects[i].getClass().getName() + ", expected " + types[i].getName());
                        } catch (ClassNotFoundException e) {
                            throw new InvalidClassException("Class not found: " + e.getMessage()).initCause(e);
                        }
            }
            release(od); // Release only after successful read to the last byte.
            return objects;
        } catch (Throwable t) {
            od.dumpError(types, t);
            return rethrowIOException(t);
        }
    }

    // this method never returns actually
    private static Object[] rethrowIOException(Throwable t) throws IOException {
        if (t instanceof RuntimeException)
            throw (RuntimeException) t;
        else if (t instanceof Error)
            throw (Error) t;
        else if (t instanceof IOException)
            throw (IOException) t;
        else
            throw new RuntimeException("Unexpected exception", t);
    }

    // ========== Instance Implementation ==========

    private final ByteArrayInput bai;
    private final ByteArrayOutput bao;
    private byte[] pooledBuffer;

    private byte[] dumpBytes;
    private int dumpOffset;
    private int dumpLength;
    private SerialClassContext serialContext;

    private ObjectDeserializer(ByteArrayInput bai) throws IOException {
        super(bai);
        this.bai = bai;
        this.bao = new ByteArrayOutput();
    }

    private byte[] readFullyIntoBuffer(DataInput in, int length) throws IOException {
        byte[] bytes;
        if (length <= MAX_POOLED_BUFFER) {
            if (pooledBuffer == null)
                pooledBuffer = new byte[MAX_POOLED_BUFFER];
            bytes = pooledBuffer;
        } else
            bytes = new byte[length];
        in.readFully(bytes, 0, length);
        return bytes;
    }

    private void decompressAndSetInput(DataInput in, int length) throws IOException {
        if (in instanceof BufferedInput && ((BufferedInput) in).readToCaptureBytes(this::decompressAndSetInput, length))
            return;
        decompressAndSetInput(readFullyIntoBuffer(in, length), 0, length);
    }

    private void decompressAndSetInput(byte[] bytes, int offset, int length) {
        dumpBytes = bytes;
        dumpOffset = offset;
        dumpLength = length;
        // * it is possible that (bytes == pooledBuffer) so care is taken not to override bytes
        // * for performance we try to decompress in same buffer, possibly pooledBuffer
        // * recursive compression should not happen, dunno why it is written this way
        try {
            while (Compression.isCompressed(bytes, offset, length)) {
                if (pooledBuffer == null)
                    pooledBuffer = new byte[MAX_POOLED_BUFFER];
                if (bao.getLimit() == 0)
                    bao.setBuffer(pooledBuffer);
                if (bao.getBuffer() == bytes)
                    bao.setPosition(offset + length);
                int pos = bao.getPosition();
                Compression.inflate(bytes, offset, length, bao);
                bytes = bao.getBuffer();
                offset = pos;
                length = bao.getPosition() - pos;
            }
        } catch (DataFormatException e) {
            // This exception is treated as indication that bytes were not really compressed.
        }
        bai.setInput(bytes, offset, length);
    }

    private void checkObjectHeader() throws IOException {
        for (byte b : ObjectIOImplUtil.STREAM_HEADER) {
            if (bai.readByte() != b)
                throw new StreamCorruptedException("invalid stream header");
        }
    }

    @SuppressWarnings("rawtypes")
    private void dumpError(Class[] types, Throwable thrown) {
        if (!DUMP_ERRORS)
            return;
        try {
            StringBuilder message = new StringBuilder("An error has occurred while deserializing object:\n");
            if (types != null)
                message.append("  Object types: ").append(Arrays.toString(types)).append("\n");
            ClassLoader cl = serialContext.getClassLoader();
            if (cl != null)
                message.append("  Classloader: ").append(cl).append('\n');
            if (dumpLength <= MAX_BYTES_TO_LOG) {
                message.append("  Bytes: [");
                for (int i = 0; i < dumpLength; i++) {
                    byte b = dumpBytes[dumpOffset + i];
                    message.append(DIGITS[(b >> 4) & 0xF]).append(DIGITS[b & 0xF]).append(' ');
                }
                message.setCharAt(message.length() - 1, ']');
            } else {
                String fileName = "deserialization-" + new SimpleDateFormat("yyyyMMdd-HHmmss.SSS").format(new Date()) + ".dump";
                try {
                    try (FileOutputStream outputStream = new FileOutputStream(fileName)) {
                        outputStream.write(dumpBytes, dumpOffset, dumpLength);
                    }
                    message.append("  Bytes were dumped into ").append(fileName);
                } catch (Throwable t) {
                    message.append("  Failed to dump bytes into ").append(fileName).append(" because of ").append(t.toString());
                }
            }
            log.error(message.toString(), thrown);
        } catch (Throwable t) {
            // ignored
        }
    }

    @Override
    protected void readStreamHeader() throws IOException {
        // There is no header available during instantiation of this stream. Skip header check.
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected Class resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
        if (ClassUtil.isPrimitiveType(desc.getName()))
            return ClassUtil.getTypeClass(desc.getName(), null);
        try {
            serialContext.check(desc.getName());
        } catch (ClassNotFoundException e) {
            throw new InvalidClassException(e.getMessage());
        }
        try {
            return ClassUtil.getTypeClass(desc.getName(), serialContext.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            // try super implementation
        }
        return super.resolveClass(desc);
    }
}
