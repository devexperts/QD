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

import com.devexperts.io.Marshalled;
import com.devexperts.rmi.impl.RMIRequestImpl;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.security.SecurityController;
import com.devexperts.util.IndexerFunction;

import java.lang.reflect.Proxy;

/**
 * The interface is responsible for the basic functionality of the client.
 * A {@link RMIClientPort} can create requests with a specified subject for remote service methods execution.
 *
 * <p> Subject is an <code>Object</code> that will be used in the {@link SecurityController}
 * on the server side to check security permissions for executing requests, which have been created this client port.
 */
public interface RMIClientPort {
    /**
     * Indexer for comparing ports for subject
     */
    public static final IndexerFunction<Marshalled<?>,RMIClientPort> INDEXER_BY_SUBJECT =
        (IndexerFunction<Marshalled<?>, RMIClientPort>) RMIClientPort::getSubject;

    /**
     * Returns the marshalled subject that is associated with this client port.
     * The result of this method will reflect thread's current subject for a <b>default port</b> of the
     * {@link RMIClient}. See {@link RMIClient#getPort(Object)}.
     *
     * @return the marshalled subject that is associated with this client port.
     * @see SecurityController
     * @see Marshalled
     */
    public Marshalled<?> getSubject();

    /**
     * Returns <tt>true</tt> if this port is open and can send requests.
     * @return <tt>true</tt> if this port is open.
     */
    public boolean isOpen();

    /**
     * Returns proxy for specified remote interface and service name.
     *
     * <p>The methods of returned proxy may throw {@link RMIException}s
     * if the corresponding service interface methods have it (or its ancestors)
     * declared to be thrown.
     * Otherwise, if {@link RMIException} occurs during underlying request
     * execution but it is not allowed to be thrown from corresponding service
     * interface method it is wrapped into {@link RuntimeRMIException} and thrown.
     *
     * <p>Methods of the {@code serviceInterface} may be annotated with {@link RMIServiceMethod}
     * to override remote method name and specify a {@link RMIRequestType type} of request that
     * is created when an invocation of this method via this proxy is made.
     *
     * @param serviceInterface a service interface.
     * @param serviceName a name which was used to export the service on the server side.
     * @return proxy for specified remote interface and service name.
     * @see Proxy
     * @see #getProxy(Class)
     */
    public <V> V getProxy(Class<V> serviceInterface, String serviceName);

    /**
     * Returns proxy for specified remote interface.
     *
     * <p>This method must be used only if the service was exported with
     * its default name on the server side. Otherwise one must use
     * {@link #getProxy(Class, String)}.
     * When {@code serviceInterface} is annotated with {@link RMIServiceInterface},
     * then its {@link RMIServiceInterface#name() name} property is used as the name of the of the service.
     * By default, it is equal to the full name of a {@code serviceInterface}
     *
     * <p>The methods of returned proxy may throw {@link RMIException}s
     * if the corresponding service interface methods have it (or its ancestors)
     * declared to be thrown.
     * Otherwise, if {@link RMIException} occurs during underlying request
     * execution but it is not allowed to be thrown from corresponding service
     * interface method it is wrapped into {@link RuntimeRMIException} and thrown.
     *
     * <p>Methods of the {@code serviceInterface} may be annotated with {@link RMIServiceMethod}
     * to override remote method name and specify a {@link RMIRequestType type} of request that
     * is created when an invocation of this method via this proxy is made.
     *
     * @param serviceInterface a service interface.
     * @return proxy for specified remote interface.
     * @see Proxy
     * @see #getProxy(Class, String)
     */
    public <V> V getProxy(Class<V> serviceInterface);

    /**
     * Creates a {@link RMIRequest request} for specified remote
     * {@link RMIOperation operation} and subject with given parameters.
     * This method is a shortcut for
     * <code>{@link #createRequest(RMIRequestType, RMIOperation, Object...)
     *   createRequest}({@link RMIRequestType#DEFAULT RMIRequestType.DEFAULT}, operation, parameters)</code>.
     *
     * @param operation {@link RMIOperation} that will be executed by this request.
     * @param parameters parameters that will be passed to executing operation method.
     * @return created {@link RMIRequest}.
     * @see #createRequest(RMIRequestMessage)
     */
    public default <T> RMIRequest<T> createRequest(RMIOperation<T> operation, Object... parameters) {
        return createRequest(RMIRequestType.DEFAULT, operation, parameters);
    }

    /**
     * Creates a {@link RMIRequest request} of a given {@link RMIRequestType type} for specified remote
     * {@link RMIOperation operation} and subject with given parameters.
     * This method is a shortcut for
     * <code>{@link #createRequest(RMIRequestMessage)
     *   createRequest}(new {@link RMIRequestMessage#RMIRequestMessage(RMIRequestType, RMIOperation, Object...)
     *     RMIRequestMessage}&lt;&gt;(type, operation, parameters))</code>.
     *
     * @param operation {@link RMIOperation} that will be executed by this request.
     * @param parameters parameters that will be passed to executing operation method.
     * @return created {@link RMIRequest}.
     * @see #createRequest(RMIRequestMessage)
     */
    public <T> RMIRequest<T> createRequest(RMIRequestType type, RMIOperation<T> operation, Object... parameters);

    /**
     * Creates a {@link RMIRequestImpl request} for specified remote
     * {@link RMIOperation operation} with given parameters.
     * @param message {@link RMIRequestMessage} that contains all information about this request.
     * @return created {@link RMIRequest}.
     * @see #createRequest(RMIOperation, Object...)
     */
    public <T> RMIRequest<T> createRequest(RMIRequestMessage<T> message);
}
