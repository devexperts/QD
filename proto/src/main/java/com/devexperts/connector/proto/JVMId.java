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
package com.devexperts.connector.proto;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.util.SystemProperties;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Unique ID for the each JVM.
 */
public class JVMId implements Comparable<JVMId> {
    private static final int UID_SIZE = 16; // default JVM ID SIZE

    private static final int TEXT_LENGTH = SystemProperties.getIntProperty(
        JVMId.class, "textLength", 5);
    private static final int MAX_JVM_ID_BYTES = SystemProperties.getIntProperty(
        JVMId.class, "maxJVMIdBytes", 100);

    private static final char[] CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    // ======================== public static constants ========================

    /**
     * Unique ID for this JVM.
     */
    public static final JVMId JVM_ID = new JVMId();

    // ======================== public static methods ========================

    /**
     * Reads {@link JVMId} from the buffered input.
     * @param in the source to read from
     * @param ctx the read context
     * @return JVMId read
     * @throws IOException if an I/O error occurs
     */
    public static JVMId readJvmId(BufferedInput in, ReadContext ctx) throws IOException {
        byte[] uid;
        if (ctx == null) {
            uid = in.readByteArray();
            return uid == null ? null : new JVMId(uid);
        }
        long length = in.readCompactLong();
        if (length == 0 || length > MAX_JVM_ID_BYTES)
            throw new IOException("Illegal JVMId length: " + length);
        if (length == -1)
            return null;
        if (length < -1)
            return ctx.getJVMId(length);
        uid = new byte[(int) length];
        in.readFully(uid);
        JVMId jvmId = new JVMId(uid);
        ctx.addJVMId(jvmId);
        return jvmId;
    }


    /**
     * Write {@link JVMId} to the buffered output.
     * @param out the destination to write to
     * @param jvmId the JVMId to be written
     * @param ctx the write context
     * @throws IOException if an I/O error occurs
     */
    public static void writeJvmId(BufferedOutput out, JVMId jvmId, WriteContext ctx) throws IOException {
        if (ctx == null) {
            out.writeByteArray(jvmId == null ? null : jvmId.uid);
            return;
        }
        int length = ctx.getIndex(jvmId);
        out.writeCompactInt(length);
        if (length > 0)
            out.write(jvmId.uid);
    }

    // ======================== instance fields ========================

    private final byte[] uid;
    private String text; // null until not needed

    // ======================== private instance constructor and methods ========================

    private JVMId() {
        uid = new byte[UID_SIZE];
        ByteBuffer buf = ByteBuffer.wrap(uid);
        UUID uuid = UUID.randomUUID();
        buf.putLong(uuid.getMostSignificantBits());
        buf.putLong(uuid.getLeastSignificantBits());
    }

    protected JVMId(byte[] uid) {
        if (uid == null)
            throw new NullPointerException();
        this.uid = uid;
    }

    private String createText() {
        char[] result = new char[TEXT_LENGTH];
        int temp = 0;
        for (int i = Math.max(0, uid.length - 4); i < uid.length; i++) {
            temp = (temp << 8) | (uid[i] & 0xff);
        }
        for (int i = TEXT_LENGTH - 1; i >= 0; i--) {
            result[i] = CHARS[Math.abs(temp % CHARS.length)];
            temp /= CHARS.length;
        }
        return new String(result);
    }

    // ======================== public methods ========================

    public byte[] getUID() {
        return uid;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * @param   o the reference object with which to compare.
     * @return  {@code true} if this object is the same as the obj
     *          argument; {@code false} otherwise.
     * @see     #hashCode()
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof JVMId))
            return false;
        JVMId other = (JVMId) o;
        return Arrays.equals(uid, other.uid);
    }

    /**
     * Returns a hash code value for the object.
     * @return a hash code value for this object.
     * @see     #equals(Object)
     */
    @Override
    public int hashCode() {
        int result = 0;
        for (byte b : uid)
            result = result * 27 + b;
        return result;
    }

    /**
     * Returns JVMId in Base52 representation.
     * @return JVMId in Base52 representation
     */
    @Override
    public String toString() {
        if (text != null)
            return text;
        return text = createText();
    }

    /**
     * Compares this object with the specified object for order.
     * @param   other the object to be compared.
     * @return  a negative integer, zero, or a positive integer as this object
     *          is less than, equal to, or greater than the specified object.
     * @throws NullPointerException if the specified object is null
     */
    @Override
    public int compareTo(JVMId other) {
        return arrayCompare(uid, other.uid);
    }

    public static int arrayCompare(byte[] a1, byte[] a2) {
        int n = Math.min(a1.length, a2.length);
        for (int i = 0; i < n; i++) {
            if (a1[i] < a2[i])
                return -1;
            if (a1[i] > a2[i])
                return 1;
        }
        if (a1.length < a2.length)
            return -1;
        if (a1.length > a2.length)
            return 1;
        return 0;
    }

    // ======================== public nested classes ========================

    /**
     * Write context for write a large number of JVMId in compact form.
     */
    public static class WriteContext {
        private final Map<JVMId, Integer> jvmIdMap = new HashMap<>();

        int getIndex(JVMId jvmId) {
            if (jvmId == null)
                return -1;
            if (jvmIdMap.containsKey(jvmId))
                return jvmIdMap.get(jvmId);
            int index = -jvmIdMap.size() - 2;
            jvmIdMap.put(jvmId, index);
            return jvmId.getUID().length;
        }
    }

    /**
     * Read context for read a large number of JVMId in compact form.
     */
    public static class ReadContext {
        private List<JVMId> jvmIds = new ArrayList<>();

        JVMId getJVMId(long index) throws IOException {
            if (index > -2 || index <= -jvmIds.size() - 2)
                throw new IOException("Invalid JVMId reference: " + index);
            return jvmIds.get((int) (-index - 2));
        }

        void addJVMId(JVMId jvmId) {
            jvmIds.add(jvmId);
        }
    }
}
