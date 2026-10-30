/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.io;

import com.devexperts.util.LockFreePool;
import com.devexperts.util.SystemProperties;

import java.io.DataOutput;
import java.io.IOException;
import java.io.NotSerializableException;

/**
 * The utility class that writes the body of one object, or the bodies of several objects with their declared types,
 * and compresses the result as appropriate.
 * It uses pooling for better efficiency.
 */
class ObjectSerializer {
    // ========== pooling implementation ==========

    static final int MAX_POOLED_BUFFER =
        SystemProperties.getIntProperty(ObjectSerializer.class, "maxPooledBuffer", 16384);

    static final LockFreePool<ObjectSerializer> POOL =
        new LockFreePool<>(ObjectSerializer.class.getName(), 16);

    private static ObjectSerializer allocate() throws IOException {
        ObjectSerializer os = POOL.poll();
        ObjectSerializationWriterFactory factory = ObjectSerializationFactories.instance().getDefault();
        if (os == null || os.factory != factory) {
            os = new ObjectSerializer(factory);
        }
        return os;
    }

    private static void release(ObjectSerializer os) {
        // if serialized object was too large then drop OOS cause it has large hash-tables inside.
        if (os.bao.getPosition() > MAX_POOLED_BUFFER)
            return;
        // try to reset the writer before releasing to pool
        try {
            if (!os.writer.reset())
                return;
        } catch (RuntimeException e) {
            return;
        }
        // return to pool
        os.bao.clear();
        POOL.offer(os);
    }

    // ========== static package-private interface methods ==========

    static byte[] toBytes(Object object) throws IOException {
        if (object == null)
            return null;
        if (object instanceof Marshalled) {
            Marshalled<?> marshalled = (Marshalled<?>) object;
            if (!(marshalled.getMarshaller() instanceof SerializationMarshallerImpl))
                throw new NotSerializableException(Marshalled.class.getName());
            try {
                return marshalled.getBytes();
            } catch (MarshallingException e) {
                throw e.getCause();
            }
        }
        ObjectSerializer os = allocate();
        byte[] bytes = os.toBytesImpl(object);
        release(os);
        return bytes;
    }

    // writes compact int size and object body
    static void writeCompact(DataOutput out, Object object) throws IOException {
        if (object == null) {
            IOUtil.writeCompactInt(out, -1);
            return;
        }
        if (object instanceof Marshalled) {
            writeMarshalled(out, (Marshalled<?>) object);
            return;
        }
        ObjectSerializer os = allocate();
        os.writeCompactImpl(out, object);
        release(os);
    }

    private static void writeMarshalled(DataOutput out, Marshalled<?> marshalled) throws IOException {
        if (!(marshalled.getMarshaller() instanceof SerializationMarshallerImpl))
            throw new NotSerializableException(Marshalled.class.getName());
        try {
            IOUtil.writeByteArray(out, marshalled.getBytes());
        } catch (MarshallingException e) {
            throw e.getCause();
        }
    }

    // writes object body only
    static void writeBody(BufferedOutput out, Object object) throws IOException {
        if (object instanceof Marshalled) {
            writeMarshalled(out, (Marshalled<?>) object);
            return;
        }
        ObjectSerializer os = allocate();
        os.writeBodyImpl(out, object);
        release(os);
    }

    private static void writeMarshalled(BufferedOutput out, Marshalled<?> marshalled) throws IOException {
        if (!(marshalled.getMarshaller() instanceof SerializationMarshallerImpl))
            throw new NotSerializableException(Marshalled.class.getName());
        try {
            out.write(marshalled.getBytes());
        } catch (MarshallingException e) {
            throw e.getCause();
        }
    }

    // writes object bodies only
    static void writeBodiesWithTypes(BufferedOutput out, Class<?>[] types, Object... objects) throws IOException {
        if (types.length != objects.length)
            throw new IOException("Number of objects does not match number of types");
        ObjectSerializer os = allocate();
        os.writeBodiesWithTypesImpl(out, types, objects);
        release(os);
    }

    // ========== Instance Implementation ==========

    private final ByteArrayOutput bao; // pooled buffer is allocated inside this object
    private final ObjectSerializationWriterFactory factory;
    private final ObjectSerializationWriter writer;

    private ObjectSerializer(ObjectSerializationWriterFactory factory) throws IOException {
        bao = new ByteArrayOutput(MAX_POOLED_BUFFER);
        this.factory = factory;
        writer = factory.createWriter(bao);
    }

    private byte[] toBytesImpl(Object object) throws IOException {
        writer.start();
        writer.write(object);
        writer.flush();
        return compressResultToBytes();
    }

    private byte[] compressResultToBytes() {
        if (Compression.shallCompress(bao.getBuffer(), 0, bao.getPosition())) {
            int pos = bao.getPosition();
            Compression.deflate(bao.getBuffer(), 0, pos, 1, bao);
            // Use compressed block only if compression ratio is better than 75% - otherwise use original.
            if (bao.getPosition() - pos < pos - (pos >> 2)) {
                byte[] b = new byte[bao.getPosition() - pos];
                System.arraycopy(bao.getBuffer(), pos, b, 0, b.length);
                bao.setPosition(pos);
                return b;
            }
            bao.setPosition(pos);
        }
        return bao.toByteArray();
    }

    // writes compact int size and object body
    private void writeCompactImpl(DataOutput out, Object object) throws IOException {
        writer.start();
        writer.write(object);
        writer.flush();
        if (Compression.shallCompress(bao.getBuffer(), 0, bao.getPosition())) {
            if (tryCompressWithSize(out))
                return;
        }
        IOUtil.writeCompactInt(out, bao.getPosition());
        out.write(bao.getBuffer(), 0, bao.getPosition());
    }

    private boolean tryCompressWithSize(DataOutput out) throws IOException {
        int pos = bao.getPosition();
        Compression.deflate(bao.getBuffer(), 0, pos, 1, bao);
        // Use compressed block only if compression ratio is better than 75% - otherwise use original.
        if (bao.getPosition() - pos < pos - (pos >> 2)) {
            IOUtil.writeCompactInt(out, bao.getPosition() - pos);
            out.write(bao.getBuffer(), pos, bao.getPosition() - pos);
            bao.setPosition(pos);
            return true;
        }
        bao.setPosition(pos);
        return false;
    }

    // writes object body only
    private void writeBodyImpl(BufferedOutput out, Object object) throws IOException {
        writer.start();
        writer.write(object);
        writer.flush();
        compressAndWriteResult(out);
    }

    // writes object bodies only
    private void writeBodiesWithTypesImpl(BufferedOutput out, Class<?>[] types, Object[] objects) throws IOException {
        bao.writeInt(ObjectIOImplUtil.getDeclaredTypesSignature(types));
        boolean objectsPresent = false;
        for (int i = 0; i < types.length; i++) {
            if (CompactSerializer.isCompact(types[i])) {
                CompactSerializer.writeCompact(bao, types[i], objects[i]);
            } else {
                objectsPresent = true;
            }
        }
        if (objectsPresent) {
            writer.start();
            for (int i = 0; i < types.length; i++) {
                if (!CompactSerializer.isCompact(types[i])) {
                    Object object = objects[i];
                    if (object instanceof Marshalled) {
                        try {
                            object = ((Marshalled<?>) object).getObject();
                        } catch (MarshallingException e) {
                            throw e.getCause();
                        }
                    }
                    if (object != null && !types[i].isInstance(object))
                        throw new IOException("Invalid type " + object.getClass().getName() + ", expected " + types[i]);
                    writer.write(object);
                }
            }
            writer.flush();
        }
        compressAndWriteResult(out);
    }

    private void compressAndWriteResult(BufferedOutput out) throws IOException {
        if (Compression.shallCompress(bao.getBuffer(), 0, bao.getPosition())) {
            int pos = bao.getPosition();
            Compression.deflate(bao.getBuffer(), 0, pos, 1, bao);
            // Use compressed block only if compression ratio is better than 75% - otherwise use original.
            if (bao.getPosition() - pos < pos - (pos >> 2)) {
                out.write(bao.getBuffer(), pos, bao.getPosition() - pos);
                bao.setPosition(pos);
                return;
            }
            bao.setPosition(pos);
        }
        out.write(bao.getBuffer(), 0, bao.getPosition());
    }
}
