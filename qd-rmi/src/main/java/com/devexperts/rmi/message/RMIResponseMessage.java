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
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIRequest;

/**
 * This abstract class forms a basis for two type of request response messages: {@link RMIResultMessage}
 * and {@link RMIErrorMessage}
 * @see RMIRequest
 */
public abstract class RMIResponseMessage extends RMIMessage {

    private final Marshalled<?> result;
    private final RMIResponseType type;

    /**
     * @return marshaller for {@link Marshalled} result in {@link RMIErrorMessage}
     */
    public static Marshaller<RMIException> getExceptionMarshaller() {
        return RMIExceptionMarshaller.INSTANCE;
    }

    protected RMIResponseMessage(Marshalled<?> result, RMIResponseType type, RMIRoute route) {
        super(route);
        this.result = result;
        this.type = type;
    }

    protected RMIResponseMessage(RMIExceptionType exceptionType, Throwable cause, RMIRoute route) {
        this(Marshalled.forObject(new RMIException(exceptionType, cause), getExceptionMarshaller()),
            RMIResponseType.ERROR, route);
    }

    /**
     * Returns the object encapsulated in Marshalled form
     * @return the object encapsulated in Marshalled form
     */
    public Marshalled<?> getMarshalledResult() {
        return result;
    }

    public RMIResponseType getType() {
        return type;
    }
}
