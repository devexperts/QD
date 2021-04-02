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

import com.devexperts.rmi.message.RMIRequestType;

/**
 * {@link RMIRequest} execution states.
 */
public enum RMIRequestState {

    /**
     * The request was created but sending has not been started yet.
     */
    NEW(false),

    /**
     * The request {@link RMIRequest#send() send} method has been invoked and
     * the request it waiting to be sent.
     */
    WAITING_TO_SEND(false),

    /**
     * The request has started to be sent to a remote endpoint.
     * Note, that {@link RMIRequestType#ONE_WAY ONE_WAY} requests cannot be in this state.
     * When they had started to be sent they become {@link #SUCCEEDED}
     * (this is because one way requests cannot be canceled after that).
     */
    SENDING(false),

    /**
     * The request has been completely sent to a remote endpoint.
     */
    SENT(false),

    /**
     * The request cancellation has been initiated by invoking
     * {@link RMIRequest#cancelWithConfirmation()} method.
     */
    CANCELLING(false),

    /**
     * The request execution was completed successfully without causing
     * an error and result is available (for <b>two-way</b> requests).
     * <br><b>OR</b><br>
     * The request was sent successfully (for <b>one-way</b> requests).
     */
    SUCCEEDED(true),

    /**
     * The request execution failed by some error. The exact cause of request
     * failure in this case is available via {@link RMIRequest#getException()}
     * which returns an {@link RMIException}. See {@link RMIExceptionType} for
     * different failure causes.
     */
    FAILED(true),

    ;

    /**
     * Returns <tt>true</tt> if this state corresponds to completed request.
     * In this case this state is final and can not be changed anymore.
     * <br>There are only two final states of the request:
     * {@link #SUCCEEDED} and {@link #FAILED}
     * @return <tt>true</tt> if this state corresponds to completed request.
     */
    public boolean isCompleted() {
        return completed;
    }

    private final boolean completed;

    RMIRequestState(boolean completed) {
        this.completed = completed;
    }
}
