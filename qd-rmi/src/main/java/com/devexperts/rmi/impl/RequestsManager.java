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
package com.devexperts.rmi.impl;

import com.devexperts.logging.Logging;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.task.RMIServiceDescriptor;

import java.util.List;
import javax.annotation.Nullable;

/**
 * The client side of {@link RMIConnection}.
 */
class RequestsManager {
    private static final Logging log = Logging.getLogging(RequestsManager.class);

    private final OutgoingRequests outgoingRequests;
    private final SentRequests sentRequests = new SentRequests();

    private final RMIConnection connection;
    // "true" when this connection is anonymous / legacy (not service descriptors are sent from the other side)
    // assume anonymous (just in case) until describe protocol receive
    private volatile boolean anonymous = true;

    RequestsManager(RMIConnection connection) {
        this.connection = connection;
        outgoingRequests = new OutgoingRequests(connection.configuredServices);
    }

    void setAnonymousOnDescribeProtocol(boolean anonymous) {
        this.anonymous = anonymous;
        if (anonymous && connection.side.hasClient())
            connection.endpoint.getClient().updateServiceDescriptors(null, connection);
    }

    boolean isAnonymous() {
        return anonymous;
    }

    //------------------------------ SentRequests methods ------------------------------

    void addSentRequest(RMIRequestImpl<?> request) {
        sentRequests.addSentRequest(request);
    }

    //if channelId = 0 => top-level request
    RMIRequestImpl<?> removeSentRequest(long channelId, long curId, RMIMessageKind kind) {
        return sentRequests.removeSentRequest(channelId, curId, kind);
    }

    RMIRequestImpl<?>[] getSentRequests(RMIRequestImpl<?>[] requests) {
        return sentRequests.getSentRequests(requests);
    }

    //------------------------------ OutgoingRequests methods ------------------------------

    // returns false if connection was already closed

    // invoked under either RMIClientImpl.services lock or under RMIChannelImpl lock
    void addOutgoingRequest(RMIRequestImpl<?> request) {
        if (!request.isWaitingToSend()) {
            request.setFailedState(RMIExceptionType.EXECUTION_REJECTION, null);
            return;
        }
        if (anonymous)
            request.setTentativeTarget(null);
        if (RMIEndpointImpl.RMI_TRACE_LOG)
            log.trace("Add outgoing request " + request + " to " + connection);
        request.assignConnection(connection);
        if (!outgoingRequests.add(request)) {
            request.setTentativeTarget(null);
            request.assignConnection(null);
            request.setFailedState(RMIExceptionType.DISCONNECTION, null);
            return;
        }
        connection.messageAdapter.rmiMessageAvailable(RMIQueueType.REQUEST);
    }

    // descriptors are null when need to rebalance anonymous connections only
    @Nullable List<RMIRequestImpl<?>> getByDescriptorsAndRemove(@Nullable List<RMIServiceDescriptor> descriptors) {
        if (descriptors == null && !anonymous)
            return null;
        return outgoingRequests.getByDescriptorsAndRemove(descriptors);
    }

    RMIRequestImpl<?> pollOutgoingRequest() {
        return outgoingRequests.poll();
    }

    int outgoingRequestSize() {
        return outgoingRequests.size();
    }

    boolean hasOutgoingRequest() {
        return !outgoingRequests.isEmpty();
    }

    boolean removeOutgoingRequest(RMIRequestImpl<?> request) {
        return outgoingRequests.remove(request);
    }

    RMIRequestImpl<?>[] getOutgoingRequests(RMIRequestImpl<?>[] requests) {
        return outgoingRequests.getRequests(requests);
    }

    //------------------------------ common methods ------------------------------

    void close() {
        outgoingRequests.close();
        sentRequests.close();
    }
}
