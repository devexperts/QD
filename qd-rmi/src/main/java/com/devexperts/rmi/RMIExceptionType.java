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

import com.devexperts.rmi.impl.RMIClientImpl;
import com.devexperts.rmi.security.SecurityController;

import java.util.concurrent.RejectedExecutionException;

/**
 * Types of {@link RMIException}.
 */
public enum RMIExceptionType {

    /**
     * The method execution was finished by throwing an internal application
     * error.
     * <p> The corresponding {@link RMIException}'s cause in this case is
     * always not null and contains the occurred application error.
     */
    APPLICATION_ERROR(1, FailureInstant.AFTER_EXECUTION,
        "Request execution finished by throwing an internal application error"),

    /**
     * The request has been cancelled by user before its actual remote
     * execution started.
     */
    CANCELLED_BEFORE_EXECUTION(10, FailureInstant.BEFORE_EXECUTION,
        "Request was cancelled before execution"),

    /**
     * The request was aborted by user in any phase of its execution.
     */
    CANCELLED_DURING_EXECUTION(11, FailureInstant.ANYWHERE_DURING_EXECUTION,
        "Request was cancelled during execution"),

    /**
     * The request was already completed when the incoming canceling was received.
     */
    CANCELLED_AFTER_EXECUTION(12, FailureInstant.AFTER_EXECUTION,
        "Request was cancelled after execution"),

    /**
     * Connection broke before the request outcome was received on the client side.
     */
    DISCONNECTION(20, FailureInstant.ANYWHERE_DURING_EXECUTION,
        "Connection broke"),

    /**
     * The execution task of this request was rejected by executor service
     * by throwing a {@link RejectedExecutionException}.
     */
    EXECUTION_REJECTION(21, FailureInstant.BEFORE_EXECUTION,
        "The task was rejected by executor"),

    /**
     * An {@link IllegalAccessException} raised when trying to execute method on
     * the server side.
     */
    ILLEGAL_ACCESS(22, FailureInstant.BEFORE_EXECUTION,
        "Could not access method"),

    /**
     * {@link SecurityController} suppressed an attempt to execute the request
     * by throwing a {@link SecurityException}.
     */
    SECURITY_VIOLATION(23, FailureInstant.BEFORE_EXECUTION,
        "Request execution was forbidden by security controller"),

    /**
     * Some error occurred when attempting to execute method.
     */
    EXECUTION_ERROR(24, FailureInstant.ANYWHERE_DURING_EXECUTION,
        "Could not execute method"),

    /**
     * The request could not be sent within defined timeout
     * {@link RMIClientImpl#requestSendingTimeout}.
     */
    REQUEST_SENDING_TIMEOUT(25, FailureInstant.BEFORE_EXECUTION,
        "Could not send request within timeout"),

    /**
     * The request execution outcome was not received within defined timeout
     * {@link RMIClientImpl#requestRunningTimeout}.
     */
    REQUEST_RUNNING_TIMEOUT(26, FailureInstant.ANYWHERE_DURING_EXECUTION,
        "Request remote execution timeout exceeded"),

    /**
     * Subject ID received with the request did not correspond with any subject.
     */
    UNKNOWN_SUBJECT(30, FailureInstant.BEFORE_EXECUTION,
        "Unknown subject identifier in request"),

    /**
     * Operation ID received with the request did not correspond with any operation.
     */
    UNKNOWN_OPERATION(31, FailureInstant.BEFORE_EXECUTION,
        "Unknown operation identifier in request"),

    /**
     * Requested operation is not currently provided by the server endpoint.
     */
    OPERATION_NOT_PROVIDED(32, FailureInstant.BEFORE_EXECUTION,
        "Requested operation is not provided by remote side"),

    /**
     * Request execution failed with an exception of unknown type.
     */
    UNKNOWN_RMI_EXCEPTION(33, FailureInstant.ANYWHERE_DURING_EXECUTION,
        "Unknown RMI exception occurred"),

    /**
     * Service with the request did not correspond with any service.
     */
    UNKNOWN_SERVICE(34, FailureInstant.BEFORE_EXECUTION,
        "Unknown service in request"),

    /**
     * Service implementation did not set task cancel listener.
     */
    TASK_CANCEL_LISTENER_NOT_SET(35, FailureInstant.ANYWHERE_DURING_EXECUTION,
        "Service implementation did not set task cancel listener"),

    /**
     * Service implementation did handle suspended state properly.
     */
    INVALID_SUSPEND_STATE(36, FailureInstant.ANYWHERE_DURING_EXECUTION,
        "Service implementation did handle suspended state properly"),

    /**
     * Request got into routing loop or the server had disconnected
     */
    ROUTE_IS_NOT_FOUND(37, FailureInstant.BEFORE_EXECUTION,
        "Route to server could not be found"),

    /**
     * Server was unable to read request.
     */
    FAILED_TO_READ_REQUEST(38, FailureInstant.BEFORE_EXECUTION,
        "Server was unable to read request"),

    /**
     * The channel is closed, or closed during the execution of the request.
     */
    CHANNEL_CLOSED(39, FailureInstant.ANYWHERE_DURING_EXECUTION,
        "The channel closed, or closed during the execution of the request"),

    /**
     * Load balancing algorithm failed to select an implementation of a service to send request to
     */
    SERVICE_UNAVAILABLE(40, FailureInstant.BEFORE_EXECUTION,
        "Load balancing could not select the target service"),


    // -------------------- Marshalling/unmarshalling --------------------

    /**
     * Remote method parameters could not be marshalled on the client side.
     */
    PARAMETERS_MARSHALLING_ERROR(50, FailureInstant.BEFORE_EXECUTION,
        "Could not marshall method parameters"),

    /**
     * Remote method parameters could not be unmarshalled on the server side.
     */
    PARAMETERS_UNMARSHALLING_ERROR(51, FailureInstant.BEFORE_EXECUTION,
        "Could not unmarshall method parameters"),

    /**
     * Subject could not be marshalled on the client side.
     */
    SUBJECT_MARSHALLING_ERROR(52, FailureInstant.BEFORE_EXECUTION,
        "Could not marshall subject"),

    /**
     * Subject could not be unmarshalled on the server side.
     */
    SUBJECT_UNMARSHALLING_ERROR(53, FailureInstant.BEFORE_EXECUTION,
        "Could not unmarshall subject"),

    /**
     * An error occurred while marshalling execution result.
     */
    RESULT_MARSHALLING_ERROR(54, FailureInstant.AFTER_EXECUTION,
        "Could not marshall execution result"),

    /**
     * An error occurred while unmarshalling execution result.
     */
    RESULT_UNMARSHALLING_ERROR(55, FailureInstant.AFTER_EXECUTION,
        "Could not unmarshall execution result"),

    /**
     * The request execution was finished by throwing an internal application error
     * but this error could not be marshalled on the server side.
     */
    APPLICATION_ERROR_MARSHALLING_ERROR(56, FailureInstant.AFTER_EXECUTION,
        "Could not marshall application error"),

    /**
     * The request execution was finished by throwing an internal application error
     * but this error could not be unmarshalled on the client side.
     */
    APPLICATION_ERROR_UNMARSHALLING_ERROR(57, FailureInstant.AFTER_EXECUTION,
        "Could not unmarshall application error"),

    ; // ==================== Public getters ====================

    /**
     * Returns <tt>true</tt> if exceptions of this type always occur only
     * before actual method invocation at server begins.
     * @return <tt>true</tt> if exceptions of this type always occur only
     * before actual method invocation at server begins.
     */
    public boolean isFailedBeforeExecution() {
        return failureInstant == FailureInstant.BEFORE_EXECUTION;
    }

    /**
     * Returns <tt>true</tt> if exceptions of this type always occur only
     * after actual method invocation at server completes (either successfully
     * or by throwing an application error).
     * @return <tt>true</tt> if exceptions of this type always occur only
     * after actual method invocation at server completes (either successfully
     * or by throwing an application error).
     */
    public boolean isFailedAfterExecution() {
        return failureInstant == FailureInstant.AFTER_EXECUTION;
    }

    /**
     * Returns <tt>true</tt> if exceptions of this type always occur when the request is cancelled.
     * @return <tt>true</tt> if exceptions of this type always occur when the request is cancelled.
     */
    public boolean isCancelled() {
        return this == CANCELLED_DURING_EXECUTION ||
            this == CANCELLED_BEFORE_EXECUTION ||
            this == CANCELLED_AFTER_EXECUTION;
    }

    /**
     * Returns a message that corresponds to this type of exception.
     * @return a message that corresponds to this type of exception.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns exception type identifier.
     * @return exception type identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * Returns exception type for the specified identifier.
     * @param id type identifier.
     * @return exception type for the specified identifier.
     */
    public static RMIExceptionType getById(int id) {
        return id >= 0 && id < TYPE_BY_ID.length ? TYPE_BY_ID[id] : null;
    }

    // ==================== Implementation ====================

    private static final RMIExceptionType[] TYPE_BY_ID = new RMIExceptionType[60];

    static {
        for (RMIExceptionType type : values()) {
            if (TYPE_BY_ID[type.getId()] != null)
                throw new AssertionError("Duplicate id: " + type.getId());
            TYPE_BY_ID[type.getId()] = type;
        }
    }

    private enum FailureInstant {
        BEFORE_EXECUTION, ANYWHERE_DURING_EXECUTION, AFTER_EXECUTION,
    }

    private final String message;
    private final int id;
    private final FailureInstant failureInstant;

    RMIExceptionType(int id, FailureInstant failureInstant, String message) {
        this.id = id;
        this.failureInstant = failureInstant;
        this.message = message;
    }
}
