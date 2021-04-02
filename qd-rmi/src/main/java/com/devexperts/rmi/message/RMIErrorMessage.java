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
package com.devexperts.rmi.message;

import com.devexperts.io.Marshalled;
import com.devexperts.io.Marshaller;
import com.devexperts.io.MarshallingException;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.impl.RMIRequestImpl;

import java.util.Objects;

/**
 * This immutable class encapsulated a failed response with <b>exceptionType</b>,
 * <b>exceptionMessage</b>, and <b>exception</b> attributes.
 * Marshalled form for this class using a strategy {@link RMIExceptionMarshaller}
 * @see RMIRequestImpl
 */
public final class RMIErrorMessage extends RMIResponseMessage {

    /**
     * Returns a specialized strategy to marshall {@link RMIException} instances.
     * @return a specialized strategy to marshall {@link RMIException} instances.
     */
    public static Marshaller<RMIException> getExceptionMarshaller() {
        return RMIExceptionMarshaller.INSTANCE;
    }

    /**
     * Creates error message.
     *
     * @param exceptionType exception type
     * @param cause <code>RMIException</code> cause
     * @param route route message. If the error was caused by the current {@link RMIEndpoint},
     * the route should be {@code null}
     * @throws MarshallingException if object cannot be converted to byte representation
     */
    public RMIErrorMessage(RMIExceptionType exceptionType, Throwable cause, RMIRoute route) {
        super(Marshalled.forObject(new RMIException(Objects.requireNonNull(exceptionType, "exceptionType"), cause),
            getExceptionMarshaller()), RMIResponseType.ERROR, route);
    }

    /**
     * Creates error message. It has to be marshalled with {@link #getExceptionMarshaller() exception marshaller}.
     *
     * @param exception exception in Marshalled form
     * @param route route message.
     * @throws IllegalArgumentException when exception's {@link Marshalled#getMarshaller() marshaller} is
     *            different from {@link #getExceptionMarshaller() exception marshaller}.
     */
    public RMIErrorMessage(Marshalled<RMIException> exception, RMIRoute route) {
        super(exception, RMIResponseType.ERROR, route);
        if (exception.getMarshaller() != getExceptionMarshaller())
            throw new IllegalArgumentException("used an incorrect marshaller");
    }

    @Override
    public String toString() {
        return "{" +
            "error=" + getMarshalledResult() +
            ", route=" + route +
            '}';
    }
}
