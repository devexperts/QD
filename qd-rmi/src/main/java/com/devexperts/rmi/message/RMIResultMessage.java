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
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.impl.RMIRequestImpl;

/**
 * This immutable class encapsulates a successful completion response with <b>operation</b> and <b>result</b> attributes.
 *
 * @see RMIRequestImpl
 */
public final class RMIResultMessage<T> extends RMIResponseMessage {

    /**
     * Creates result message.
     *
     * @param operation operation
     * @param result result in Marshalled form
     * @param route route message
     * @throws IllegalArgumentException when result's {@link Marshalled#getMarshaller() marshaller} is
     *            different from operation's {@link RMIOperation#getResultMarshaller() result marshaller}.
     */
    public RMIResultMessage(RMIOperation<T> operation, Marshalled<T> result, RMIRoute route) {
        super(result, RMIResponseType.SUCCESS, route);
        if (!result.getMarshaller().equals(operation.getResultMarshaller()))
            throw new IllegalArgumentException("used an incorrect marshaller");
    }

    /**
     * Creates result message.
     *
     * @param operation operation
     * @param result result
     */
    @SuppressWarnings("unchecked")
    public RMIResultMessage(RMIOperation<T> operation, T result) {
        this(operation, Marshalled.forObject(result, operation.getResultMarshaller()), null);
    }

    @Override
    public String toString() {
        return "{" +
            "result=" + getMarshalledResult() +
            ", route=" + route +
            '}';
    }
}
