/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.rmi.impl;

import java.util.*;
import java.util.concurrent.Executor;
import javax.annotation.concurrent.GuardedBy;

import com.devexperts.io.Marshalled;
import com.devexperts.logging.Logging;
import com.devexperts.rmi.*;
import com.devexperts.rmi.message.*;
import com.devexperts.rmi.task.*;
import com.devexperts.util.ExecutorProvider;
import com.devexperts.util.SystemProperties;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.impl.ExtensibleDXEndpoint;

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

    @GuardedBy("services")
    private final OutgoingRequests outgoingRequests = new OutgoingRequests();

    private final RMITimeoutRequestMonitoringThread timeoutRequestMonitoringThread;

    private final ClientRequestSender requestSender;

    private final RMIClientPort defaultPort;

    // Note: RMIClient will share executor reference with DXEndpoint if attached to DXEndpoint with role FEED
    // (changing executor here and there is in sync)
    private final ExecutorProvider.Reference defaultExecutorReference;

    // ==================== constructor ====================

    RMIClientImpl(RMIEndpointImpl endpoint) {
        this.endpoint = endpoint;
        this.services = new ClientSideServices(this);
        timeoutRequestMonitoringThread = new RMITimeoutRequestMonitoringThread(endpoint);
        requestSender = new ClientRequestSender();
        defaultPort = new PortImpl(null); // don't move to field, needs endpoint to be set first
        ExecutorProvider.Reference sharedExecutorReference = getSharedExecutorReference();
        defaultExecutorReference = sharedExecutorReference != null ? sharedExecutorReference : endpoint.getDefaultExecutorProvider().newReference();
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
            new RMIRequestMessage<>(RMIRequestType.ONE_WAY, operation, Marshalled.forObject(parameters, operation.getParametersMarshaller()), route, null);
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
        synchronized (services) {
            return outgoingRequests.size();
        }
    }

    // ==================== private implementation ====================

    private ExecutorProvider.Reference getSharedExecutorReference() {
        ExtensibleDXEndpoint dx = endpoint.dxEndpoint;
        // reference the same executor if attached to FEED.
        return dx != null && dx.getRole() == DXEndpoint.Role.FEED ? dx.getExecutorReference() : null;
    }

    void close() {
        timeoutRequestMonitoringThread.wakeUp();
        ExecutorProvider.Reference sharedExecutorReference = getSharedExecutorReference();
        if (defaultExecutorReference != sharedExecutorReference)
            defaultExecutorReference.close();
    }

    ClientSideServices getServices() {
        return services;
    }

    RMIRequestImpl<?> getEarliestRequest() {
        synchronized (services) {
            return outgoingRequests.getEarliestRequest();
        }
    }

    void updateServiceDescriptors(List<RMIServiceDescriptor> descriptors, RMIConnection connection) {
        synchronized (services) {
            if (connection.requestsManager.isAnonymous()) {
                services.updateAnonymousRouter(connection);
                outgoingRequests.rebalanced(null);
            } else {
                connection.clientDescriptorsManager.updateDescriptors(descriptors); // remember descriptors from this connections
                services.updateDescriptorAndUpdateServices(descriptors, connection);
                outgoingRequests.rebalanced(descriptors);
            }
        }
    }

    void removeConnection(RMIConnection connection) {
        synchronized (services) {
            // now clear descriptors of this connection
            List<RMIServiceDescriptor> result = new ArrayList<>();
            for (RMIServiceDescriptor descriptor : connection.clientDescriptorsManager.clearDescriptors())
                result.add(RMIServiceDescriptor.createUnavailableDescriptor(descriptor.getServiceId(), descriptor.getProperties()));
            updateServiceDescriptors(result, connection);
        }
    }

    @GuardedBy("services")
    private RMIConnection assignRequestConnection(RMIRequestImpl<?> request) {
        RMIServiceId target = services.loadBalance(request.getRequestMessage());
        request.setTentativeTarget(target);
        RMIConnection connection = services.getConnection(target);
        if (connection != null)
            connection.requestsManager.addOutgoingRequest(request);
        return connection;
    }

    private void addOutgoingRequestImpl(RMIRequestImpl<?> request) {
        synchronized (services) {
            if (RMIEndpointImpl.RMI_TRACE_LOG)
                log.trace("Add outgoing request " + request + " to " + endpoint);
            if (assignRequestConnection(request) == null)
                outgoingRequests.add(request);
        }
        timeoutRequestMonitoringThread.startIfNotAlive();
    }

    private boolean removeOutgoingRequestImpl(RMIRequestImpl<?> request) {
        synchronized (services) {
            return outgoingRequests.remove(request);
        }
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
        public boolean removeOutgoingRequest(RMIRequestImpl<?> request) {
            return removeOutgoingRequestImpl(request);
        }
    }

    private class OutgoingRequests {
        /**
         * Queue is not generally sorted by time, but the request with least time is
         * maintained to be first for {@link #getEarliestRequest}.
         */
        @GuardedBy("services")
        private final LinkedList<RMIRequestImpl<?>> queue = new LinkedList<>();

        @GuardedBy("services")
        private boolean firstIsEarliest;

        @GuardedBy("services")
        void add(RMIRequestImpl<?> request) {
            if (request.getState() != RMIRequestState.WAITING_TO_SEND)
                return;

            RMIRequestImpl<?> peek = queue.peek();
            if (peek == null || RMIRequestImpl.REQUEST_COMPARATOR_BY_SENDING_TIME.compare(request, peek) < 0) {
                queue.addFirst(request);
                return;
            }
            queue.add(request);
        }

        //if descriptors = null, then to client added a new anonymous connection
        @GuardedBy("services")
        void rebalanced(List<RMIServiceDescriptor> descriptors) {
            for (Iterator<RMIConnection> it = endpoint.concurrentConnectionsIterator(); it.hasNext(); ) {
                RMIConnection connection = it.next();
                List<RMIRequestImpl<?>> requests = connection.requestsManager.getByDescriptorsAndRemove(descriptors);
                if (requests != null && !requests.isEmpty())
                    for (RMIRequestImpl<?> request : requests) {
                        request.setTentativeTarget(null);
                        request.assignConnection(null);
                        add(request);
                    }
            }
            for (Iterator<RMIRequestImpl<?>> it = queue.iterator(); it.hasNext(); ) {
                RMIRequestImpl<?> request = it.next();
                if (assignRequestConnection(request) != null) {
                    it.remove();
                    continue;
                }
                request.setTentativeTarget(null);
            }
        }

        // Note: it is used by TimeoutMonitoring and must return request with minimal time
        @GuardedBy("services")
        RMIRequestImpl<?> getEarliestRequest() {
            if (queue.isEmpty())
                return null;
            if (!firstIsEarliest) {
                RMIRequestImpl<?> earliest = null;
                for (RMIRequestImpl<?> request : queue) {
                    if (earliest == null || RMIRequestImpl.REQUEST_COMPARATOR_BY_SENDING_TIME.compare(request, earliest) < 0)
                        earliest = request;
                }
                queue.remove(earliest);
                queue.addFirst(earliest);
                firstIsEarliest = true;
            }
            return queue.peek();
        }

        @GuardedBy("services")
        boolean remove(RMIRequestImpl<?> request) {
            if (request == queue.peek())
                firstIsEarliest = false;
            return queue.remove(request);
        }

        @GuardedBy("services")
        int size() {
            return queue.size();
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
