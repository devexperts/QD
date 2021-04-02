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
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.impl.RMIRequestImpl;
import com.devexperts.rmi.task.RMIServiceId;

/**
 * This immutable class encapsulates the basic fields of the request
 * @see RMIRequestImpl
 */
public final class RMIRequestMessage<T> extends RMIMessage {

    // ==================== private instance fields ====================

    private final RMIRequestType requestType;
    private final RMIOperation<T> operation;
    private final Marshalled<Object[]> parameters;
    private final RMIServiceId target; // May be null, null means "any target"

    // ==================== public methods ====================

    /**
     * Creates a request message.
     * @param type requestType (not null)
     * @param operation operation (not null)
     * @param parameters parameters that will be passed to executing operation method
     *
     * @throws NullPointerException when type or operation is null.
     */
    public RMIRequestMessage(RMIRequestType type, RMIOperation<T> operation, Object... parameters) {
        this(type, operation,
            Marshalled.forObject(parameters, operation.getParametersMarshaller()), RMIRoute.EMPTY, null);
    }

    /**
     * Creates a request message with the target.
     * @param type type of request
     * @param operation operation (not null)
     * @param parameters marshalled parameters (not null)
     * @param route route message
     * @param target target of the request
     *
     * @throws NullPointerException when any of arguments is null.
     * @throws IllegalArgumentException when parameter's {@link Marshalled#getMarshaller() marshaller} is
     *            different from operation's {@link RMIOperation#getParametersMarshaller() parameters marshaller}
     *            or subject's marshalled is not {@link Marshaller#SERIALIZATION}.
     */
    public RMIRequestMessage(RMIRequestType type, RMIOperation<T> operation,
        Marshalled<Object[]> parameters, RMIRoute route, RMIServiceId target)
    {
        super(route);
        if (operation == null)
            throw new NullPointerException("operation");
        if (!parameters.getMarshaller().equals(operation.getParametersMarshaller())) // causes NPE, too
            throw new IllegalArgumentException("Parameters used an incorrect marshaller");
        if (type == null)
            throw new NullPointerException("type");
        this.requestType = type;
        this.operation = operation;
        this.parameters = parameters;
        this.target = target;
    }

    /**
     * Creates a request message with the target.
     * @param requestType type of request
     * @param operation operation (not null)
     * @param subject marshalled subject (not null)
     * @param parameters marshalled parameters (not null)
     * @param route route message
     * @param target target of the request
     *
     * @throws NullPointerException when any of arguments is null.
     * @throws IllegalArgumentException when parameter's {@link Marshalled#getMarshaller() marshaller} is
     *            different from operation's {@link RMIOperation#getParametersMarshaller() parameters marshaller}
     *            or subject's marshalled is not {@link Marshaller#SERIALIZATION}.
     */


    /**
     * Creates {@link RMIRequestMessage} with changes in the route and target.
     * @param newTarget new target
     * @param route new route
     * @return new {@link RMIRequestMessage} with changes in the route and target
     */
    public RMIRequestMessage<T> changeTargetRoute(RMIServiceId newTarget, RMIRoute route) {
        return new RMIRequestMessage<>(requestType, operation, parameters, route, newTarget);
    }

    /**
     * Returns the target of this message (may be null, if request does not have a target).
     * @return the target of this message (may be null, if request does not have a target).
     */
    public RMIServiceId getTarget() {
        return target;
    }

    /**
     * Returns the operation of this message (not null).
     * @return the operation of this message (not null).
     */
    public RMIOperation<T> getOperation() {
        return operation;
    }

    /**
     * Returns parameters in {@link Marshalled Marshalled} form of this message (not null).
     * @return parameters in Marshalled form of this message (not null).
     */
    public Marshalled<Object[]> getParameters() {
        return parameters;
    }

    /**
     * Returns the {@link RMIRequestType type} of request of this message (not null).
     * @return the type of request of this message (not null).
     */
    public RMIRequestType getRequestType() {
        return requestType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        RMIRequestMessage<?> message = (RMIRequestMessage<?>) o;
        if (requestType != message.requestType)
            return false;
        if (!operation.equals(message.operation))
            return false;
        if (!parameters.equals(message.parameters))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        int result = requestType.hashCode();
        result = 31 * result + operation.hashCode();
        result = 31 * result + parameters.hashCode();
        return result + 7 * super.hashCode();
    }

    @Override
    public String toString() {
        // Note: It is use as a part of RMIExecutionTaskImpl.toString() method on processing failures
        return "{" +
            "operation=" + operation +
            ", target=" + target +
            ", parameters=" + parameters +
            ", route=" + route +
            '}';
    }
}
