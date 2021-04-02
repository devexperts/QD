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
package com.devexperts.qd.impl.matrix.management.dump;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.StreamInput;
import com.devexperts.qd.impl.matrix.Collector;
import com.devexperts.qd.impl.matrix.CollectorDebug;
import com.devexperts.qd.impl.matrix.FatalError;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.UnsafeHolder;

import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.InflaterInputStream;

import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.BOOLEAN;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.BYTE;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.CHAR;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.DOUBLE;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.EXCEPTION;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.FLOAT;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.INT;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.LONG;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.OWNER;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.REFERENCE;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.SHORT;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.STRING;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.SYSTEM_PROPERTIES;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.UNKNOWN;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.VERSION;

public class DebugDumpReader {
    private final IndexedSet<Integer, ObjectReader> classMap = IndexedSet.createInt(reader -> reader.classId);
    private final IndexedSet<Integer, ObjectRef> objectMap = IndexedSet.createInt(ref -> ref.id);

    private DebugDumpReader() {
        classMap.put(new ObjectReader(UNKNOWN, null));
        classMap.put(new ObjectReader(STRING, String.class));
        classMap.put(new ObjectReader(BOOLEAN, boolean[].class));
        classMap.put(new ObjectReader(BYTE, byte[].class));
        classMap.put(new ObjectReader(SHORT, short[].class));
        classMap.put(new ObjectReader(CHAR, char[].class));
        classMap.put(new ObjectReader(INT, int[].class));
        classMap.put(new ObjectReader(LONG, long[].class));
        classMap.put(new ObjectReader(FLOAT, float[].class));
        classMap.put(new ObjectReader(DOUBLE, double[].class));
        objectMap.put(new ObjectRef(0));
    }

    private void read(String fileName) {
        System.out.println("Reading " + fileName);
        try {
            try (StreamInput in =
                new StreamInput(new InflaterInputStream(new TrackingInput(new RandomAccessFile(fileName, "r")))))
            {
                while (parse(in)) /* just loop */ ;
            }
        } catch (IOException e) {
            System.out.println("Exception while reading " + fileName);
            e.printStackTrace(System.out);
        }
    }

    private void resolve() {
        System.out.println("Resolving " + objectMap.size() + " objects from " + classMap.size() + " classes");
        objectMap.forEach(ObjectRef::resolveEnums);
        objectMap.forEach(ObjectRef::resolveFields);
        System.out.println("Snapshot reconstructed");
    }

    private boolean parse(StreamInput in) throws IOException {
        int id;
        try {
            id = in.readCompactInt();
        } catch (EOFException e) {
            return false;
        }
        try {
            if (id < 0)
                readClassDesc(in, id);
            else if (id > 0)
                readObjectDesc(in, id);
            else
                throw new IOException("Invalid");
        } catch (IOException e) {
            throw new IOException("Failed to parse id=" + id, e);
        }
        return true;
    }

    private void readClassDesc(StreamInput in, int classId) throws IOException {
        if (classMap.containsKey(classId))
            throw new IOException("Repeated classId=" + classId);
        String className = in.readUTFString();
        int parentClassId = in.readCompactInt();
        if (!classMap.containsKey(parentClassId))
            throw new IOException("Parent class " + parentClassId + " is not found");
        ObjectReader parent = classMap.getByKey(parentClassId);
        if (className == null) {
            ArrayDesc desc = new ArrayDesc(classId, parent.clazz);
            classMap.put(desc);
            return;
        }
        Class<?> clazz;
        try {
            clazz = Class.forName(className);
        } catch (ClassNotFoundException e) {
            System.out.println("Cannot find class " + className);
            clazz = null;
        }
        int fieldCount = in.readCompactInt();
        ClassDesc desc = new ClassDesc(classId, parent, clazz, fieldCount);
        classMap.put(desc);
        for (int i = 0; i < fieldCount; i++) {
            String fieldName = in.readUTFString();
            int fieldType = in.readCompactInt();
            Field field = null;
            try {
                field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
            } catch (Exception e) {
                System.out.println("Cannot find field " + fieldName + " of " + className);
            }
            desc.fields[i] = new FieldDesc(field, fieldType);
            if (fieldType == REFERENCE)
                desc.refCount++;
        }
    }

    private void readObjectDesc(StreamInput in, int objectId) throws IOException {
        if (objectMap.containsKey(objectId))
            throw new IOException("Repeated objectId=" + objectId);
        int classId = in.readCompactInt();
        ObjectReader reader = classMap.getByKey(classId);
        if (reader == null)
            throw new IOException("Invalid classId=" + classId);
        objectMap.put(reader.readObject(in, objectId));
    }

    public void dumpInfo() {
        Object owner = getOwner();
        Object version = getInstanceOrNull(VERSION);
        Object systemProperties = getInstanceOrNull(SYSTEM_PROPERTIES);
        Object exception = getInstanceOrNull(EXCEPTION);

        System.out.println("--- Dump information ---");
        System.out.println("Owner = " + owner);
        System.out.println("QDS   = " + version);
        System.out.println("JVM   = " + ((systemProperties instanceof Map) ? ((Map<?, ?>) systemProperties).get("java.version") : null));
        if (exception instanceof Throwable) {
            System.out.println("Error = " + exception);
            ((Throwable) exception).printStackTrace(System.out);
        }
    }

    public Object getOwner() {
        return getInstanceOrNull(OWNER);
    }

    public CollectorDebug.RehashCrashInfo getRehashCrashInfo() {
        final CollectorDebug.RehashCrashInfo rci = new CollectorDebug.RehashCrashInfo();
        Object exception = getInstanceOrNull(EXCEPTION);
        if (exception instanceof FatalError) {
            String message = ((FatalError) exception).getMessage();
            Matcher matcher = Pattern.compile("^Counter underflow for key=(\\d+),.*").matcher(message);
            if (matcher.matches()) {
                rci.agent = 1; // support only total subscription rehash crash
                rci.key = Integer.parseInt(matcher.group(1));
                System.out.println("Rehash crash detected at key " + rci.key);
            }
        }
        return rci;
    }

    public void visit(Object owner, CollectorVisitor visitor) {
        if (owner instanceof Collection) {
            for (Object o : (Collection<?>) owner)
                visit(o, visitor);
        } else if (owner instanceof Collector)
            visitor.visit((Collector) owner);
    }

    private Object getInstanceOrNull(int objectId) {
        ObjectRef ref = objectMap.getByKey(objectId);
        return ref == null ? null : ref.instance;
    }

    private Object getInstanceOrClass(int objectId) {
        if (objectId >= 0) {
            ObjectRef ref = objectMap.getByKey(objectId);
            if (ref == null) {
                System.out.println("Cannot find object instance #" + objectId);
                return null;
            }
            return ref.instance;
        } else {
            ObjectReader reader = classMap.getByKey(objectId);
            if (reader == null) {
                System.out.println("Cannot find class #" + objectId);
                return null;
            }
            return reader.clazz;
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Usage: DebugDumpReader <file> [<command> [<args>]] [+ <command> ...]");
            return;
        }
        DebugDumpReader reader = new DebugDumpReader();
        reader.read(args[0]);
        reader.resolve();
        DebugDumpCLI cli = new DebugDumpCLI(reader);
        if (args.length == 1)
            cli.interactive();
        else
            cli.execute(Arrays.copyOfRange(args, 1, args.length));
    }

    static class ObjectRef {
        final int id;
        Object instance;

        ObjectRef(int id) {
            this.id = id;
        }

        ObjectRef(int id, Object instance) {
            this.id = id;
            this.instance = instance;
        }

        void resolveEnums() {}
        void resolveFields() {}
    }

    class ArrayRef extends ObjectRef {
        int[] refs;

        ArrayRef(int id, Class<?> componentType, int length) {
            super(id);
            instance = Array.newInstance(componentType, length);
            refs = new int[length];
        }

        @Override
        void resolveFields() {
            Object[] a = (Object[]) instance;
            for (int i = 0; i < refs.length; i++) {
                int ref = refs[i];
                Object value = getInstanceOrClass(ref);
                try {
                    a[i] = value;
                } catch (ArrayStoreException e) {
                    System.out.println("Cannot store element " + i + " of array #" + id + " " +
                        a.getClass().getComponentType().getName() + "[]" +
                        " to reference #" + ref + " " + value.getClass().getName());
                }
            }
        }

    }

    class InstanceRef extends ObjectRef {
        int[] refs;
        final ClassDesc classDesc;

        InstanceRef(int id, Object instance, ClassDesc classDesc) {
            super(id, instance);
            refs = new int[classDesc.refCount];
            this.classDesc = classDesc;
        }

        void resolveEnums() {
            if (instance instanceof Enum<?>) {
                resolveFields();
                instance = resolveEnum((Enum<?>) instance);
            }
        }

        @Override
        void resolveFields() {
            int i = 0;
            ClassDesc cur = classDesc;
            do {
                for (FieldDesc fd : cur.fields)
                    if (fd.type == REFERENCE && fd.field != null) {
                        int ref = refs[i++];
                        Object value = getInstanceOrClass(ref);
                        try {
                            if (fd.field.getType().isInstance(value))
                                fd.field.set(instance, value);
                        } catch (Exception e) {
                            System.out.println("Cannot set field " + fd.field.getName() + " on object #" + id + " " +
                                fd.field.getDeclaringClass().getName() +
                                " to reference #" + ref + " " + value.getClass().getName());
                        }
                    }
                cur = cur.parent;
            } while (cur != null);
        }

        private Object resolveEnum(Enum<?> instance) {
            try {
                return Enum.valueOf(instance.getClass(), instance.name());
            } catch (IllegalArgumentException e) {
                System.out.println("Cannot resolveFields enum " + instance.getClass().getName() + " with name " + instance.name());
                return instance;
            }
        }
    }

    static class ObjectReader {
        final int classId;
        final Class<?> clazz;

        ObjectReader(int classId, Class<?> clazz) {
            this.classId = classId;
            this.clazz = clazz;
        }

        ObjectRef readObject(BufferedInput in, int objectId) throws IOException {
            if (classId == UNKNOWN) {
                in.readUTFString(); // skip
                return new ObjectRef(objectId);
            }
            if (classId == STRING)
                return new ObjectRef(objectId, in.readUTFString());
            int length = in.readCompactInt();
            switch (classId) {
            case BOOLEAN:
                boolean[] booleanArr = new boolean[length];
                for (int i = 0; i < length; i++)
                    booleanArr[i] = in.readByte() != 0;
                return new ObjectRef(objectId, booleanArr);
            case BYTE:
                byte[] byteArr = new byte[length];
                for (int i = 0; i < length; i++)
                    byteArr[i] = in.readByte();
                return new ObjectRef(objectId, byteArr);
            case SHORT:
                short[] shortArr = new short[length];
                for (int i = 0; i < length; i++)
                    shortArr[i] = (short) in.readCompactInt();
                return new ObjectRef(objectId, shortArr);
            case CHAR:
                char[] charArr = new char[length];
                for (int i = 0; i < length; i++)
                    charArr[i] = (char) in.readUTFChar();
                return new ObjectRef(objectId, charArr);
            case INT:
                int[] intArr = new int[length];
                for (int i = 0; i < length; i++)
                    intArr[i] = in.readCompactInt();
                return new ObjectRef(objectId, intArr);
            case LONG:
                long[] longArr = new long[length];
                for (int i = 0; i < length; i++)
                    longArr[i] = in.readCompactLong();
                return new ObjectRef(objectId, longArr);
            case FLOAT:
                float[] floatArr = new float[length];
                for (int i = 0; i < length; i++)
                    floatArr[i] = in.readFloat();
                return new ObjectRef(objectId, floatArr);
            case DOUBLE:
                double[] doubleArr = new double[length];
                for (int i = 0; i < length; i++)
                    doubleArr[i] = in.readFloat();
                return new ObjectRef(objectId, doubleArr);
            default:
                throw new IllegalArgumentException("classId=" + classId);
            }
        }
    }

    class ArrayDesc extends ObjectReader {
        ArrayDesc(int classId, Class<?> parent) {
            super(classId, parent == null ? Object[].class : Array.newInstance(parent, 0).getClass());
        }

        @Override
        ObjectRef readObject(BufferedInput in, int objectId) throws IOException {
            int length = in.readCompactInt();
            ArrayRef result = new ArrayRef(objectId, clazz.getComponentType(), length);
            for (int i = 0; i < length; i++)
                result.refs[i] = in.readCompactInt();
            return result;
        }
    }

    static class FieldDesc {
        final Field field;
        final int type;

        FieldDesc(Field field, int type) {
            this.field = field;
            this.type = type;
        }

    }

    class ClassDesc extends ObjectReader {
        final ClassDesc parent;
        final FieldDesc[] fields;
        int refCount;

        ClassDesc(int classId, ObjectReader parent, Class<?> clazz, int fieldCount) {
            super(classId, clazz);
            this.parent = parent instanceof ClassDesc ? (ClassDesc) parent : null;
            fields = new FieldDesc[fieldCount];
            refCount = this.parent == null ? 0 : this.parent.refCount;
        }

        @Override
        ObjectRef readObject(BufferedInput in, int objectId) throws IOException {
            if (clazz == null || Modifier.isAbstract(clazz.getModifiers())) {
                skipInstanceFields(in);
                return new ObjectRef(objectId);
            }
            Object instance;
            try {
                instance = UnsafeHolder.UNSAFE.allocateInstance(clazz);
            } catch (InstantiationException e) {
                throw new IOException("Cannot allocate instance " + objectId + " of " + clazz.getName(), e);
            }
            InstanceRef result = readInstanceFields(in, new InstanceRef(objectId, instance, this));
            return result;
        }

        private InstanceRef readInstanceFields(BufferedInput in, InstanceRef result) throws IOException {
            ClassDesc cur = this;
            Object instance = result.instance;
            int i = 0;
            do {
                for (FieldDesc fd : cur.fields)
                    try {
                        switch (fd.type) {
                        case REFERENCE:
                            result.refs[i++] = in.readCompactInt();
                            break;
                        case BOOLEAN:
                            boolean booleanValue = in.readByte() != 0;
                            if (fd.field != null)
                                fd.field.setBoolean(instance, booleanValue);
                            break;
                        case BYTE:
                            byte byteValue = in.readByte();
                            if (fd.field != null)
                                fd.field.setByte(instance, byteValue);
                            break;
                        case SHORT:
                            short shortValue = (short) in.readCompactInt();
                            if (fd.field != null)
                                fd.field.setShort(instance, shortValue);
                            break;
                        case CHAR:
                            char charValue = (char) in.readUTFChar();
                            if (fd.field != null)
                                fd.field.setChar(instance, charValue);
                            break;
                        case INT:
                            int intValue = in.readCompactInt();
                            if (fd.field != null)
                                fd.field.setInt(instance, intValue);
                            break;
                        case LONG:
                            long longValue = in.readCompactLong();
                            if (fd.field != null)
                                fd.field.setLong(instance, longValue);
                            break;
                        case FLOAT:
                            float floatValue = in.readFloat();
                            if (fd.field != null)
                                fd.field.setFloat(instance, floatValue);
                            break;
                        case DOUBLE:
                            double doubleValue = in.readDouble();
                            if (fd.field != null)
                                fd.field.setDouble(instance, doubleValue);
                            break;
                        default:
                            throw new IOException("Invalid field type " + fd.type);
                        }
                    } catch (IOException e) {
                        throw e;
                    } catch (Exception e) {
                        System.out.println("Cannot set field " + fd.field.getName() + " on " + fd.field.getDeclaringClass().getName() + ": " + e);
                    }
                cur = cur.parent;
            } while (cur != null);
            return result;
        }

        private void skipInstanceFields(BufferedInput in) throws IOException {
            ClassDesc cur = this;
            do {
                for (FieldDesc fd : cur.fields)
                    switch (fd.type) {
                    case REFERENCE:
                        in.readCompactInt();
                        break;
                    case BOOLEAN:
                        in.readByte();
                        break;
                    case BYTE:
                        in.readByte();
                        break;
                    case SHORT:
                        in.readCompactInt();
                        break;
                    case CHAR:
                        in.readUTFChar();
                        break;
                    case INT:
                        in.readCompactInt();
                        break;
                    case LONG:
                        in.readCompactLong();
                        break;
                    case FLOAT:
                        in.readFloat();
                        break;
                    case DOUBLE:
                        in.readDouble();
                        break;
                    default:
                        throw new IOException("Invalid field type " + fd.type);
                    }
                cur = cur.parent;
            } while (cur != null);
        }
    }
}
