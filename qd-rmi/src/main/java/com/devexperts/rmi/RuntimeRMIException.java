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

/**
 * Wrapping runtime exception for {@link RMIException}.
 * It is thrown instead of {@link RMIException} when
 * remote method does not declare throwing the latter.
 */
public class RuntimeRMIException extends RuntimeException {
    private static final long serialVersionUID = 0;

    /**
     * Constructs a RemoteRuntimeException with specified
     * {@link RMIException} as a cause.
     * @param cause RMIException to wrap.
     */
    public RuntimeRMIException(RMIException cause) {
        initCause(cause);
    }

    /**
     * Returns {@link RMIException} wrapped by this RemoteRuntimeException.
     * @return {@link RMIException} wrapped by this RemoteRuntimeException.
     */
    @Override
    public RMIException getCause() {
        return (RMIException) super.getCause();
    }

    @Override
    public String toString() {
        return super.toString() + " " + super.getCause();
    }
}
