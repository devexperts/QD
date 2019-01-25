/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.impl;


import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.*;

import com.devexperts.logging.Logging;
import com.devexperts.rmi.*;
import com.devexperts.rmi.message.RMIRequestType;
import com.dxfeed.promise.Promise;

/**
 * An {@link InvocationHandler} that is used inside remote service proxies created
 * on the client side of RMI framework. The handler creates a corresponding
 * {@link RMIRequestImpl} for every method invocation, sends it for execution and
 * then returns its result (or throws an error if it fails).
*/
class RMIRequestInvocationHandler implements InvocationHandler {
    // ==================== private static fields ====================
    private static final Logging log = Logging.getLogging(RMIRequestInvocationHandler.class);

    private static final StackTraceElement RMI_LAYER_SEPARATOR_FRAME =
        new StackTraceElement("com.devexperts.rmi", "<REMOTE-METHOD-INVOCATION>", null, -1);

    // ==================== private static implementation ====================

    static void trimStackTrace(Throwable cause) {
        // Set the proper stack-trace for the cause.
        StackTraceElement[] remoteStackTrace = cause.getStackTrace();
        cause.fillInStackTrace();
        StackTraceElement[] localStackTrace =  cause.getStackTrace();
        // Two first (topmost) elements of the local stack-trace should be omitted:
        // com.devexperts.rmi.impl.RMIInvocationHandler.invoke(RMIInvocationHandler.java:58)
        // $ProxyN.throwError(Unknown Source)
        StackTraceElement[] combinedStackTrace =
            new StackTraceElement[remoteStackTrace.length + localStackTrace.length - 2];
        System.arraycopy(remoteStackTrace, 0, combinedStackTrace, 0, remoteStackTrace.length);
        combinedStackTrace[remoteStackTrace.length] = RMI_LAYER_SEPARATOR_FRAME;
        System.arraycopy(localStackTrace, 3, combinedStackTrace,
            remoteStackTrace.length + 1, localStackTrace.length - 3);
        cause.setStackTrace(combinedStackTrace);
    }

    // ==================== private instance fields ====================

    private final RMIClientPortImpl clientPort;
    private final String serviceName;
    private final EnumSet<ObjectMethods> objectOverrideMethods;
    private final Map<Method, RMIOperation<?>> operationCache =
        Collections.synchronizedMap(new HashMap<Method, RMIOperation<?>>());

    // ==================== public methods ====================

    @Override
    public Object invoke(Object proxy, Method method, Object[] parameters) throws Throwable {
        // All object methods are invoked via Proxy with method.getDeclaringClass() equal to Object.class even
        // if they are overridden in interface.
        if (method.getDeclaringClass().equals(Object.class) &&
            !objectOverrideMethods.contains(ObjectMethods.getMethod(method)))
        {
            return invokeObjMethod(ObjectMethods.getMethod(method), proxy, parameters);
        }
        RMIServiceMethod methodAnnotation = method.getAnnotation(RMIServiceMethod.class);
        RMIRequestType type = methodAnnotation != null ? methodAnnotation.type() : RMIRequestType.DEFAULT;
        RMIOperation<?> operation = getOperation(method);
        Object[] params = parameters == null ? new Object[] {} : parameters;
        RMIRequest<?> request = clientPort.createRequest(type, operation, params);
        try {
            request.send();
            try {
                if (method.getReturnType() == Promise.class)
                    return request.getPromise();
                return request.getBlocking();
            } catch (RMIException e) {
                if (e.getType() == RMIExceptionType.APPLICATION_ERROR) {
                    Throwable cause = e.getCause(); // always not null for APPLICATION_ERROR
                    trimStackTrace(cause);
                    throw cause;
                }
                throw e;
            }
        } catch (RMIException e) {
            for (Class<?> exceptionType : method.getExceptionTypes()) {
                if (exceptionType.isAssignableFrom(e.getClass()))
                    throw e;
            }
            log.error("Exception in request:" + request, e);
            throw new RuntimeRMIException(e);
        }
    }

    // ==================== private implementation ====================

    RMIRequestInvocationHandler(RMIClientPortImpl clientPort, String serviceName,
        EnumSet<ObjectMethods> objectOverrideMethods)
    {
        this.clientPort = clientPort;
        this.serviceName = serviceName;
        this.objectOverrideMethods = objectOverrideMethods;
    }

    @SuppressWarnings("unchecked")
    private <T> RMIOperation<T> getOperation(Method method) {
        return (RMIOperation<T>) operationCache.computeIfAbsent(method, m -> RMIOperation.valueOf(serviceName, m));
    }

    private Object invokeObjMethod(ObjectMethods method, Object proxy, Object[] parameters) {
        switch (method) {
        case TO_STRING:
            return "RMIProxy@" + Integer.toHexString(System.identityHashCode(proxy)) +
                "{service=" + serviceName +
                ", endpoint=" + clientPort.getEndpoint().getName() + '}';
        case EQUALS:
            return proxy == parameters[0];
        case HASH_CODE:
            return System.identityHashCode(proxy);
        default:
            throw new AssertionError();
        }
    }
}
