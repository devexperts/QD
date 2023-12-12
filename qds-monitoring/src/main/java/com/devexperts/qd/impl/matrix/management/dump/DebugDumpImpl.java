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
package com.devexperts.qd.impl.matrix.management.dump;

import com.devexperts.io.BufferedOutput;
import com.devexperts.io.StreamOutput;
import com.devexperts.logging.Logging;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.impl.matrix.management.DebugDump;
import com.devexperts.qd.impl.matrix.management.impl.Exec;
import com.devexperts.util.LogUtil;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;

import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.BOOLEAN;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.BYTE;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.CHAR;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.DOUBLE;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.EXCEPTION;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.FIRST_CLASS_ID;
import static com.devexperts.qd.impl.matrix.management.dump.DebugDumpConst.FIRST_OBJECT_ID;
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

public class DebugDumpImpl implements DebugDump {
    private static final Logging log = Logging.getLogging(DebugDumpImpl.class);

    private static final Pattern DUMP_CLASS_PATTERN = Pattern.compile(
        "com\\.devexperts\\.qd\\.impl\\..*|" +  // QD core implementation classes
        "com\\.devexperts\\.qd\\.kit\\..*|" +   // records, schemes, fields
        "com\\.devexperts\\.qd\\.ng\\..*|" +    // record buffers, etc
        "com\\.devexperts\\.qd\\.stats\\..*|" + // QD stats
        "com\\.devexperts\\.qd\\.util\\..*|" +  // QD utility classes
        "com\\.devexperts\\.qd\\.[^.]*|" +      // QD core classes
        "com\\.devexperts\\.util\\..*|" +       // dxlib utility classes (TimeDistribution is used, IndexedSet maybe too)
        "java\\..*"                             // all java utilities (java.lang.Thread will be excluded)
    );

    private static final Pattern EXCLUDE_CLASS_PATTERN = Pattern.compile(
        "java\\.lang\\.Thread|.*\\$\\$Lambda\\$.*");

    private int nextClassId = FIRST_CLASS_ID;
    private int nextObjectId = FIRST_OBJECT_ID;

    private final IdentityHashMap<Class, ObjectWriter> classMap = new IdentityHashMap<Class, ObjectWriter>();
    private final Queue<ObjectWriter> classQueue = new LinkedList<ObjectWriter>();

    private final IdentityHashMap<Object, Integer> objectMap = new IdentityHashMap<Object, Integer>();
    private final Queue<Object> objectQueue = new LinkedList<Object>();

    public DebugDumpImpl() {
        classMap.put(boolean[].class, new ObjectWriter(BOOLEAN));
        classMap.put(byte[].class, new ObjectWriter(BYTE));
        classMap.put(short[].class, new ObjectWriter(SHORT));
        classMap.put(char[].class, new ObjectWriter(CHAR));
        classMap.put(int[].class, new ObjectWriter(INT));
        classMap.put(long[].class, new ObjectWriter(LONG));
        classMap.put(float[].class, new ObjectWriter(FLOAT));
        classMap.put(double[].class, new ObjectWriter(DOUBLE));
        classMap.put(String.class, new ObjectWriter(STRING));
    }

    public static void makeDump(final String file, final Object owner) {
        Exec.EXEC.execute(new Runnable() {
            public void run() {
                try {
                    new DebugDumpImpl().makeDump(file, owner, null);
                } catch (Throwable t) {
                    log.error("Failed to dump to " + LogUtil.hideCredentials(file), t);
                }
            }
        });
    }

    public void makeDump(String file, Object owner, Throwable t) throws IOException {
        log.info("Dumping objects in internal format to '" + LogUtil.hideCredentials(file));
        putObject(owner, OWNER);
        putObject(QDFactory.getVersion(), VERSION);
        try {
            putObject(System.getProperties(), SYSTEM_PROPERTIES);
        } catch (SecurityException e) {
            // ignore
        }
        if (t != null)
            t.getStackTrace(); // ensure stack trace if there
        putObject(t, EXCEPTION);
        dump(file);
        log.info("Debug dump completed");
    }

    private void dump(String file) throws IOException {
        StreamOutput out = new StreamOutput(new DeflaterOutputStream(new FileOutputStream(file)));
        try {
            dump(out);
        } finally {
            out.close();
        }
    }

    private void dump(BufferedOutput out) throws IOException {
        int cnt = 0;
        while (!objectQueue.isEmpty()) {
            dumpObject(out, objectQueue.poll());
            cnt++;
            processClassQueue(out);
        }
        log.info("Dumping done, written " + cnt + " object instances");
    }

    private void processClassQueue(BufferedOutput out) throws IOException {
        while (!classQueue.isEmpty())
            classQueue.poll().writeClass(out);
    }

    private ObjectWriter defineClass(BufferedOutput out, Class clazz) throws IOException {
        if (clazz == null)
            return null;
        if (classMap.containsKey(clazz))
            return classMap.get(clazz);
        if (clazz.isArray())
            return defineArrayClass(out, clazz);
        String name = clazz.getName();
        if (!DUMP_CLASS_PATTERN.matcher(name).matches() || EXCLUDE_CLASS_PATTERN.matcher(name).matches()) {
            log.info("Skipped class " + name);
            ObjectWriter result = defineClass(out, clazz.getSuperclass());
            classMap.put(clazz, result);
            return result;
        }
        return defineRegularClass(out, clazz);
    }

    private ObjectWriter defineArrayClass(BufferedOutput out, Class clazz) throws IOException {
        ObjectWriter component = defineClass(out, clazz.getComponentType());
        int classId = nextClassId--;
        log.info("Dumping array description " + classId + " " + clazz.getName());
        ArrayDesc desc = new ArrayDesc(classId, component);
        classMap.put(clazz, desc);
        classQueue.add(desc);
        return desc;
    }

    private ObjectWriter defineRegularClass(BufferedOutput out, Class clazz) throws IOException {
        ClassDesc parent = (ClassDesc) defineClass(out, clazz.getSuperclass());

        int classId = nextClassId--;
        log.info("Dumping class description " + classId + " " + clazz.getName());
        ClassDesc desc = new ClassDesc(classId, clazz.getName(), parent);
        classMap.put(clazz, desc);

        for (Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()))
                continue;
            if (f.getAnnotation(DebugDumpExclude.class) != null)
                continue;
            f.setAccessible(true);
            int type = fieldClassToSerialType(f.getType());
            desc.fields.add(new FieldDesc(f, type));
        }

        classQueue.add(desc);
        return desc;
    }

    private void dumpObject(BufferedOutput out, Object obj) throws IOException {
        Class clazz = obj.getClass();
        ObjectWriter writer = defineClass(out, clazz);
        processClassQueue(out);
        out.writeCompactInt(objectMap.get(obj));
        if (writer == null) {
            out.writeCompactInt(UNKNOWN);
            out.writeUTFString(obj.toString());
        } else {
            out.writeCompactInt(writer.classId);
            writer.writeObject(out, obj);
        }
    }

    private int fieldClassToSerialType(Class clazz) {
        if (clazz == boolean.class)
            return BOOLEAN;
        else if (clazz == byte.class)
            return BYTE;
        else if (clazz == short.class)
            return SHORT;
        else if (clazz == char.class)
            return CHAR;
        else if (clazz == int.class)
            return INT;
        else if (clazz == long.class)
            return LONG;
        else if (clazz == float.class)
            return FLOAT;
        else if (clazz == double.class)
            return DOUBLE;
        else
            return REFERENCE;
    }

    private void writeTypedField(BufferedOutput out, int type, Object value) throws IOException {
        switch (type) {
        case REFERENCE:
            writeObjectReference(out, value);
            break;
        case BOOLEAN:
            out.writeByte(value == null ? 0 : (Boolean) value ? 1 : 0);
            break;
        case BYTE:
            out.writeByte(value == null ? 0 : (Byte) value);
            break;
        case SHORT:
            out.writeCompactInt(value == null ? 0 : (Short) value);
            break;
        case CHAR:
            out.writeUTFChar(value == null ? 0 : (Character) value);
            break;
        case INT:
            out.writeCompactInt(value == null ? 0 : (Integer) value);
            break;
        case LONG:
            out.writeCompactLong(value == null ? 0 : (Long) value);
            break;
        case FLOAT:
            out.writeFloat(value == null ? 0 : (Float) value);
            break;
        case DOUBLE:
            out.writeDouble(value == null ? 0 : (Double) value);
            break;
        }
    }

    private void writeObjectReference(BufferedOutput out, Object value) throws IOException {
        if (value instanceof Class) {
            ObjectWriter writer = defineClass(out, (Class) value);
            out.writeCompactInt(writer == null ? 0 : writer.classId);
        } else
            out.writeCompactInt(addObject(value));
    }

    public void putObject(Object obj, int objectId) {
        if (obj == null)
            return;
        objectMap.put(obj, objectId);
        objectQueue.add(obj);
    }

    private int addObject(Object obj) {
        if (obj == null)
            return 0;
        Integer id = objectMap.get(obj);
        if (id != null)
            return id;
        int objectId = nextObjectId++;
        putObject(obj, objectId);
        return objectId;
    }

    class ObjectWriter {
        final int classId;

        ObjectWriter(int classId) {
            this.classId = classId;
        }

        void writeClass(BufferedOutput out) throws IOException {}

        void writeObject(BufferedOutput out, Object value) throws IOException {
            switch (classId) {
            case STRING:
                out.writeUTFString((String) value);
                break;
            case BOOLEAN:
                out.writeCompactInt(((boolean[]) value).length);
                for (boolean b : (boolean[]) value)
                    out.writeByte(b ? 1 : 0);
                break;
            case BYTE:
                out.writeCompactInt(((byte[]) value).length);
                for (byte b : (byte[]) value)
                    out.writeByte(b);
                break;
            case SHORT:
                out.writeCompactInt(((short[]) value).length);
                for (short i : (short[]) value)
                    out.writeCompactInt(i);
                break;
            case CHAR:
                out.writeCompactInt(((char[]) value).length);
                for (char c : (char[]) value)
                    out.writeUTFChar(c);
                break;
            case INT:
                out.writeCompactInt(((int[]) value).length);
                for (int i : (int[]) value)
                    out.writeCompactInt(i);
                break;
            case LONG:
                out.writeCompactInt(((long[]) value).length);
                for (long i : (long[]) value)
                    out.writeCompactLong(i);
                break;
            case FLOAT:
                out.writeCompactInt(((float[]) value).length);
                for (float x : (float[]) value)
                    out.writeFloat(x);
                break;
            case DOUBLE:
                out.writeCompactInt(((double[]) value).length);
                for (double x : (double[]) value)
                    out.writeDouble(x);
                break;
            default:
                throw new IllegalArgumentException("classId=" + classId);
            }
        }
    }

    class ArrayDesc extends ObjectWriter {
        final ObjectWriter component;

        ArrayDesc(int classId, ObjectWriter component) {
            super(classId);
            this.component = component;
        }

        @Override
        void writeClass(BufferedOutput out) throws IOException {
            out.writeCompactInt(classId);
            out.writeUTFString(null);
            out.writeCompactInt(component.classId);
        }

        @Override
        void writeObject(BufferedOutput out, Object value) throws IOException {
            if (value == null) {
                out.writeCompactInt(-1);
                return;
            }
            out.writeCompactInt(((Object[]) value).length);
            for (Object o : (Object[]) value)
                writeObjectReference(out, o);
        }
    }

    static final class FieldDesc {
        final Field field;
        final int type;

        FieldDesc(Field field, int type) {
            this.field = field;
            this.type = type;
        }

        private Object getValue(Object obj) {
            try {
                return field.get(obj);
            } catch (IllegalAccessException e) {
                log.error("Cannot access " + field.getName() + " of " + field.getType().getName(), e);
                return null;
            }
        }

        @Override
        public String toString() {
            return field.getName();
        }
    }

    class ClassDesc extends ObjectWriter {
        final String name;
        final ClassDesc parent;
        final List<FieldDesc> fields = new ArrayList<FieldDesc>();

        ClassDesc(int classId, String name, ClassDesc parent) {
            super(classId);
            this.name = name;
            this.parent = parent;
        }

        @Override
        void writeClass(BufferedOutput out) throws IOException {
            out.writeCompactInt(classId);
            out.writeUTFString(name);
            out.writeCompactInt(parent == null ? 0 : parent.classId);
            out.writeCompactInt(fields.size());
            for (FieldDesc fd : fields) {
                out.writeUTFString(fd.field.getName());
                out.writeCompactInt(fd.type);
            }
        }

        @Override
        void writeObject(BufferedOutput out, Object obj) throws IOException {
            ClassDesc cur = this;
            do {
                for (FieldDesc fd : cur.fields)
                    writeTypedField(out, fd.type, fd.getValue(obj));
                cur = cur.parent;
            } while (cur != null);
        }
    }
}
