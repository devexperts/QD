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
package com.devexperts.rmi;

import java.rmi.RemoteException;

/**
 * An exception that occurs during request execution.
 * The specific {@link RMIExceptionType type} of the exception can be ascertained with the
 * help of {@link #getType()} method.
 * <p> The most special type of <code>RMIException</code> is
 * {@link RMIExceptionType#APPLICATION_ERROR} which means that the request execution
 * finished by throwing an application error, which is available as this
 * <code>RMIException</code> cause ({@link #getCause()} method).
 * All other types of <code>RMIException</code> are RMI-framework layer failures.
 *
 * @see RMIExceptionType
 */
public final class RMIException extends RemoteException {
    private static final long serialVersionUID = 0;

    private final RMIExceptionType type;
    private final boolean requestInfo;

    /**
     * Constructs new {@link RMIException} of specified type.
     * @param type the type of this exception.
     * @param cause {@link Throwable} that caused this exception.
     * @throws NullPointerException if the type is <tt>null</tt> or if the type is
     * {@link RMIExceptionType#APPLICATION_ERROR} and the cause is null.
     */
    public RMIException(RMIExceptionType type, Throwable cause) {
        super(type.getMessage(), cause);
        if (type == RMIExceptionType.APPLICATION_ERROR && cause == null)
            throw new NullPointerException("Application error cause can never be null.");
        this.type = type;
        this.requestInfo = false;
    }

    /**
     * Constructs new {@link RMIException} with request data.
     * @param exception the RMIException.
     * @param request the request.
     * @throws NullPointerException if the exception is <tt>null</tt> or if the type is
     * {@link RMIExceptionType#APPLICATION_ERROR} and the cause is null.
     */
    public RMIException(RMIException exception, RMIRequest<?> request) {
        super(exception.getMessage() + " (" + request.toString() + ")", exception.getCause());
        this.type = exception.type;
        this.requestInfo = true;
    }

    /**
     * Returns <code>true<code/> if exception includes information about request data.
     * @return <code>true<code/> if exception includes information about request data.
     */
    public boolean hasRequestInfo() {
        return requestInfo;
    }

    /**
     * Returns the type of this {@link RMIException}.
     * @return the type of this {@link RMIException}.
     */
    public RMIExceptionType getType() {
        return type;
    }
}
