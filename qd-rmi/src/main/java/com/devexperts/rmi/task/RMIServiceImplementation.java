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
package com.devexperts.rmi.task;

import com.devexperts.io.Marshalled;
import com.devexperts.io.SerialClassContext;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIServiceInterface;

import java.io.InvalidClassException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * This implementation {@link RMIService} can use the executor and the classLoader; <code>null</code> for default.
 * To define the data fields, use appropriate: {@code serviceImplementation.setExecutor} and
 * {@code serviceImplementation.setClassLoader}
 */
public final class RMIServiceImplementation<T> extends RMILocalService<T> {

    // ==================== private static methods ====================

    private static void trimStackTrack(Throwable cause, Method method) {
        // Exclude the bottom part of cause stack trace.
        // (It will be replaced by the caller stack trace bottom part at the client side.)
        StackTraceElement[] causeStackTrace = cause.getStackTrace();
        StackTraceElement[] environmentStackTrace = Thread.currentThread().getStackTrace();
        int causePos = causeStackTrace.length;
        int environmentPos = environmentStackTrace.length;
        while (causePos > 0 && environmentPos > 0 &&
            causeStackTrace[causePos - 1].equals(environmentStackTrace[environmentPos - 1]))
        {
            causePos--;
            environmentPos--;
        }
        String className = method.getDeclaringClass().getName();
        String methodName = method.getName();
        while (causePos > 0) {
            StackTraceElement element = causeStackTrace[causePos - 1];
            if (className.equals(element.getClassName()) && methodName.equals(element.getMethodName()))
                break;
            causePos--;
        }
        // now change cause's stack trace
        StackTraceElement[] truncatedStackTrace = new StackTraceElement[causePos];
        System.arraycopy(causeStackTrace, 0, truncatedStackTrace, 0, causePos);
        cause.setStackTrace(truncatedStackTrace);
    }


    // ==================== private fields ====================

    private final Class<T> serviceInterface;
    private final T implementation;
    private SerialClassContext serialContext;
    private volatile Map<String, Method> providingImplMethods;

    // ==================== public methods ====================

    /**
     * Set the ClassLoader that will be used to load classes.
     * @param loader the ClassLoader that will be used to load classes.
     * @deprecated use {@link #setSerialClassContext(SerialClassContext)}
     * and {@link SerialClassContext#getDefaultSerialContext(ClassLoader) SerialClassContext.getDefaultSerialContext(loader)}
     */
    public void setClassLoader(ClassLoader loader) {
        this.serialContext = SerialClassContext.getDefaultSerialContext(loader);
    }

    /**
     * Returns the that will be used to load classes.
     * @return the ClassLoader that will be used to load classes.
     * @deprecated use {@link #getSerialClassContext()} and {@link SerialClassContext#getClassLoader()}
     */
    public ClassLoader getClassLoader() {
        return serialContext.getClassLoader();
    }

    /**
     * Set the serial class context that will be used to load classes.
     * @param serialContext the serial class context that will be used to load classes.
     */
    public void setSerialClassContext(SerialClassContext serialContext) {
        this.serialContext = serialContext;
    }

    /**
     * Returns the that will be used to load classes.
     * @return the serial class context that will be used to load classes.
     */
    public SerialClassContext getSerialClassContext() {
        return serialContext;
    }


    /**
     * Sets executor that will be used by this service to execute the requests.
     * Note, that for backwards compatibility, if the executor implements {@link ExecutorService},
     * then it will be use only {@link ExecutorService#submit(Runnable)}
     * @param executor that will be used by this service to execute the requests.
     */
    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    /**
     * Constructs new <code>RMIServiceImplementation</code> with specified name
     *
     * @param implementation implementation of the serviceImplementation.
     * @param serviceInterface interface of the serviceImplementation.
     * @param serviceName a public name of this serviceImplementation.
     */
    public RMIServiceImplementation(T implementation, Class<T> serviceInterface, String serviceName) {
        this(implementation, serviceInterface, serviceName, null);
    }

    public RMIServiceImplementation(T implementation, Class<T> serviceInterface, String serviceName, Map<String, String> properties) {
        super(serviceName, properties);
        if (implementation == null)
            throw new NullPointerException("Implementation is null");
        if (!serviceInterface.isInterface())
            throw new IllegalArgumentException("Only interface can be exported");
        if (!serviceInterface.isInstance(implementation))
            throw new IllegalArgumentException("Exporting implementation does not implement the interface");
        this.serviceInterface = serviceInterface;
        this.implementation = implementation;
    }

    /**
     * Constructs new <code>RMIServiceImplementation</code> with default name.
     * When {@code serviceInterface} is annotated with {@link RMIServiceInterface},
     * then its {@link RMIServiceInterface#name() name} property is used as the name of the of the exported service.
     * By default, it is equal to the full name of a {@code serviceInterface}
     *
     * @param serviceInterface interface of the serviceImplementation.
     * @param implementation implementation of the serviceImplementation.
     */
    public RMIServiceImplementation(T implementation, Class<T> serviceInterface) {
        this(implementation, serviceInterface, getServiceName(serviceInterface));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public T invoke(final RMITask<T> task) throws Throwable {
        Method implMethod = getProvidingImplMethods().get(task.getOperation().getSignature());
        if (implMethod == null) {
            // we cannot throw exception from invoke, because it is interpreted as as an APPLICATION_EXCEPTION
            task.completeExceptionally(RMIExceptionType.OPERATION_NOT_PROVIDED, null);
            return null; // result is ignored, since the task is completed anyway
        }
        final Object[] parameters;
        try {
            Marshalled<Object[]> marshalledParameters = task.getRequestMessage().getParameters();
            if (serialContext != null)
                marshalledParameters = Marshalled.forBytes(marshalledParameters.getBytes(), marshalledParameters.getMarshaller(), serialContext);
            parameters = marshalledParameters.getObject();
        } catch (Throwable t) {
            task.completeExceptionally(RMIExceptionType.PARAMETERS_UNMARSHALLING_ERROR, t);
            return null;
        }
        try {
            return (T) implMethod.invoke(implementation, parameters);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            trimStackTrack(cause, implMethod);
            throw cause;
        } catch (IllegalAccessException e) {
            task.completeExceptionally(RMIExceptionType.ILLEGAL_ACCESS, e);
            return null; // result is ignored, since the task is completed anyway
        } catch (Throwable e) {
            // should not happen, but just in case...
            task.completeExceptionally(RMIExceptionType.EXECUTION_ERROR, e);
            return null; // result is ignored, since the task is completed anyway
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected RMIChannelSupport<T> channelSupport() {
        return implementation instanceof RMIChannelSupport ? (RMIChannelSupport<T>) implementation : null;
    }

    // ==================== private implementation ====================

    private synchronized Map<String, Method> fillProvidingImplMethodsSync() {
        Map<String, Method> providingImplMethods = this.providingImplMethods;
        if (providingImplMethods != null) // double check
            return providingImplMethods;
        providingImplMethods = new HashMap<>();
        for (Method method : serviceInterface.getMethods()) {
            RMIOperation<T> operation = RMIOperation.valueOf(serviceName, method);
            try {
                method = implementation.getClass().getMethod(method.getName(),
                    operation.getParametersMarshaller().getClasses(serialContext != null ? serialContext.getClassLoader() : null));
                method.setAccessible(true);
            } catch (NoSuchMethodException | InvalidClassException e) {
                throw new IllegalArgumentException(e);
            }
            providingImplMethods.put(operation.getSignature(), method); // previously added implementations are erased
        }
        // done, publish result (volatile write)
        return this.providingImplMethods = providingImplMethods;
    }

    private Map<String, Method> getProvidingImplMethods() {
        Map<String, Method> providingImplMethods = this.providingImplMethods; // volatile read
        if (providingImplMethods != null)
            return providingImplMethods;
        return fillProvidingImplMethodsSync();
    }
}
