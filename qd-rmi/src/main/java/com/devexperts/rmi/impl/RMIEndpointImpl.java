/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.impl;

import com.devexperts.connector.proto.EndpointId;
import com.devexperts.io.SerialClassContext;
import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIEndpointListener;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.security.SecurityContext;
import com.devexperts.rmi.security.SecurityController;
import com.devexperts.rmi.task.RMILoadBalancerFactory;
import com.devexperts.rmi.task.RMIServiceImplementation;
import com.devexperts.services.Services;
import com.devexperts.transport.stats.EndpointStats;
import com.devexperts.util.ExecutorProvider;
import com.devexperts.util.SynchronizedIndexedSet;
import com.devexperts.util.SystemProperties;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.impl.ExtensibleDXEndpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import javax.annotation.concurrent.GuardedBy;
import javax.net.ssl.TrustManager;

public final class RMIEndpointImpl extends RMIEndpoint {
    static final boolean RMI_TRACE_LOG = RMIEndpointImpl.class.desiredAssertionStatus();

    // ==================== private static fields ====================

    private static final Logging log = Logging.getLogging(RMIEndpointImpl.class);
    private static final String THREAD_COUNT_SYSTEM_PROPERTY = "com.devexperts.rmi.ThreadCount";

    // ==================== private instance fields ====================

    private final String name;
    private final Object lock; // is taken from qdEndpoint ans protects state
    private final QDEndpoint qdEndpoint;

    private SerialClassContext serialContext;

    // Due to deadlock possibility both connections and endpointListeners cannot be guarded by lock
    private final SynchronizedIndexedSet<RMIConnection, RMIConnection> connections = new SynchronizedIndexedSet<>();
    private final List<RMIEndpointListener> endpointListeners = new CopyOnWriteArrayList<>();

    private final RMIClientImpl client;
    private final RMIServerImpl server;
    private String address;

    ExtensibleDXEndpoint dxEndpoint;
    final RMIEndpoint.Side side;

    // Default provider for client and server executors (when they are not being set explicitly)
    private final ExecutorProvider defaultExecutorProvider;

    private SecurityController securityController;
    private MessageAdapter.ConfigurableFactory attachedMessageAdapterFactory;
    TrustManager trustManager;

    private volatile List<RMILoadBalancerFactory> loadBalancerFactories;

    @GuardedBy("this")
    private boolean closed;

    /**
     * Creates RMIEndpointImpl with a specified side and {@link QDEndpoint}.
     * @param side the side (non-null).
     * @param qdEndpoint QD endpoint (non null).
     * @param attachedMessageAdapterFactory attached factory (may be null)
     * @param dxEndpoint attached DX endpoint (may be null)
     */
    public RMIEndpointImpl(Side side, QDEndpoint qdEndpoint, MessageAdapter.Factory attachedMessageAdapterFactory, DXEndpoint dxEndpoint) {
        if (side == null)
            throw new NullPointerException();
        if (qdEndpoint == null)
            throw new NullPointerException();
        if (dxEndpoint != null) {
            if (!(dxEndpoint instanceof RMISupportingDXEndpoint))
                throw new IllegalArgumentException("Unsupported instance of DXEndpoint");
            if (((RMISupportingDXEndpoint) dxEndpoint).getQDEndpoint() != qdEndpoint)
                throw new IllegalArgumentException("DXEndpoint for a different QDEndpoint");
        }

        this.name = qdEndpoint.getName();
        this.lock = qdEndpoint.getLock();
        this.side = side;
        this.qdEndpoint = qdEndpoint;

        serialContext = SerialClassContext.getDefaultSerialContext(null);
        defaultExecutorProvider = new ExecutorProvider(
            SystemProperties.getIntProperty(THREAD_COUNT_SYSTEM_PROPERTY, Runtime.getRuntime().availableProcessors()),
            name + "-RMIExecutorThread", log);

        if (!qdEndpoint.hasConnectorInitializer())
            qdEndpoint.setConnectorInitializer(new RMIConnectorInitializer(this));

        SecurityController securityController = Services.createService(SecurityController.class, null, null);
        if (securityController == null)
            securityController = SecurityContext.getInstance();
        this.securityController = securityController;

        this.dxEndpoint = (ExtensibleDXEndpoint) dxEndpoint; // must be ExtensibleDXEndpoint or ClassCastException
        if (attachedMessageAdapterFactory != null)
            setAttachedMessageAdapterFactoryImpl(attachedMessageAdapterFactory);

        client = this.side.hasClient() ? new RMIClientImpl(this) : null;
        server = this.side.hasServer() ? new RMIServerImpl(this) : null;
    }

    // ==================== instance Common API ====================

    @Override
    public void connect(String address) {
        if (address == null)
            throw new NullPointerException();
        synchronized (lock) {
            if (qdEndpoint.isClosed() || address.equals(this.address))
                return;
            disconnect();
            qdEndpoint.connect(address);
            setConnectedAddressSync(address);
            if (dxEndpoint != null)
                dxEndpoint.setConnectedAddressSync(address);
        }
    }

    // GuardedBy(lock)
    public void setConnectedAddressSync(String address) {
        this.address = address;
    }

    @Override
    public void reconnect() {
        synchronized (lock) {
            if (qdEndpoint.isClosed())
                return;
            qdEndpoint.reconnectActiveConnectors();
        }
    }

    @Override
    public void disconnect() {
        synchronized (lock) {
            if (address == null)
                return;
            address = null;
            qdEndpoint.cleanupConnectors();
            connections.clear();
            if (side.hasClient())
                getClient().stopTimeoutRequestMonitoringThread();
            if (dxEndpoint != null)
                dxEndpoint.disconnect();
        }
    }

    boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (closed)
                return;
            closed = true;
            disconnect();
            qdEndpoint.close();
        }
        if (client != null)
            client.close();
        if (server != null)
            server.close();
    }

    @Override
    public boolean isConnected() {
        return !connections.isEmpty();
    }

    @Override
    public EndpointStats getEndpointStats() {
        synchronized (lock) {
            return MessageConnectors.getEndpointStats(qdEndpoint.getConnectors());
        }
    }

    @Override
    public void addEndpointListener(RMIEndpointListener listener) {
        endpointListeners.add(listener);
    }

    @Override
    public  void removeEndpointListener(RMIEndpointListener listener) {
        endpointListeners.remove(listener);
    }

    @Override
    public void setSerialClassContext(SerialClassContext serialClassContexts) {
        this.serialContext = serialClassContexts;
    }

    @Override
    public SerialClassContext getSerialClassContext() {
        return serialContext;
    }

    @Override
    public SecurityController getSecurityController() {
        return securityController;
    }

    @Override
    public void setSecurityController(SecurityController securityController) {
        this.securityController = securityController;
    }

    @Override
    public DXEndpoint getDXEndpoint() {
        if (dxEndpoint == null)
            throw new IllegalStateException("There is no DXEndpoint associated with this RMIEndpoint.");
        return dxEndpoint;
    }

    @Override
    @Deprecated
    public MessageAdapter.ConfigurableFactory getAttachedMessageAdapterFactory() {
        return attachedMessageAdapterFactory;
    }

    @Override
    @Deprecated
    public void setAttachedMessageAdapterFactory(MessageAdapter.Factory attachedMessageAdapterFactory) {
        // NOTE: This method is deprecated and we support only abstract factories anyway
        if (attachedMessageAdapterFactory == null)
            return;
        if (dxEndpoint != null)
            throw new IllegalStateException("Cannot set attached message factory for dxFeed-enabled endpoint.");
        setAttachedMessageAdapterFactoryImpl(attachedMessageAdapterFactory);
    }

    private void setAttachedMessageAdapterFactoryImpl(MessageAdapter.Factory attachedMessageAdapterFactory) {
        if (this.attachedMessageAdapterFactory != null)
            throw new IllegalStateException("Cannot change attached message factory");
        this.attachedMessageAdapterFactory = MessageConnectors.configurableFactory(attachedMessageAdapterFactory);
        this.attachedMessageAdapterFactory.setEndpoint(RMIEndpoint.class, this);
    }

    @Override
    public void setTrustManager(TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Side getSide() {
        return side;
    }

    @Override
    public RMIServerImpl getServer() {
        if (server == null)
            throw new IllegalStateException("This RMIEndpoint has RMIEndpoint.Side.CLIENT");
        return server;
    }

    @Override
    public RMIClientImpl getClient() {
        if (client == null)
            throw new IllegalStateException("This RMIEndpoint has RMIEndpoint.Side.SERVER");
        return client;
    }

    // ==================== Server API ====================

    @Override
    @Deprecated
    public <T> void export(T implementation, Class<T> serviceInterface) {
        getServer().export(implementation, serviceInterface);
    }

    @Override
    @Deprecated
    public <T> void export(T implementation, Class<T> serviceInterface, ExecutorService executor) {
        RMIServiceImplementation<T> service = new RMIServiceImplementation<>(implementation, serviceInterface);
        service.setExecutor(executor);
        getServer().export(service);
    }

    @Override
    @Deprecated
    public <T> void export(T implementation, Class<T> serviceInterface, String serviceName) {
        getServer().export(new RMIServiceImplementation<>(implementation, serviceInterface, serviceName));
    }

    @Override
    @Deprecated
    public <T> void export(T implementation, Class<T> serviceInterface, String serviceName, ExecutorService executor) {
        RMIServiceImplementation<T> service = new RMIServiceImplementation<>(implementation, serviceInterface, serviceName);
        service.setExecutor(executor);
        getServer().export(service);
    }

    @Override
    @Deprecated
    public ExecutorService getDefaultExecutor() {
        // Kludge: For backwards compatibility with legacy API just cast here
        return (ExecutorService) getServer().getDefaultExecutor();
    }

    @Override
    @Deprecated
    public void setDefaultExecutor(ExecutorService executor) {
        getServer().setDefaultExecutor(executor);
    }

    // ==================== Client API ====================

    @Override
    @Deprecated
    public <T> T getProxy(Class<T> serviceInterface) {
        return getProxy(serviceInterface, serviceInterface.getName());
    }

    @Override
    @Deprecated
    @SuppressWarnings({"unchecked"})
    public <T> T getProxy(Class<T> serviceInterface, String serviceName) {
        return getClient().getProxy(serviceInterface, serviceName);
    }

    @Override
    @Deprecated
    public <T> RMIRequest<T> createRequest(Object subject, RMIOperation<T> operation, Object... parameters) {
        return getClient().createRequest(subject, operation, parameters);
    }

    @Override
    @Deprecated
    public <T> RMIRequest<T> createOneWayRequest(Object subject, RMIOperation<T> operation, Object... parameters) {
        return getClient().createOneWayRequest(subject, operation, parameters);
    }

    @Override
    @Deprecated
    public void setRequestSendingTimeout(long timeout) {
        getClient().setRequestSendingTimeout(timeout);
    }

    @Override
    @Deprecated
    public long getRequestSendingTimeout() {
        return getClient().getRequestSendingTimeout();
    }

    @Override
    @Deprecated
    public void setRequestRunningTimeout(long timeout) {
        getClient().setRequestRunningTimeout(timeout);
    }

    @Override
    @Deprecated
    public long getRequestRunningTimeout() {
        return getClient().getRequestRunningTimeout();
    }

    @Override
    @Deprecated
    public void setStoredSubjectsLimit(int limit) {
        getClient().setStoredSubjectsLimit(limit);
    }

    @Override
    @Deprecated
    public int getStoredSubjectsLimit() {
        return getClient().getStoredSubjectsLimit();
    }

    @Override
    @Deprecated
    public int getSendingRequestsQueueLength() {
        return getClient().getSendingRequestsQueueLength();
    }

    @Override
    public String toString() {
        return "RMIEndpoint{side=" + side + ","
            + "id=" + getEndpointId() +
            "}";
    }

    // ==================== private implementation ====================

    List<RMILoadBalancerFactory> getRMILoadBalancerFactories() {
        List<RMILoadBalancerFactory> factories = loadBalancerFactories;
        if (factories != null)
            return factories;
        synchronized (lock) {
            factories = loadBalancerFactories;
            if (factories != null)
                return factories;

            Iterable<RMILoadBalancerFactory> factoryServices = Services.createServices(RMILoadBalancerFactory.class, null);
            List<RMILoadBalancerFactory> factoryList = new ArrayList<>();
            factoryServices.forEach(factoryList::add);
            loadBalancerFactories = Collections.unmodifiableList(factoryList);
            return loadBalancerFactories;
        }
    }

    public QDEndpoint getQdEndpoint() {
        return qdEndpoint;
    }

    public EndpointId getEndpointId() {
        return qdEndpoint.getEndpointId();
    }

    Object getLock() {
        return lock;
    }

    ExecutorProvider getDefaultExecutorProvider() {
        return defaultExecutorProvider;
    }

    void registerConnection(RMIConnection connection) {
        connections.add(connection);
        notifyListeners();
        if (side.hasClient()) {
            if (getClient().getSendingRequestsQueueLength() > 0)
                connection.messageAdapter.rmiMessageAvailable(RMIQueueType.REQUEST);
        }
    }

    void unregisterConnection(RMIConnection connection) {
        // First remove from a list of connection to make sure no further request rebalancing will use it
        connections.remove(connection);
        if (side.hasClient())
            getClient().removeConnection(connection);
        notifyListeners();
    }

    Iterator<RMIConnection> concurrentConnectionsIterator() {
        return connections.concurrentIterator();
    }

    private void notifyListeners() {
        for (RMIEndpointListener listener : endpointListeners) {
            try {
                listener.stateChanged(this);
            } catch (Throwable t) {
                log.error("Error in RMIEndpointListener", t);
            }
        }
    }
}
