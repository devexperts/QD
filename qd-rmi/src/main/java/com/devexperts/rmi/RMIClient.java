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

import com.devexperts.rmi.impl.RMIRequestImpl;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.security.SecurityController;
import com.devexperts.rmi.task.RMIService;

import java.lang.reflect.Proxy;
import java.util.concurrent.Executor;

/**
 * The client side {@link RMIEndpoint endpoint}.
 * <p>
 * A client side endpoint can create requests for remote service methods execution
 * or provide stubs for remote services.
 *
 * @see RMIRequest
 */
public abstract class RMIClient {

    // ==================== public static fields ====================

    /**
     * The default value of timeout for requests sending.
     */
    public static final long DEFAULT_REQUEST_SENDING_TIMEOUT = 60 * 1000;

    /**
     * Name of the system property for default timeout for request sending
     */
    public static final String DEFAULT_REQUEST_SENDING_TIMEOUT_PROPERTY = "com.devexperts.rmi.RequestSendingTimeout";

    /**
     * The default value of timeout for requests execution.
     */
    public static final long DEFAULT_REQUEST_RUNNING_TIMEOUT = Long.MAX_VALUE;

    /**
     * Name of the system property for default timeout for request execution
     */
    public static final String DEFAULT_REQUEST_RUNNING_TIMEOUT_PROPERTY = "com.devexperts.rmi.RequestRunningTimeout";

    /**
     * The default value of maximum number of cached subjects per connection
     */
    public static final int DEFAULT_STORED_SUBJECTS_LIMIT = 100000;

    /**
     * Name of the system property for default value of maximum number of cached subjects per connection
     */
    public static final String DEFAULT_STORED_SUBJECTS_LIMIT_PROPERTY = "com.devexperts.rmi.StoredSubjectsLimit";

    /**
     * Returns proxy for specified remote interface.
     *
     * <p> This method must be used only if the service was exported with
     * its default name on the server side. Otherwise one must use
     * {@link #getProxy(Class, String)}.
     * When {@code serviceInterface} is annotated with {@link RMIServiceInterface},
     * then its {@link RMIServiceInterface#name() name} property is used as the name of the of the service.
     * By default, it is equal to the full name of a {@code serviceInterface}.
     *
     * <p> The methods of returned proxy may throw {@link RMIException}s
     * if the corresponding service interface methods have it (or its ancestors)
     * declared to be thrown.
     * Otherwise, if {@link RMIException} occurs during underlying request
     * execution but it is not allowed to be thrown from corresponding service
     * interface method it is wrapped into {@link RuntimeRMIException} and thrown.
     *
     * <p>This method is a shortcut for
     * <code>{@link #getPort(Object) getPort(null)}.{@link RMIClientPort#getProxy(Class)
     * getProxy(serviceInterface)}</code>.
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
    public abstract <T> T getProxy(Class<T> serviceInterface);

    /**
     * Returns proxy for specified remote interface and service name.
     *
     * <p> The methods of returned proxy may throw {@link RMIException}s
     * if the corresponding service interface methods have it (or its ancestors)
     * declared to be thrown.
     * Otherwise, if {@link RMIException} occurs during underlying request
     * execution but it is not allowed to be thrown from corresponding service
     * interface method it is wrapped into {@link RuntimeRMIException} and thrown.
     *
     * <p>This method is a shortcut for
     * <code>{@link #getPort(Object) getPort(null)}.{@link RMIClientPort#getProxy(Class, String)
     * getProxy(serviceInterface, serviceName)}</code>.
     *
     * <p>Methods of the {@code serviceInterface} may be annotated with {@link RMIServiceMethod}
     * to override remote method name and specify a {@link RMIRequestType type} of request that
     * is created when an invocation of this method via this proxy is made.
     *
     * @param serviceInterface a service interface.
     * @param serviceName a name which was used to export the service on the server side.
     * @return proxy for specified remote interface and service name.
     * @see Proxy
     */
    public abstract <T> T getProxy(Class<T> serviceInterface, String serviceName);

    /**
     * Returns the {@link RMIService} implementation, which allows to connect to the services on another {@link RMIServer}
     * May be used with wildcards to connect to several servers simultaneously.
     * The filter is a comma-separated list of services, each item can use at most one wildcard symbol "*" that matches
     * any sequence of characters.
     *
     * @param serviceName service name on another, may be used wildcards {@link RMIServer}
     * @return the {@link RMIService} implementation, which allows to connect to the services on another {@link RMIServer}
     */
    public abstract RMIService<?> getService(String serviceName);

    /**
     * Returns {@link RMIClientPort client port} with given subject.
     *
     * <p>{@code getPort(null)} returns <b>default port</b> -- the same port that is used to implement various
     * operations on this {@code RMIClient} implementation.
     *
     * @param subject an <code>Object</code> that will be used in the {@link SecurityController}
     * on the server side to check security permissions for executing this requests which have been created this port.
     * When {@code subject} is null, the the subject is taken from {@link SecurityController} at the thread that
     * creates request.
     *
     * @return client port with given subject.
     */
    public abstract RMIClientPort getPort(Object subject);

    /**
     * Creates a {@link RMIRequest request} for specified remote
     * {@link RMIOperation operation} with given subject and parameters.
     *
     * <p>This method is a shortcut for
     * <code>{@link #getPort(Object) getPort(subject)}.{@link RMIClientPort#createRequest(RMIOperation, Object...)
     * createRequest(operation, parameters)}</code>.
     *
     * @param subject an <code>Object</code> that will be used in the {@link SecurityController}
     * on the server side to check security permissions for executing this request.
     * @param operation {@link RMIOperation} that will be executed by this request.
     * @param parameters parameters that will be passed to executing operation method.
     * @return created {@link RMIRequest}.
     * @see #createRequest(RMIRequestMessage)
     */
    public abstract <T> RMIRequest<T> createRequest(Object subject, RMIOperation<T> operation, Object... parameters);

    /**
     * Creates a one-way {@link RMIRequestImpl request} for specified remote
     * {@link RMIOperation operation} with given subject and parameters.
     *
     *  <p> The one-way request becomes {@link RMIRequestState#SUCCEEDED SUCCEEDED}
     * right after it was sent and it does not receive any respond about its actual
     * remote execution (even in case in fact it fails) unlike the ordinary two-way
     * request.
     *
     * @param subject an <code>Object</code> that will be used in the
     * {@link SecurityController} on the server side to check
     * security permissions for executing this request.
     * @param operation {@link RMIOperation} that will be executed by this request.
     * @param parameters parameters that will be passed to executing operation method.
     * @return created {@link RMIRequestImpl}.
     *
     * @see #createRequest(Object, RMIOperation, Object[])
     * @see #createRequest(RMIRequestMessage)
     * @see #getPort(Object)
     *
     * @deprecated Use <code>{@link #getPort(Object) getPort(subject)}.{@link RMIClientPort#createRequest(RMIRequestType, RMIOperation, Object...)
     *     createRequest}({@link RMIRequestType#ONE_WAY}, operation, parameters)</code>
     */
    public abstract <T> RMIRequest<T> createOneWayRequest(Object subject, RMIOperation<T> operation, Object... parameters);

    /**
     * Creates a {@link RMIRequestImpl request} for specified remote
     * {@link RMIOperation operation} with given subject and parameters.
     *
     * <p>This method is a shortcut for
     * <code>{@link #getPort(Object) getPort(null)}.{@link RMIClientPort#createRequest(RMIRequestMessage)
     * createRequest(message)}</code>.
     *
     * @param message {@link RMIRequestMessage} that contains all information about this request.
     * @return created {@link RMIRequestImpl}.
     * @see #createRequest(Object, RMIOperation, Object...)
     * @see #getPort(Object)
     */
    public abstract <T> RMIRequest<T> createRequest(RMIRequestMessage<T> message);

    /**
     * Sets the specific {@link Executor} for perform notifications {@link RMIRequestListener} for {@link RMIRequest}
     * that creates this client.
     * @param executor for perform notifications {@link RMIRequestListener} for {@link RMIRequest}
     * that creates this client.
     */
    public abstract void setDefaultExecutor(Executor executor);

    /**
     * Returns the default {@link Executor} that is used for perform notifications {@link RMIRequestListener}
     * for {@link RMIRequest} that creates this client. By default used executor that is shared with server-side.
     *
     * <p><b>This method must not use any locks that are not terminal in the lock hierarchy to ensure deadlock-freedom.</b>
     *
     * @return Returns the default {@link Executor} that is used for perform notifications {@link RMIRequestListener}
     * for {@link RMIRequest} that creates this client
     */
    public abstract Executor getDefaultExecutor();

    /**
     * Sets the timeout for requests sending.
     * <p> If a request could not be sent within this timeout then
     * it will fail with an {@link RMIException} of type
     * {@link RMIExceptionType#REQUEST_SENDING_TIMEOUT}.
     * <p> The default value of this timeout is taken from system property
     * <tt>"{@value RMIClient#DEFAULT_REQUEST_SENDING_TIMEOUT_PROPERTY}"</tt>.
     * If the property is not defined then it is equal to
     * <tt>{@value RMIClient#DEFAULT_REQUEST_SENDING_TIMEOUT}</tt> ms.
     * @param timeout new request sending timeout in milliseconds.
     */
    public abstract void setRequestSendingTimeout(long timeout);

    /**
     * Returns the timeout for requests sending.
     * <p> If a request could not be sent within this timeout then
     * it will fail with an {@link RMIException} of type
     * {@link RMIExceptionType#REQUEST_SENDING_TIMEOUT}.
     * <p> The default value of this timeout is taken from system property
     * <tt>"{@value RMIClient#DEFAULT_REQUEST_SENDING_TIMEOUT_PROPERTY}"</tt>.
     * If the property is not defined then it is equal to
     * <tt>{@value RMIClient#DEFAULT_REQUEST_SENDING_TIMEOUT}<tt> ms.
     * @return current request sending timeout in milliseconds.
     */
    public abstract long getRequestSendingTimeout();

    /**
     * Sets the timeout for requests execution.
     * <p> If a request execution result is not received within this timeout
     * then it will fail with an {@link RMIException} of type
     * {@link RMIExceptionType#REQUEST_RUNNING_TIMEOUT}.
     * <p> The default value of this timeout is taken from system property
     * <tt>"{@value RMIClient#DEFAULT_REQUEST_RUNNING_TIMEOUT_PROPERTY}"</tt>.
     * If the property is not defined then it is equal to
     * <tt>{@value RMIClient#DEFAULT_REQUEST_RUNNING_TIMEOUT}</tt> ms.
     * @param timeout new request running timeout in milliseconds.
     */
    public abstract void setRequestRunningTimeout(long timeout);

    /**
     * Returns the timeout for requests execution.
     * <p> If a request execution result is not received within this timeout
     * then it will fail with an {@link RMIException} of type
     * {@link RMIExceptionType#REQUEST_RUNNING_TIMEOUT}.
     * <p> The default value of this timeout is taken from system property
     * <tt>"{@value RMIClient#DEFAULT_REQUEST_RUNNING_TIMEOUT_PROPERTY}"</tt>.
     * If the property is not defined then it is equal to
     * <tt>{@value RMIClient#DEFAULT_REQUEST_RUNNING_TIMEOUT}</tt> ms.
     * @return current request running timeout in milliseconds.
     */
    public abstract long getRequestRunningTimeout();

    /**
     * Sets the maximum number of cached subjects per connection.
     * This property is used on the client side.
     * <p> The default value of this limit is taken from system property
     * <tt>"{@value RMIClient#DEFAULT_STORED_SUBJECTS_LIMIT_PROPERTY}"</tt>.
     * If the property is not defined then it is equal to
     * <tt>{@value RMIClient#DEFAULT_STORED_SUBJECTS_LIMIT}</tt>.
     * @param limit new maximum number of cached subjects per connection.
     */
    public abstract void setStoredSubjectsLimit(int limit);

    /**
     * Returns the maximum number of cached subjects per connection.
     * This property is used on the client side.
     * <p> The default value of this limit is taken from system property
     * <tt>"{@value RMIClient#DEFAULT_STORED_SUBJECTS_LIMIT_PROPERTY}"</tt>.
     * If the property is not defined then it is equal to
     * <tt>{@value RMIClient#DEFAULT_STORED_SUBJECTS_LIMIT}</tt>.
     * @return current maximum number of cached subjects per connection.
     */
    public abstract int getStoredSubjectsLimit();

    /**
     * Returns the number of requests currently pending for sending in a queue.
     * @return the number of requests currently pending for sending in a queue.
     */
    public abstract int getSendingRequestsQueueLength();
}
