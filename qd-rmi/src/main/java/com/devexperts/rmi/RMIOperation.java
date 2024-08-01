/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi;

import com.devexperts.io.Marshaller;
import com.devexperts.rmi.task.RMIService;
import com.dxfeed.promise.Promise;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Identifier of a single remote operation.
 * An operation consists of:
 *  <ul>
 *      <li>Service name</li>
 *      <li>Method name</li>
 *      <li>Parameter types</li>
 *      <li>Result type</li>
 * </ul>
 * From these four components an unique operation {@link #getSignature() signature} is composed.
 */
public final class RMIOperation<T> {
    // ========== private static fields ==========

    private static final char SIGNATURE_SERVICE_NAME_SEP = '#';
    private static final char SIGNATURE_START_PARAMS_SEP = '(';
    private static final String SIGNATURE_END_PARAMS_SEP = "):";

    // ========== private instance fields ==========

    private final String signature;
    private final String serviceName;
    private final String methodName;
    private final Marshaller.Typed<Object[]> parametersMarshaller;
    private final Marshaller.Typed<T> resultMarshaller;

    // ========== Constructing methods ==========

    /**
     * Constructs an {@link RMIOperation} by specified service interface class and method.
     * @param serviceInterface {@link Class} of a service interface.
     * @param method {@link Method} that corresponds with the operation.
     * @return constructed {@link RMIOperation}.
     */
    public static <T> RMIOperation<T> valueOf(Class<?> serviceInterface, Method method) {
        return valueOf(RMIService.getServiceName(serviceInterface), method);
    }

    /**
     * Constructs an {@link RMIOperation} by specified service name and method.
     * @param serviceName name of a service.
     * @param method {@link Method} that corresponds with the operation.
     * @return constructed {@link RMIOperation}.
     * @throws IllegalArgumentException if serviceName contains '#' character.
     */
    @SuppressWarnings({"unchecked"})
    public static <T> RMIOperation<T> valueOf(String serviceName, Method method) {
        Class<?> resultClass = method.getReturnType();
        if (resultClass == Promise.class)
            resultClass = extractPromiseReturnType(method);
        RMIServiceMethod methodAnnotation = method.getAnnotation(RMIServiceMethod.class);
        String name = methodAnnotation != null && !methodAnnotation.name().isEmpty() ? methodAnnotation.name() :
            method.getName();
        return valueOf(serviceName, (Class<T>) resultClass, name, method.getParameterTypes());
    }

    private static Class<?> extractPromiseReturnType(Method method) {
        Type returnType = method.getGenericReturnType();
        if (!(returnType instanceof ParameterizedType)) // something line raw Promise type
            throw new IllegalArgumentException("Promise return type '" + returnType + "' of method '" + method + " is not parameterized");
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        assert actualTypeArguments.length == 1; // we know our promise class -- it has one type argument
        Type argument = actualTypeArguments[0];
        if (argument instanceof Class)
            return (Class<?>) argument;
        if (argument instanceof ParameterizedType)
            return (Class<?>) (((ParameterizedType) argument).getRawType());
        // something like Promise<?>
        throw new IllegalArgumentException("Promise return type '" + returnType + "' of method '" + method + " is not properly parameterized");
    }

    /**
     * Constructs an {@link RMIOperation} by specified service name, result type class,
     * method name and parameter type classes.
     * @param serviceName name of a service.
     * @param resultClass {@link Class} of result type.
     * @param methodName name of a method.
     * @param parameterClasses array with parameters type {@link Class classes}.
     * @return constructed {@link RMIOperation}.
     * @throws IllegalArgumentException if serviceName contains '#' character or methodName contains '('.
     */
    public static <T> RMIOperation<T> valueOf(String serviceName,
        Class<T> resultClass, String methodName, Class<?>... parameterClasses)
    {
        return new RMIOperation<>(serviceName, resultClass, methodName, parameterClasses);
    }

    /**
     * Constructs an {@link RMIOperation} by specified service interface, result type class,
     * method name and parameter type classes.
     * @param serviceInterface {@link Class} of a service interface.
     * @param resultClass {@link Class} of result type.
     * @param methodName name of a method.
     * @param parameterClasses array with parameters type {@link Class classes}.
     * @return constructed {@link RMIOperation}.
     * @throws IllegalArgumentException if serviceName contains '#' character or methodName contains '('.
     */
    public static <T> RMIOperation<T> valueOf(Class<?> serviceInterface,
        Class<T> resultClass, String methodName, Class<?>... parameterClasses)
    {
        return
            new RMIOperation<>(RMIService.getServiceName(serviceInterface), resultClass, methodName, parameterClasses);
    }

    /**
     * Constructs an {@link RMIOperation} by unique operation signature .
     * @param signature unique operation signature (see {@link #getSignature()}).
     * @return constructed {@link RMIOperation}.
     */
    public static <T> RMIOperation<T> valueOf(String signature) {
        return new RMIOperation<>(signature);
    }

    // ========== Getters ==========

    /**
     * Returns a full signature of this operation. A signature of an operation has the following format:
     * <br>
     * <tt>&lt;service name&gt;#&lt;method name&gt;(&lt;comma-separated parameter types&gt;):&lt;result type&gt;</tt>
     * <p>
     * All types are represented as declared in {@link Class#getName()} description.
     * <p>
     * For example:
     * <ul>
     * <li></tt>java.util.List#get(int):java.lang.Object</tt></li>
     * <li></tt>java.util.Collection#toArray([Ljava.lang.Object;):[Ljava.lang.Object;</tt></li>
     * <li></tt>someService#doStuff():void</tt></li>
     * </ul>
     *
     * @return a full signature of this operation.
     */
    public String getSignature() {
        return signature;
    }

    /**
     * Returns the service name of this operation.
     * @return the service name of this operation.
     */
    public String getServiceName() {
        return serviceName;
    }

    /**
     * Returns the method name of this operation.
     * @return the method name of this operation.
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Returns type-aware marshaller for this operation's parameters.
     * The result of this method can be used to get access to
     * parameters {@link Marshaller.Typed#getTypes() type}
     * and the corresponding {@link Marshaller.Typed#getClasses(ClassLoader) classes}.
     * @return type-aware marshaller for this operation's parameters.
     */
    public Marshaller.Typed<Object[]> getParametersMarshaller() {
        return parametersMarshaller;
    }

    /**
     * Returns type-aware marshaller for this operation's result.
     * The result of this method can be used to get access to
     * result {@link Marshaller.Typed#getTypes() type}
     * and the corresponding {@link Marshaller.Typed#getClasses(ClassLoader) class}.
     * @return type-aware marshaller for this operation's result.
     */
    public Marshaller.Typed<T> getResultMarshaller() {
        return resultMarshaller;
    }

    // ========== Implementation ==========

    /**
     * Indicates whether some other object is "equal to" this one.
     * Two RMIOperation objects are equal when their
     * {@link #getSignature() signature} is equal.
     * @return  {@code true} if this object is the same as the other argument; {@code false} otherwise.
     */
    public boolean equals(Object other) {
        return other instanceof RMIOperation<?> && ((RMIOperation<?>) other).signature.equals(signature);
    }

    /**
     * Returns a hash code value for the object that is composed of the hashcode of its
     * {@link #getSignature() signature}.
     * @return  a hash code value for this object.
     */
    public int hashCode() {
        return signature.hashCode();
    }

    /**
     * Returns the signature of this operation.
     * @return the signature of this operation.
     */
    public String toString() {
        // Note: It is use as a part of RMIExecutionTaskImpl.toString() method on processing failures
        return signature;
    }

    private RMIOperation(String serviceName, Class<T> resultClass, String methodName, Class<?>[] parameterClasses) {
        if (serviceName.indexOf(SIGNATURE_SERVICE_NAME_SEP) >= 0)
            throw new IllegalArgumentException("Invalid service name: " + serviceName);
        if (methodName.indexOf(SIGNATURE_START_PARAMS_SEP) >= 0)
            throw new IllegalArgumentException("Invalid method name: " + methodName);
        this.serviceName = serviceName;
        this.methodName = methodName;
        this.parametersMarshaller = Marshaller.forClasses(parameterClasses);
        this.resultMarshaller = Marshaller.forClass(resultClass);
        signature = serviceName +
            SIGNATURE_SERVICE_NAME_SEP + methodName +
            SIGNATURE_START_PARAMS_SEP + this.parametersMarshaller.getTypes() +
            SIGNATURE_END_PARAMS_SEP + resultClass.getName();
    }

    @SuppressWarnings("unchecked")
    private RMIOperation(String signature) {
        this.signature = signature;
        int i = signature.indexOf(SIGNATURE_SERVICE_NAME_SEP);
        int j = signature.indexOf(SIGNATURE_START_PARAMS_SEP, i + 1);
        int k = signature.indexOf(SIGNATURE_END_PARAMS_SEP, j + 1);
        this.serviceName = signature.substring(0, i);
        this.methodName = signature.substring(i + 1, j);
        this.parametersMarshaller = Marshaller.forTypes(signature.substring(j + 1, k));
        this.resultMarshaller = (Marshaller.Typed<T>) Marshaller.forType(signature.substring(k + SIGNATURE_END_PARAMS_SEP.length()));
    }
}
