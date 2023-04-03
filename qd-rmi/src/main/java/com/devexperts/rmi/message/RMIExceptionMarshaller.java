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
package com.devexperts.rmi.message;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.io.Marshaller;
import com.devexperts.io.SerialClassContext;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.util.SystemProperties;

import java.io.EOFException;
import java.io.IOException;

/**
 * Special strategy for encapsulation errors({@link RMIException RMIException}).
 * She knows how to extract the {@link RMIExceptionType type} and an
 * {@link RMIException#getMessage() error message} if deserialization failed.
 */
class RMIExceptionMarshaller extends Marshaller<RMIException> {

    private static final StackTraceElement[] EMPTY_STACK = new StackTraceElement[0];

    private static final boolean removeStackTraces =
            SystemProperties.getBooleanProperty("com.devexperts.rmi.removeStackTraces", true);

    static final RMIExceptionMarshaller INSTANCE = new RMIExceptionMarshaller();

    private RMIExceptionMarshaller() {} // singleton

    /**
     * Message consists of integer number (message type), string and byte array (self exception).
     *
     * @param in the source to read from
     * @return the <code>int</code> value read
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int readMarshalledLength(BufferedInput in) throws IOException {
        in.mark();
        long startPosition = in.totalPosition();
        in.readCompactInt(); // type
        int messageLength = Math.max(0, in.readCompactInt()); // exception message length
        if (in.skip(messageLength) != messageLength) // skip exception message body
            throw new EOFException("Input is too short, expected message length: " + messageLength);
        int objectBodyLength = Math.max(0, in.readCompactInt()); // read object length
        long objectBodyPosition = in.totalPosition();
        in.reset();
        return (int) (objectBodyPosition - startPosition) + objectBodyLength;
    }

    /**
     * The length is not written by this implementation.
     * It is computed from the contents by {@link #readMarshalledLength(BufferedInput)}.
     *
     * @param out the destination to write to
     * @param length of an object in serialized form to be written
     */
    @Override
    public void writeMarshalledLength(BufferedOutput out, int length) {
        return;
    }

    @Override
    public void writeObjectTo(BufferedOutput out, RMIException object) throws IOException {
        if (removeStackTraces)
            removeStackTraces(object);
        out.writeCompactInt(object.getType().getId());
        out.writeUTFString(object.getMessage());
        out.writeObject(object);
    }

    @Override
    public RMIException readObjectFrom(BufferedInput in, int length, SerialClassContext serialContext) throws IOException {
        RMIException result;
        int typeId = in.readCompactInt(); // must be always Ok, was checked by readMarshalledLength
        RMIExceptionType type = RMIExceptionType.getById(typeId);
        if (type == null)
            type = RMIExceptionType.UNKNOWN_RMI_EXCEPTION;
        String causeMessage = null;
        try {
            causeMessage = in.readUTFString(); // may crash with UTFDataFormatException, for example
            result = (RMIException) in.readObject(serialContext);
            if (result == null)
                result = new RMIException(type, new Exception(causeMessage, null));
        } catch (Throwable t) {
            result = causeMessage != null ?
                new RMIException(type, new Exception(causeMessage, t)) :
                new RMIException(type, t);
        }
        return result;
    }

    /**
     * Remove all modifiable stack traces from an exception
     */
    private static void removeStackTraces(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            current.setStackTrace(EMPTY_STACK);
            for (Throwable t: current.getSuppressed()) {
                removeStackTraces(t);
            }
            Throwable cause = current.getCause();
            current = cause == current ? null : cause;
        }
    }
}
