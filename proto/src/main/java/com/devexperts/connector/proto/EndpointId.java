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
import java.io.Serializable;

/**
 * A unique ID for each QDEndpoint.
 */
public class EndpointId implements Comparable<EndpointId>, Serializable {

    private static final int COUNTER_MODULO = SystemProperties.getIntProperty(
        EndpointId.class, "counterModulo", 64);

    static {
        if (COUNTER_MODULO <= 0 || (COUNTER_MODULO & (COUNTER_MODULO - 1)) != 0)
            throw new ExceptionInInitializerError("counterModule must be a power of 2");
    }

    private static final long[] ENDPOINT_COUNTER = new long[COUNTER_MODULO];

    private static final long serialVersionUID = -3853750500070074505L;

    // ======================== public static methods ========================

    /**
     * Create new unique EndpointId with special name
     * @param name special name
     * @return new unique EndpointId with special name;
     */
    public static synchronized EndpointId newEndpointId(String name) {
        int i = name.hashCode() & (COUNTER_MODULO - 1);
        return new EndpointId(JVMId.JVM_ID, name, ENDPOINT_COUNTER[i]++);
    }

    /**
     * Reads {@link EndpointId} from the buffered input.
     * @param in the source to read from
     * @param ctx the read context
     * @return EndpointId read
     * @throws IOException if an I/O error occurs
     */
    public static EndpointId readEndpointId(BufferedInput in, JVMId.ReadContext ctx) throws IOException {
        JVMId jvmId = JVMId.readJvmId(in, ctx);
        if (jvmId == null)
            return null;
        String name = in.readUTFString();
        long id = in.readCompactLong();
        return new EndpointId(jvmId, name, id);
    }

    /**
     * Write {@link EndpointId} to the buffered output.
     * @param out the destination to write to
     * @param endpointId the EndpointId to be written
     * @param ctx the write context
     * @throws IOException if an I/O error occurs
     */
    public static void writeEndpointId(BufferedOutput out, EndpointId endpointId, JVMId.WriteContext ctx) throws IOException {
        if (endpointId == null) {
            out.writeByteArray(null);
            return;
        }
        JVMId.writeJvmId(out, endpointId.jvmId, ctx);
        out.writeUTFString(endpointId.name);
        out.writeCompactLong(endpointId.id);
    }

    // ======================== instance fields ========================

    private final JVMId jvmId;
    private String name;
    private final long id;

    // ======================== private instance constructor ========================

    private EndpointId(JVMId jvmId, String name, long id) {
        this.jvmId = jvmId;
        this.name = name;
        this.id = id;
    }

    // ======================== public methods ========================

    /**
     * Returns unique {@link JVMId} for this Endpoint.
     * @return unique {@link JVMId} for this Endpoint
     */
    public JVMId getJVMId() {
        return jvmId;
    }

    /**
     * Returns special name for this EndpointId.
     * @return special name for this EndpointId
     */
    public String getName() {
        return name;
    }


    /**
     * Returns id this EndpointId on a JVM.
     * @return id this EndpointId on a JVM.
     */
    public long getId() {
        return id;
    }

    /**
     * Compares this object with the specified object for order.
     * @param   other the object to be compared.
     * @return  a negative integer, zero, or a positive integer as this object
     *          is less than, equal to, or greater than the specified object.
     * @throws NullPointerException if the specified object is null
     */
    @Override
    public int compareTo(EndpointId other) {
        int i = jvmId.compareTo(other.jvmId);
        if (i != 0)
            return i;
        i = name.compareTo(other.name);
        if (i != 0)
            return i;
        return id < other.id ? -1 : (id > other.id) ? 1 : 0;
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
        if (!(o instanceof EndpointId))
            return false;
        EndpointId other = (EndpointId) o;
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

