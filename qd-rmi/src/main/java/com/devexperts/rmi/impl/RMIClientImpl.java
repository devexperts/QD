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
import com.devexperts.logging.Logging;
import com.devexperts.rmi.RMIClient;
import com.devexperts.rmi.RMIClientPort;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.message.RMIRoute;
import com.devexperts.rmi.task.BalanceResult;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.util.ExecutorProvider;
import com.devexperts.util.SystemProperties;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.impl.ExtensibleDXEndpoint;
import com.dxfeed.promise.Promise;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.concurrent.GuardedBy;

import static com.devexperts.rmi.task.RMIServiceDescriptor.createUnavailableDescriptor;

public class RMIClientImpl extends RMIClient {
    private static final Logging log = Logging.getLogging(RMIClientImpl.class);

    // ==================== private instance fields ====================
    final RMIEndpointImpl endpoint;

    private long requestSendingTimeout =
        SystemProperties.getLongProperty(DEFAULT_REQUEST_SENDING_TIMEOUT_PROPERTY, DEFAULT_REQUEST_SENDING_TIMEOUT);
    private long requestRunningTimeout =
        SystemProperties.getLongProperty(DEFAULT_REQUEST_RUNNING_TIMEOUT_PROPERTY, DEFAULT_REQUEST_RUNNING_TIMEOUT);
    private int storedSubjectsLimit =
        SystemProperties.getIntProperty(DEFAULT_STORED_SUBJECTS_LIMIT_PROPERTY, DEFAULT_STORED_SUBJECTS_LIMIT);

    // this object serves as a lock for a service-descriptor or connection-list modifying operations on client side
    @GuardedBy("services")
    private final ClientSideServices services;

    private final PendingRequests pendingRequests = new PendingRequests();

    private final RMITimeoutRequestMonitoringThread timeoutRequestMonitoringThread;

    private final ClientRequestSender requestSender;

    private final RMIClientPort defaultPort;

    // Note: RMIClient will share executor reference with DXEndpoint if attached to DXEndpoint with role FEED
    // (changing executor here and there is in sync)
    private final ExecutorProvider.Reference defaultExecutorReference;

    // ==================== constructor ====================

    RMIClientImpl(RMIEndpointImpl endpoint) {
        this.endpoint = endpoint;
        this.services = new ClientSideServices(this, endpoint.getRMILoadBalancerFactories());
        timeoutRequestMonitoringThread = new RMITimeoutRequestMonitoringThread(endpoint);
        requestSender = new ClientRequestSender();
        defaultPort = new PortImpl(null); // don't move to field, needs endpoint to be set first
        ExecutorProvider.Reference sharedExecutorReference = getSharedExecutorReference();
        defaultExecutorReference = sharedExecutorReference != null ?
            sharedExecutorReference : endpoint.getDefaultExecutorProvider().newReference();
    }

    // ==================== public methods ====================

    @Override
    public <T> RMIRequest<T> createRequest(Object subject, RMIOperation<T> operation, Object... parameters) {
        return getPort(Marshalled.forObject(subject)).createRequest(operation, parameters);
    }

    @Override
    public <T> RMIRequest<T> createOneWayRequest(Object subject, RMIOperation<T> operation, Object... parameters) {
        RMIRoute route = RMIRoute.createRMIRoute(endpoint.getEndpointId());
        RMIRequestMessage<T> requestMessage =
            new RMIRequestMessage<>(RMIRequestType.ONE_WAY, operation,
                Marshalled.forObject(parameters, operation.getParametersMarshaller()), route, null);
        return getPort(Marshalled.forObject(subject)).createRequest(requestMessage);
    }

    @Override
    public RMIClientPort getPort(Object subject) {
        if (subject == null)
            return defaultPort;
        Marshalled<?> marshalledSubject;
        if (subject instanceof Marshalled)
            marshalledSubject = (Marshalled<?>) subject;
        else
            marshalledSubject = Marshalled.forObject(subject);
        return new PortImpl(marshalledSubject);
    }

    @Override
    public <T> RMIRequest<T> createRequest(RMIRequestMessage<T> message) {
        return defaultPort.createRequest(message);
    }

    @Override
    public <T> T getProxy(Class<T> serviceInterface) {
        return defaultPort.getProxy(serviceInterface, RMIService.getServiceName(serviceInterface));
    }

    @Override
    public <T> T getProxy(Class<T> serviceInterface, String serviceName) {
        return defaultPort.getProxy(serviceInterface, serviceName);
    }

    @Override
    public RMIService<?> getService(String serviceName) {
        return services.getService(serviceName);
    }

    @Override
    public void setDefaultExecutor(Executor executor) {
        defaultExecutorReference.setExecutor(executor);
    }

    @Override
    public Executor getDefaultExecutor() {
        return defaultExecutorReference.getOrCreateExecutor();
    }

    @Override
    public void setRequestSendingTimeout(long timeout) {
        requestSendingTimeout = timeout;
        timeoutRequestMonitoringThread.wakeUp();
    }

    @Override
    public long getRequestSendingTimeout() {
        return requestSendingTimeout;
    }

    @Override
    public void setRequestRunningTimeout(long timeout) {
        requestRunningTimeout = timeout;
        timeoutRequestMonitoringThread.wakeUp();
    }

    @Override
    public long getRequestRunningTimeout() {
        return requestRunningTimeout;
    }

    @Override
    public void setStoredSubjectsLimit(int limit) {
        storedSubjectsLimit = limit;
    }

    @Override
    public int getStoredSubjectsLimit() {
        return storedSubjectsLimit;
    }

    @Override
    public int getSendingRequestsQueueLength() {
        return pendingRequests.size();
    }

    // ==================== private implementation ====================

    private ExecutorProvider.Reference getSharedExecutorReference() {
        ExtensibleDXEndpoint dx = endpoint.dxEndpoint;
        // reference the same executor if attached to FEED.
        return dx != null && dx.getRole() == DXEndpoint.Role.FEED ? dx.getExecutorReference() : null;
    }

    void close() {
        services.close();
        timeoutRequestMonitoringThread.wakeUp();
        ExecutorProvider.Reference sharedExecutorReference = getSharedExecutorReference();
        if (defaultExecutorReference != sharedExecutorReference)
            defaultExecutorReference.close();
    }

    ClientSideServices getServices() {
        return services;
    }

    void forEachPendingRequest(@Nonnull Consumer<RMIRequestImpl<?>> consumer) {
        pendingRequests.forEachRMIRequest(consumer);
    }

    void updateServiceDescriptors(List<RMIServiceDescriptor> descriptors, RMIConnection connection) {
        synchronized (services) {
            if (connection.requestsManager.isAnonymous()) {
                services.updateAnonymousRouter(connection);
                rebalancePendingRequests(null);
            } else {
                connection.clientDescriptorsManager.updateDescriptors(descriptors); // remember descriptors from this connections
                services.updateDescriptorAndUpdateServices(descriptors, connection);
                rebalancePendingRequests(descriptors);
            }
        }
    }

    void removeConnection(RMIConnection connection) {
        synchronized (services) {
            // move outgoing requests of removed connection back to pending requests queue
            RMIRequestImpl<?>[] requests = connection.requestsManager.getOutgoingRequests(new RMIRequestImpl[0]);
            for (RMIRequestImpl<?> request : requests) {
                if (!request.isNestedRequest()) {
                    connection.requestsManager.removeOutgoingRequest(request);
                    pendingRequests.addPendingRequest(request);
                }
            }
            // now clear descriptors of this connection
            List<RMIServiceDescriptor> result = new ArrayList<>();
            for (RMIServiceDescriptor descriptor : connection.clientDescriptorsManager.clearDescriptors())
                result.add(createUnavailableDescriptor(descriptor.getServiceId(), descriptor.getProperties()));
            // will also rebalance all pending requests
            updateServiceDescriptors(result, connection);
        }
    }

    @GuardedBy("services")
    private void balance(RMIRequestImpl<?> request) {
        Promise<BalanceResult> balancePromise = services.balance(request.getRequestMessage());
        if (balancePromise.isDone()) {
            RMILog.logBalancingCompletion(request, balancePromise);
            // Fast path for balancers that return completed promises immediately
            balancingCompleted(request, balancePromise);
            return;
        }

        // Async path, balancing is in progress
        pendingRequests.addBalancePromise(request, balancePromise, this::balancingCompleted);
    }

    @SuppressWarnings("ThrowableNotThrown")
    private void balancingCompleted(RMIRequestImpl<?> request, Promise<? extends BalanceResult> result) {
        // If the set of descriptors changes while balancing is in progress (service implementation appear or
        // disappear in the network) the promise is NOT cancelled. After promise completes the request may get
        // into the pending queue if there is no connection for it.

        if (request.isCompleted())
            return;

        if (result.hasException() || result.getResult().isReject()) {
            Throwable cause = result.hasException() ?
                result.getException() : new RMIFailedException(result.getResult().getRejectReason());
            request.setFailedState(RMIExceptionType.SERVICE_UNAVAILABLE, cause);
            return;
        }
        BalanceResult decision = result.getResult();

        synchronized (services) {
            request.setTentativeTarget(decision.getTarget());
            RMIConnection connection = services.getConnection(request.getTentativeTarget());
            if (connection != null) {
                connection.requestsManager.addOutgoingRequest(request);
            } else {
                pendingRequests.addPendingRequest(request);
            }
        }
    }

    // if descriptors = null, then to client added a new anonymous connection
    @GuardedBy("services")
    private void rebalancePendingRequests(List<RMIServiceDescriptor> descriptors) {
        // 1. Put all requests that are sitting within each connection back to pending queue
        for (Iterator<RMIConnection> it = endpoint.concurrentConnectionsIterator(); it.hasNext(); ) {
            RMIConnection connection = it.next();
            List<RMIRequestImpl<?>> requests = connection.requestsManager.getByDescriptorsAndRemove(descriptors);
            if (requests != null && !requests.isEmpty())
                for (RMIRequestImpl<?> request : requests) {
                    pendingRequests.addPendingRequest(request);
                }
        }

        // 2. Rebalance the pending queue to assign new connections (and return requests with unassigned connections
        // back to the queue)
        List<RMIRequestImpl<?>> toBeRebalanced = pendingRequests.removeAllBalanced();
        toBeRebalanced.forEach(rmiRequest -> {
            rmiRequest.setTentativeTarget(null);
            rmiRequest.assignConnection(null);
            balance(rmiRequest);
        });
    }

    private void addOutgoingRequestImpl(RMIRequestImpl<?> request) {
        synchronized (services) {
            if (RMIEndpointImpl.RMI_TRACE_LOG)
                log.trace("Add outgoing request " + request + " to " + endpoint);
            balance(request);
        }
        timeoutRequestMonitoringThread.startIfNotAlive();
    }

    void stopTimeoutRequestMonitoringThread() {
        timeoutRequestMonitoringThread.stop();
    }

    private class ClientRequestSender extends RequestSender {
        @Override
        void startTimeoutRequestMonitoringThread() {
            timeoutRequestMonitoringThread.startIfNotAlive();
        }

        @Override
        RMIEndpointImpl getEndpoint() {
            return endpoint;
        }

        @Override
        public Executor getExecutor() {
            return getDefaultExecutor();
        }

        @Override
        public void addOutgoingRequest(RMIRequestImpl<?> request) {
            addOutgoingRequestImpl(request);
        }

        @Override
        public boolean dropPendingRequest(RMIRequestImpl<?> request) {
            return pendingRequests.dropPendingRequest(request.getId());
        }
    }

    private class PortImpl extends RMIClientPortImpl {
        PortImpl(Marshalled<?> marshalledSubject) {
            super(RMIClientImpl.this.endpoint, marshalledSubject);
        }

        @Override
        protected RequestSender getRequestSender() {
            return requestSender;
        }
    }
}
