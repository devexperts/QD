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

import com.devexperts.io.SerialClassContext;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.qtp.socket.ClientSocketConnector;
import com.devexperts.rmi.impl.RMIClientImpl;
import com.devexperts.rmi.impl.RMIRequestImpl;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.message.RMIResponseMessage;
import com.devexperts.rmi.security.SecurityController;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceImplementation;
import com.devexperts.services.Service;
import com.devexperts.services.Services;
import com.devexperts.transport.stats.EndpointStats;
import com.dxfeed.api.DXEndpoint;

import java.io.Closeable;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

/**
 * The endpoint of RMI framework.
 * <p/>
 * <p>An endpoint is used on either a {@link RMIClient client} or {@link RMIServer server} side. RMIEndpoint can only have
 * the server side ({@link Side#SERVER}) or client side only ({@link Side#CLIENT}) or
 * both simultaneously ({@link Side#CLIENT_SERVER})
 * It connects to another endpoint at specified address. A server side endpoint ({@link Side#SERVER} or
 * {@link Side#CLIENT_SERVER}) can export some {@link RMIService} for execution, and a client side
 * endpoint ({@link Side#SERVER} or {@link Side#CLIENT_SERVER}) can create requests for remote service methods execution
 * or provide stubs for remote services.
 *
 * @see Side
 * @see RMIServer
 * @see RMIClient
 */
public abstract class RMIEndpoint implements Closeable {
    // ==================== static field API ====================

    /**
     * Defines property for endpoint name that is used to distinguish multiple endpoints
     * in the same JVM in logs and in other diagnostic means.
     * Use {@link Builder#withProperty(String, String)} method.
     * This property is also changed by {@link Builder#withName(String)} method.
     */
    public static final String NAME_PROPERTY = QDEndpoint.NAME_PROPERTY;

    // ==================== RMIEndpoint Side ====================

    /**
     * Represents the side of this RMI endpoint that was specified during its {@link RMIEndpoint#createEndpoint()} creation}.
     *
     * @see RMIEndpoint
     */
    public enum Side {
        /**
         * Null object (does not support RMI).
         */
        NONE(false, false),

        /**
         * {@code CLIENT} RMI endpoint has only the client side ({@link RMIClient}). It can only send
         * {@link RMIRequestImpl requests} to the remote RMI endpoint. {@link RMIEndpoint#getServer()} method
         * throws {@link IllegalStateException} as well as legacy server-side methods on RMI endpoint.
         */
        CLIENT(true, false),

        /**
         * {@code SERVER} RMI endpoint has only the server side ({@link RMIServer}). It can only send
         * {@link RMIResponseMessage response messages} to the remote RMI endpoint or export {@link RMIService}.
         * {@link RMIEndpoint#getClient()} method throws {@link IllegalStateException} as well as
         * legacy client-side methods on RMI endpoint.
         */
        SERVER(false, true),

        /**
         * {@code CLIENT_SERVER} RMI endpoint has both server and client sides (<b>this is a default</b>).
         */
        CLIENT_SERVER(true, true);

        private final boolean client;
        private final boolean server;

        Side(boolean client, boolean server) {
            this.client = client;
            this.server = server;
        }

        /**
         * Returns true when this side supports client side operations.
         *
         * @return true when this side supports client side operations.
         */
        public boolean hasClient() {
            return client;
        }

        /**
         * Returns true when this side supports server side operations.
         *
         * @return true when this side supports server side operations.
         */
        public boolean hasServer() {
            return server;
        }

        /**
         * Returns side that {@link #hasClient() has client}.
         *
         * @return side that {@link #hasClient() has client}.
         */
        public Side withClient() {
            return client ? this : this == NONE ? CLIENT : CLIENT_SERVER;
        }

        /**
         * Returns side that {@link #hasServer() has server}.
         *
         * @return side that {@link #hasServer() has server}.
         */
        public Side withServer() {
            return server ? this : this == NONE ? SERVER : CLIENT_SERVER;
        }
    }

    // ==================== static Common API ====================

    /**
     * Creates new {@link Builder} instance.
     * Use {@link Builder#build()} to build an instance of {@link RMIEndpoint} when
     * all configuration properties were set.
     *
     * @return the created endpoint builder.
     */
    public static Builder newBuilder() {
        Builder builder = Services.createService(Builder.class, null, null);
        if (builder == null)
            throw new IllegalStateException("There is no " + Builder.class + " implementation service in class path");
        return builder;
    }

    /**
     * Creates new {@link RMIEndpoint}.
     * This is a shortcut to
     * {@link #newBuilder() newBuilder()}.{@link Builder#build() build()}
     * This endpoint does not have an attached {@link DXEndpoint} and has {@link Side#CLIENT_SERVER CLIENT_SERVER} side.
     *
     * @return newly created {@link RMIEndpoint}.
     */
    public static RMIEndpoint createEndpoint() {
        return newBuilder().build();
    }

    /**
     * Creates new {@link RMIEndpoint} with attached {@link DXEndpoint} with specified dxRole.
     * This is a shortcut to
     * {@link #newBuilder() newBuilder()}.{@link Builder#withRole(DXEndpoint.Role) withRole(dxRole)}.{@link Builder#build() build()}
     * This endpoint has {@link Side#CLIENT_SERVER CLIENT_SERVER} side.
     *
     * @return newly created {@link RMIEndpoint}.
     */
    public static RMIEndpoint createEndpoint(DXEndpoint.Role role) {
        return newBuilder().withRole(role).build();
    }

    /**
     * Creates new {@link RMIEndpoint} with specified side
     * ({@link Side#SERVER SERVER}, or {@link Side#CLIENT CLIENT}, or {@link Side#CLIENT_SERVER CLIENT_SERVER}).
     * This is a shortcut to
     * {@link #newBuilder() newBuilder()}.{@link Builder#withSide(Side) withSide(side)}.{@link Builder#build() build()}
     *
     * @param side the side of new endpoint.
     * @return newly created {@link RMIEndpoint}.
     */
    public static RMIEndpoint createEndpoint(Side side) {
        return newBuilder().withSide(side).build();
    }

    // ==================== Common API ====================

    /**
     * Connects the endpoint to specified address(es) (after disconnecting from all
     * previously used addresses).
     *
     * @param address address(es) to connect to.
     */
    public abstract void connect(String address);

    /**
     * Reconnects the endpoint.
     *
     * <p>Will drop and reestablish remote connections using the same address as specified in {@link #connect(String)}.
     * Does nothing if endpoint was not previously connected or was disconnected with {@link #disconnect()}.
     */
    public abstract void reconnect();

    /**
     * Disconnects the endpoint.
     * <p/>
     * <p>This method does not release all resources that are associated with this endpoint.
     * Use {@link #close()} method to release all resources.
     */
    public abstract void disconnect();

    /**
     * Closes this endpoint. All network connection are terminated as with
     * {@link #disconnect() disconnect} method and no further connections can be established.
     * All resources associated with this endpoint are released.
     */
    public abstract void close();

    /**
     * Returns <tt>true</tt> if there exists at least one active connection
     * for this endpoint.
     *
     * @return <tt>true</tt> if there exists at least one active connection
     * for this endpoint.
     */
    public abstract boolean isConnected();

    /**
     * Returns statistics for this endpoint.
     *
     * @return statistics for this endpoint
     */
    public abstract EndpointStats getEndpointStats();

    /**
     * Adds the specified {@link RMIEndpointListener listener} to this endpoint.
     *
     * @param listener newly adding {@link RMIEndpointListener}.
     */
    public abstract void addEndpointListener(RMIEndpointListener listener);

    /**
     * Removes the specified {@link RMIEndpointListener listener} from this endpoint
     * listener list.
     *
     * @param listener removing {@link RMIEndpointListener}.
     */
    public abstract void removeEndpointListener(RMIEndpointListener listener);

    /**
     * Sets serial class context.
     *
     * @param serialClassContext the serial class context.
     */
    public abstract void setSerialClassContext(SerialClassContext serialClassContext);

    /**
     * Returns the serial class context.
     *
     * @return the serial class context.
     */
    public abstract SerialClassContext getSerialClassContext();

    /**
     * Returns the {@link SecurityController security controller} used
     * by this endpoint.
     *
     * @return the {@link SecurityController security controller} used
     * by this endpoint.
     */
    public abstract SecurityController getSecurityController();

    /**
     * Sets the {@link SecurityController security controller} to be used
     * by this endpoint.
     *
     * @param securityController {@link SecurityController} to be used
     *                           by this endpoint.
     */
    public abstract void setSecurityController(SecurityController securityController);

    /**
     * Returns {@link DXEndpoint} associated with this RMI endpoint.
     *
     * @throws IllegalStateException if there is no associated DXEndpoint.
     */
    public abstract DXEndpoint getDXEndpoint();

    /**
     * Returns {@link MessageAdapter.Factory factory} for message adapters
     * that will be used to handle non-rmi messages.
     *
     * @return {@link MessageAdapter.Factory factory} for message adapters
     * that will be used to handle non-rmi messages.
     *
     * @deprecated Use {@link Builder#withRole(DXEndpoint.Role)} to combine RMI and QD connections.
     */
    public abstract MessageAdapter.Factory getAttachedMessageAdapterFactory();

    /**
     * Sets {@link MessageAdapter.Factory factory} for message adapters
     * that will be used to handle non-rmi messages.
     *
     * @param attachedMessageAdapterFactory {@link MessageAdapter.Factory factory}
     *                                      for message adapters that will be used to handle non-rmi messages.
     * @deprecated Use {@link Builder#withRole(DXEndpoint.Role)} to combine RMI and QD connections.
     */
    public abstract void setAttachedMessageAdapterFactory(MessageAdapter.Factory attachedMessageAdapterFactory);

    /**
     * Sets custom {@link TrustManager trust manager} for {@link ClientSocketConnector}.
     * It works only with {@link ClientSocketConnector} with tls (address format "<code>tls+&lt;host&gt;:&lt;port&gt;</code>").
     * <p/>
     * It accepts {@code null} argument, in which case it is reset to use default trust manager.
     *
     * @param trustManager trust manager to be used by {@link SSLContext}.
     * @see ClientSocketConnector#setTrustManager(TrustManager)
     */
    public abstract void setTrustManager(TrustManager trustManager);

    /**
     * Returns name of this RMI endpoint
     *
     * @return name of this RMI endpoint
     */
    public abstract String getName();

    /**
     * Returns side of this RMI endpoint
     */
    public abstract Side getSide();

    /**
     * Returns {@link RMIServer server} of this RMI endpoint
     *
     * @return server of this RMI endpoint
     */
    public abstract RMIServer getServer();

    /**
     * Returns {@link RMIClient client} of this RMI endpoint
     *
     * @return client of this RMI endpoint
     */
    public abstract RMIClient getClient();

    // ==================== server API ====================

    /**
     * Exports a {@link RMIServiceImplementation} with default name.
     * The name of the exporting service will be equal to the full name of a serviceInterface
     * ({@code serviceInterface.getName()}).
     *
     * @param implementation   implementation of the exporting service.
     * @param serviceInterface interface of the exporting service.
     * @see #export(Object, Class, ExecutorService)
     * @see #export(Object, Class, String, ExecutorService)
     * @deprecated Use {@link RMIServer#export(Object, Class)}.
     */
    public abstract <T> void export(T implementation, Class<T> serviceInterface);

    /**
     * Exports a {@link RMIServiceImplementation} with default name and specified {@link ExecutorService executor}.
     * The name of the exporting service will be equal to the full name of a serviceInterface
     * ({@code serviceInterface.getName()}).
     * <p/>
     * <p> For example, the following code is equal to calling
     * <code> endpoint.export(implementation, serviceInterface, executor) </code>:
     * <pre>
     * RMIService service = new RMIImplementationService(implementation, serviceInterface);
     * service.setExecutor(executor);
     * endpoint.getServer().export(service);
     * </pre>
     *
     * @param implementation   implementation of the exporting service.
     * @param serviceInterface interface of the exporting service.
     * @param executor         {@link ExecutorService} that will be used to execute this service
     *                         requests.
     * @see #export(Object, Class)
     * @see #export(Object, Class, String, ExecutorService)
     * @deprecated use {@link RMIServer#export(RMIService)}.
     */
    public abstract <T> void export(T implementation, Class<T> serviceInterface, ExecutorService executor);

    /**
     * Exports a {@link RMIServiceImplementation} with specified name.
     *
     * @param implementation   implementation of the exporting service.
     * @param serviceInterface interface of the exporting service.
     * @param serviceName      a public name of this service.
     * @see #export(Object, Class)
     * @see #export(Object, Class, ExecutorService)
     * @see #export(Object, Class, String, ExecutorService)
     * @deprecated Use {@link RMIServer#export(RMIService)} and
     * {@link RMIServiceImplementation#RMIServiceImplementation(Object, Class, String)
     * RMIServiceImplementation(Object, Class, String)} or use {@link RMIServiceInterface} annotation.
     */
    public abstract <T> void export(T implementation, Class<T> serviceInterface, String serviceName);

    /**
     * Exports a {@link RMIServiceImplementation} with specified name and {@link ExecutorService executor}.
     * <p/>
     * <p> For example, the following code is equal to calling
     * <code> endpoint.export(implementation, serviceInterface, serviceName, executor) </code>:
     * <pre>
     * RMIService service = new RMIImplementationService(implementation, serviceInterface, serviceName);
     * service.setExecutor(executor);
     * endpoint.getServer().export(service);
     * </pre>
     *
     * @param implementation   implementation of the exporting service.
     * @param serviceInterface interface of the exporting service.
     * @param serviceName      a public name of this service.
     * @param executor         {@link ExecutorService} that will be used to execute this service
     * @see #export(Object, Class)
     * @see #export(Object, Class, ExecutorService)
     * @deprecated Use {@link RMIServer#export(RMIService)}
     */
    public abstract <T> void export(T implementation, Class<T> serviceInterface, String serviceName, ExecutorService executor);

    /**
     * Returns the default {@link ExecutorService} that is commonly used
     * by this endpoint to execute the requests.
     *
     * <p>This method delegates to {@link RMIServer#getDefaultExecutor()}.
     *
     * @return the default {@link ExecutorService} that is commonly used
     * by this endpoint to execute the requests.
     * @see #setDefaultExecutor(ExecutorService)
     * @deprecated use {@link RMIServer#getDefaultExecutor()}
     */
    public abstract ExecutorService getDefaultExecutor();

    /**
     * Sets default {@link ExecutorService} that will be commonly used
     * by this endpoint to execute the requests.
     * <p/>
     * In order to use special executors (different from the default one)
     * for some services operations one should export these services using
     * one of two methods: {@link #export(Object, Class, ExecutorService)}
     * or {@link #export(Object, Class, String, ExecutorService)}.
     *
     * <p>This method delegates to {@link RMIServer#setDefaultExecutor(Executor)}.
     *
     * @param executor default {@link ExecutorService} that will be commonly
     *                 used by this endpoint to execute the requests.
     * @see #getDefaultExecutor()
     * @deprecated Use {@link RMIServer#setDefaultExecutor(Executor)}
     */
    public abstract void setDefaultExecutor(ExecutorService executor);

    // ==================== Client API ====================

    /**
     * Returns the stub for specified remote interface.
     * <p> This method must be used only if the service was exported with
     * its default name on the server side. Otherwise one must use
     * {@link #getProxy(Class, String)}.
     * <p> The methods of returned stub may throw {@link RMIException}s
     * if the corresponding service interface methods have it (or its ancestors)
     * declared to be thrown.
     * Otherwise, if {@link RMIException} occurs during underlying request
     * execution but it is not allowed to be thrown from corresponding service
     * interface method it is wrapped into {@link RuntimeRMIException} and thrown.
     *
     * @param serviceInterface a service interface.
     * @return the stub for specified remote interface.
     * @see Proxy
     * @deprecated Use {@link RMIClient#getProxy(Class)}
     */
    public abstract <T> T getProxy(Class<T> serviceInterface);

    /**
     * Returns the stub for specified remote interface and service name.
     * <p> The methods of returned stub may throw {@link RMIException}s
     * if the corresponding service interface methods have it (or its ancestors)
     * declared to be thrown.
     * Otherwise, if {@link RMIException} occurs during underlying request
     * execution but it is not allowed to be thrown from corresponding service
     * interface method it is wrapped into {@link RuntimeRMIException} and thrown.
     *
     * @param serviceInterface a service interface.
     * @param serviceName      a name which was used to export the service on the server side.
     * @return the stub for specified remote interface and service name.
     * @see Proxy
     * @deprecated Use {@link RMIClient#getProxy(Class, String)}
     */
    public abstract <T> T getProxy(Class<T> serviceInterface, String serviceName);

    /**
     * Creates a {@link RMIRequestImpl request} for specified remote
     * {@link RMIOperation operation} with given subject and parameters.
     *
     * @param subject    an <code>Object</code> that will be used in the {@link SecurityController}
     *                   on the server side to check security permissions for executing this request.
     * @param operation  {@link RMIOperation} that will be executed by this request.
     * @param parameters parameters that will be passed to executing operation method.
     * @return created {@link RMIRequestImpl}.
     * @see #createOneWayRequest(Object, RMIOperation, Object[])
     * @deprecated Use {@link RMIClient#createRequest(Object, RMIOperation, Object...)}
     */
    public abstract <T> RMIRequest<T> createRequest(Object subject, RMIOperation<T> operation, Object... parameters);

    /**
     * Creates a one-way {@link RMIRequestImpl request} for specified remote
     * {@link RMIOperation operation} with given subject and parameters.
     * <p> The one-way request becomes {@link RMIRequestState#SUCCEEDED SUCCEEDED}
     * right after it was sent and it does not receive any respond about its actual
     * remote execution (even in case in fact it fails) unlike the ordinary two-way
     * request.
     *
     * @param subject    an <code>Object</code> that will be used in the
     *                   {@link SecurityController} on the server side to check
     *                   security permissions for executing this request.
     * @param operation  {@link RMIOperation} that will be executed by this request.
     * @param parameters parameters that will be passed to executing operation method.
     * @return created {@link RMIRequestImpl}.
     * @see #createRequest(Object, RMIOperation, Object[])
     * @deprecated Use {@link RMIClient#createRequest(RMIRequestMessage)} and
     * {@link RMIRequestMessage#RMIRequestMessage(RMIRequestType, RMIOperation, Object...)
     * RMIRequestMessage(RMIRequestType, RMIOperation, Object, Object...)}
     */
    public abstract <T> RMIRequest<T> createOneWayRequest(Object subject, RMIOperation<T> operation, Object... parameters);

    /**
     * Sets the timeout for requests sending.
     * <p> If a request could not be sent within this timeout then
     * it will fail with an {@link RMIException} of type
     * {@link RMIExceptionType#REQUEST_SENDING_TIMEOUT}.
     * <p> The default value of this timeout is taken from system property
     * <tt>"{@value RMIClientImpl#DEFAULT_REQUEST_SENDING_TIMEOUT_PROPERTY}"</tt>.
     * If the property is not defined then it is equal to
     * <tt>{@value RMIClientImpl#DEFAULT_REQUEST_SENDING_TIMEOUT}</tt> ms.
     *
     * @param timeout new request sending timeout in milliseconds.
     * @deprecated Use {@link RMIClient#setRequestSendingTimeout(long)})}
     */
    public abstract void setRequestSendingTimeout(long timeout);

    /**
     * Returns the timeout for requests sending.
     * <p> If a request could not be sent within this timeout then
     * it will fail with an {@link RMIException} of type
     * {@link RMIExceptionType#REQUEST_SENDING_TIMEOUT}.
     * <p> The default value of this timeout is taken from system property
     * <tt>"{@value RMIClientImpl#DEFAULT_REQUEST_SENDING_TIMEOUT_PROPERTY}"</tt>.
     * If the property is not defined then it is equal to
     * <tt>{@value RMIClientImpl#DEFAULT_REQUEST_SENDING_TIMEOUT}</tt> ms.
     *
     * @return current request sending timeout in milliseconds.
     * @deprecated Use {@link RMIClient#getRequestSendingTimeout()}
     */
    public abstract long getRequestSendingTimeout();

    /**
     * Sets the timeout for requests execution.
     * <p> If a request execution result is not received within this timeout
     * then it will fail with an {@link RMIException} of type
     * {@link RMIExceptionType#REQUEST_RUNNING_TIMEOUT}.
     * <p> The default value of this timeout is taken from system property
     * <tt>"{@value RMIClientImpl#DEFAULT_REQUEST_RUNNING_TIMEOUT_PROPERTY}"</tt>.
     * If the property is not defined then it is equal to
     * <tt>{@value RMIClientImpl#DEFAULT_REQUEST_RUNNING_TIMEOUT}</tt> ms.
     *
     * @param timeout new request running timeout in milliseconds.
     * @deprecated Use {@link RMIClient#setRequestRunningTimeout(long)}
     */
    public abstract void setRequestRunningTimeout(long timeout);

    /**
     * Returns the timeout for requests execution.
     * <p> If a request execution result is not received within this timeout
     * then it will fail with an {@link RMIException} of type
     * {@link RMIExceptionType#REQUEST_RUNNING_TIMEOUT}.
     * <p> The default value of this timeout is taken from system property
     * <tt>"{@value RMIClientImpl#DEFAULT_REQUEST_RUNNING_TIMEOUT_PROPERTY}"</tt>.
     * If the property is not defined then it is equal to
     * <tt>{@value RMIClientImpl#DEFAULT_REQUEST_RUNNING_TIMEOUT}<tt> ms.
     *
     * @return current request running timeout in milliseconds.
     * @deprecated Use {@link RMIClient#getRequestRunningTimeout()}
     */
    public abstract long getRequestRunningTimeout();

    /**
     * Sets the maximum number of cached subjects per connection.
     * This property is used on the client side.
     * <p> The default value of this limit is taken from system property
     * <tt>"{@value RMIClientImpl#DEFAULT_STORED_SUBJECTS_LIMIT_PROPERTY}"</tt>.
     * If the property is not defined then it is equal to
     * <tt>{@value RMIClientImpl#DEFAULT_STORED_SUBJECTS_LIMIT}<tt>.
     *
     * @param limit new maximum number of cached subjects per connection.
     * @deprecated Use {@link RMIClient#setStoredSubjectsLimit(int)}
     */
    public abstract void setStoredSubjectsLimit(int limit);

    /**
     * Returns the maximum number of cached subjects per connection.
     * This property is used on the client side.
     * <p> The default value of this limit is taken from system property
     * <tt>"{@value RMIClientImpl#DEFAULT_STORED_SUBJECTS_LIMIT_PROPERTY}"</tt>.
     * If the property is not defined then it is equal to
     * <tt>{@value RMIClientImpl#DEFAULT_STORED_SUBJECTS_LIMIT}<tt>.
     *
     * @return current maximum number of cached subjects per connection.
     * @deprecated Use {@link RMIClient#getStoredSubjectsLimit()}
     */
    public abstract int getStoredSubjectsLimit();

    /**
     * Returns the number of requests currently pending for sending in a queue.
     *
     * @return the number of requests currently pending for sending in a queue.
     * @deprecated Use {@link RMIClient#getSendingRequestsQueueLength()}
     */
    public abstract int getSendingRequestsQueueLength();

    // ==================== Builder =======================

    /**
     * Builder that creates instances of {@link RMIEndpoint} objects.
     */
    @Service
    public abstract static class Builder {
        private static final AtomicInteger INSTANCES_NUMERATOR = new AtomicInteger();

        protected DataScheme scheme;
        protected DXEndpoint.Role dxRole;
        protected Side side = Side.CLIENT_SERVER;
        protected final Properties props = new Properties();

        /**
         * Creates new builder. This method is for extension only.
         * Don't use it directly. Use {@link RMIEndpoint#newBuilder()}.
         */
        protected Builder() {}

        /**
         * Changes name that is used to distinguish multiple endpoints
         * in the same JVM in logs and in other diagnostic means.
         * This is a shortcut for
         * {@link #withProperty withProperty}({@link #NAME_PROPERTY NAME_PROPERTY},{@code name})
         */
        public final Builder withName(String name) {
            return withProperty(NAME_PROPERTY, name);
        }

        /**
         * Sets data scheme for the associated {@link DXEndpoint} .
         * By default, the default data scheme is used.
         *
         * @return {@code this} endpoint builder.
         */
        public final Builder withScheme(DataScheme scheme) {
            if (scheme == null)
                throw new NullPointerException();
            this.scheme = scheme;
            return this;
        }

        /**
         * Sets side for the created RMI endpoint.
         * By default, the side is {@link Side#CLIENT_SERVER CLIENT_SERVER}.
         * Depending on this side, methods
         * {@link RMIEndpoint#getClient() RMIEndpoint.getClient} and {@link RMIEndpoint#getServer() RMIEndpoint.getServer}
         * either return an object or throw {@link IllegalStateException}.
         *
         * @return {@code this} endpoint builder.
         */
        public Builder withSide(Side side) {
            this.side = side;
            return this;
        }

        /**
         * Sets role for the associated {@link DXEndpoint} .
         * By default, the dxRole is null and associated {@link DXEndpoint} is not created.
         * Without this role, {@link RMIEndpoint#getDXEndpoint() RMIEndpoint.getDXEndpoint} throws {@link IllegalStateException}.
         *
         * @return {@code this} endpoint builder.
         */
        public Builder withRole(DXEndpoint.Role role) {
            if (role != DXEndpoint.Role.FEED && role != DXEndpoint.Role.PUBLISHER)
                throw new IllegalArgumentException("Unsupported role " + role);
            this.dxRole = role;
            return this;
        }

        /**
         * Sets the specified property. Unsupported properties are ignored.
         *
         * @see #supportsProperty(String)
         */
        public final Builder withProperty(String key, String value) {
            if (key == null || value == null)
                throw new NullPointerException();
            if (supportsProperty(key))
                props.setProperty(key, value);
            return this;
        }

        /**
         * Sets all supported properties from the provided properties object.
         */
        public final Builder withProperties(Properties props) {
            for (Map.Entry<Object, Object> entry : props.entrySet())
                withProperty((String) entry.getKey(), (String) entry.getValue());
            return this;
        }

        /**
         * Returns true if the corresponding property key is supported.
         *
         * @see #withProperty(String, String)
         */
        public boolean supportsProperty(String key) {
            return NAME_PROPERTY.equals(key);
        }

        protected final String getOrCreateName() {
            String name = props.getProperty(NAME_PROPERTY);
            if (name != null)
                return name;
            int number = INSTANCES_NUMERATOR.getAndIncrement();
            return "rmi" + (number == 0 ? "" : "-" + number);
        }

        /**
         * Builds {@link QDEndpoint} instance.
         *
         * @return the created endpoint.
         */
        public abstract RMIEndpoint build();
    }
}
