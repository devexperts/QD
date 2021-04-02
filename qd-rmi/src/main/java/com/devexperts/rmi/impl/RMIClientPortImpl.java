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
package com.devexperts.rmi.impl;

import com.devexperts.io.Marshalled;
import com.devexperts.rmi.RMIClientPort;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.message.RMIRoute;
import com.devexperts.rmi.task.RMIService;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;

abstract class RMIClientPortImpl implements RMIClientPort {

    // ==================== fields ====================

    @Nullable
    private final Marshalled<?> subject; // null means "get subject at the request creation time"

    private final RMIEndpointImpl endpoint;

    @GuardedBy("this")
    private Map<String, Object> cachedProxies; // initialized on first need

    // ==================== constructor ====================

    protected RMIClientPortImpl(RMIEndpointImpl endpoint, @Nullable Marshalled<?> subject) {
        this.subject = subject;
        this.endpoint = endpoint;
    }

    // ==================== methods ====================

    protected abstract RequestSender getRequestSender();

    @Override
    public Marshalled<?> getSubject() {
        if (subject != null)
            return subject;
        return Marshalled.forObject(endpoint.getSecurityController().getSubject());
    }

    @Override
    public boolean isOpen() {
        return !endpoint.getQdEndpoint().isClosed();
    }

    @Override
    @SuppressWarnings("unchecked")
    public synchronized  <V> V getProxy(Class<V> serviceInterface, String serviceName) {
        if (cachedProxies == null)
            cachedProxies = new HashMap<>();
        V proxy = (V) cachedProxies.get(serviceName);
        if (proxy == null) {
            proxy = (V) Proxy.newProxyInstance(serviceInterface.getClassLoader(), new Class<?>[] {serviceInterface},
                new RMIRequestInvocationHandler(this, serviceName, getObjectOverrideMethods(serviceInterface)));
            cachedProxies.put(serviceName, proxy);
        }
        if (!serviceInterface.isInstance(proxy))
            throw new IllegalArgumentException("Wrong interface");
        return proxy;
    }

    @Override
    public <V> V getProxy(Class<V> serviceInterface) {
        return getProxy(serviceInterface, RMIService.getServiceName(serviceInterface));
    }

    @Override
    public <T> RMIRequest<T> createRequest(RMIRequestType type, RMIOperation<T> operation, Object... parameters) {
        return new RMIRequestImpl<>(getRequestSender(), getSubject(),
            createRequestMessage(type, operation, parameters));
    }

    @Override
    public <T> RMIRequest<T> createRequest(RMIRequestMessage<T> message) {
        return new RMIRequestImpl<>(getRequestSender(), getSubject(), updateRequestMessage(message));
    }

    private static <T> EnumSet<ObjectMethods> getObjectOverrideMethods(Class<T> serviceInterface) {
        EnumSet<ObjectMethods> result = EnumSet.noneOf(ObjectMethods.class);
        for (Method method : serviceInterface.getMethods()) {
            if (ObjectMethods.getMethod(method) != null)
                result.add(ObjectMethods.getMethod(method));
        }
        return result;
    }

    protected final RMIEndpointImpl getEndpoint() {
        return endpoint;
    }

    @Nonnull
    protected final <T> RMIRequestMessage<T> createRequestMessage(RMIRequestType type, RMIOperation<T> operation,
        Object[] parameters)
    {
        RMIRoute route = RMIRoute.createRMIRoute(endpoint.getEndpointId());
        return new RMIRequestMessage<>(type, operation,
            Marshalled.forObject(parameters, operation.getParametersMarshaller()), route, null);
    }

    @Nonnull
    protected final <T> RMIRequestMessage<T> updateRequestMessage(RMIRequestMessage<T> message) {
        RMIRoute route = message.getRoute().append(endpoint.getEndpointId());
        return message.changeTargetRoute(message.getTarget(), route);
    }
}
