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
package com.devexperts.rmi.task;

import com.devexperts.connector.proto.JVMId;
import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.io.ByteArrayOutput;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;


/**
 * A unique ID, for each implementation {@link RMIService}.
 */
public class RMIServiceId implements Comparable<RMIServiceId> {

    private static final Map<String, AtomicLong> SERVICE_COUNTERS = new HashMap<>();

    // ======================== public static methods ========================

    /**
     * Creates unique RMIServiceId with special name.
     * @param name special name
     * @return unique RMIServiceId with special name
     */
    public static synchronized RMIServiceId newServiceId(String name) {
        if (!SERVICE_COUNTERS.containsKey(name))
            SERVICE_COUNTERS.put(name, new AtomicLong(0));
        return new RMIServiceId(name);
    }

    /**
     * Reads {@link RMIServiceId} from the buffered input.
     * @param in the source to read from
     * @param ctx the read context
     * @return RMIServiceId read
     * @throws IOException if an I/O error occurs
     */
    public static RMIServiceId readRMIServiceId(BufferedInput in, JVMId.ReadContext ctx) throws IOException {
        JVMId jvmId = JVMId.readJvmId(in, ctx);
        if (jvmId == null)
            return null;
        String name = in.readUTFString();
        long id = in.readCompactLong();
        return new RMIServiceId(name, jvmId, id);
    }

    /**
     * Write {@link RMIServiceId} to the buffered output.
     * @param out the destination to write to
     * @param serviceId the RMIServiceId to be written
     * @param ctx the write context
     * @throws IOException if an I/O error occurs
     */
    public static void writeRMIServiceId(BufferedOutput out, RMIServiceId serviceId, JVMId.WriteContext ctx) throws IOException {
        if (serviceId == null) {
            out.writeByteArray(null);
            return;
        }
        JVMId.writeJvmId(out, serviceId.jvmId, ctx);
        out.writeUTFString(serviceId.getName());
        out.writeCompactLong(serviceId.id);
    }

    // ======================== instance fields ========================

    private final String name;
    private final JVMId jvmId;
    private final long id;

    private RMIServiceId(String name, JVMId jvmId, long id) {
        this.name = name;
        this.jvmId = jvmId;
        this.id = id;
    }

    // ======================== private instance constructor ========================

    private RMIServiceId(String name) {
        this(name, JVMId.JVM_ID, SERVICE_COUNTERS.get(name).getAndIncrement());
    }

    // ======================== public methods ========================

    /**
     * Returns {@link JVMId} for this service implementation.
     * @return {@link JVMId} for this service implementation
     */
    public JVMId getJVMId() {
        return jvmId;
    }

    /**
     * Returns service name.
     * @return service name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns number service implementation with the specified name on the specified JVM.
     * @return number service implementation with the specified name on the specified JVM
     */
    public long getId() {
        return id;
    }

    /**
     * Returns RMIServiceId in the byte representation.
     * @return RMIServiceId in the byte representation
     */
    public byte[] getBytes() {
        ByteArrayOutput out = new ByteArrayOutput();
        try {
            writeRMIServiceId(out, this, null);
        } catch (IOException e) {
            throw new AssertionError();
        }
        return out.getBuffer();
    }

    /**
     * Compares this object with the specified object for order.
     * @param   other the object to be compared.
     * @return  a negative integer, zero, or a positive integer as this object
     *          is less than, equal to, or greater than the specified object.
     * @throws NullPointerException if the specified object is null
     */
    @Override
    public int compareTo(RMIServiceId other) {
        int i = jvmId.compareTo(other.jvmId);
        if (i != 0)
            return i;
        i = name.compareTo(other.name);
        if (i != 0)
            return i;
        return Long.compare(id, other.id);
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
        if (!(o instanceof RMIServiceId))
            return false;
        RMIServiceId other = (RMIServiceId) o;
        return jvmId.equals(other.jvmId) && name.equals(other.name) && id == other.id;
    }

    /**
     * Returns a hash code value for the object.
     * @return  a hash code value for this object.
     * @see     #equals(Object)
     */
    @Override
    public int hashCode() {
        return (jvmId.hashCode() * 27 + name.hashCode()) * 27 + ((int) id ^ (int) (id >> 32));
    }

    /**
     * Returns a string representation of the object.
     * @return a string representation of the object.
     */
    @Override
    public String toString() {
        return name + "@" + (id == 0 ? jvmId.toString() : (jvmId + "." + id));
    }

}
