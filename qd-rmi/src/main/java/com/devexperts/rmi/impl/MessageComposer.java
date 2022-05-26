/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.impl;

import com.devexperts.connector.proto.EndpointId;
import com.devexperts.connector.proto.JVMId;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.Chunk;
import com.devexperts.io.Marshalled;
import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.MessageVisitor;
import com.devexperts.rmi.RMIClient;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequestState;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.message.RMIResponseMessage;
import com.devexperts.rmi.message.RMIResponseType;
import com.devexperts.rmi.message.RMIRoute;
import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.rmi.task.RMIServiceId;
import com.devexperts.rmi.task.RMITaskState;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.SystemProperties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;

/**
 * Auxiliary class that communicates that composes outgoing RMI messages and keeps composed stream state.
 */
class MessageComposer {
    private static final Logging log = Logging.getLogging(MessageComposer.class);

    private static final int MAX_CONCURRENT_RMI_MESSAGES =
        SystemProperties.getIntProperty("com.devexperts.rmi.maxConcurrentRMIMessages", 6);
    private static final int DESCRIBE_AHEAD_LIMIT =
        SystemProperties.getIntProperty("com.devexperts.rmi.describeAheadLimit", 2);

    // ==================== fields ====================

    private final RMIConnection connection;

    @GuardedBy("this")
    private final ByteArrayOutput aux = new ByteArrayOutput(ComposedMessage.RESERVE);

    @GuardedBy("this")
    private final Subjects subjects = new Subjects();

    @GuardedBy("this")
    private final Operations operations = new Operations();

    @GuardedBy("this")
    private final Queues queues;

    @GuardedBy("this")
    private int sequence = 1; // used to number message parts

    /*
     * These variables are set from reader thread in {@link #setRemoteReceiveSet(EnumSet)}
     * and are checked by message senders in {@link #canEnqueueRequest()} and {@link #completeMessage(ComposedMessage)}.
     */
    private volatile boolean canEnqueueRequest;

    private volatile boolean supportsComboResponse;
    private volatile boolean supportsMessagePart;
    private volatile boolean supportTargetRouteProtocol;

    // ==================== package-private API ====================

    MessageComposer(RMIConnection connection) {
        this.connection = connection;
        queues = new Queues(connection.side);
    }

    void setRemoteReceiveSet(EnumSet<MessageType> remoteReceiveSet) {
        canEnqueueRequest = remoteReceiveSet.contains(MessageType.RMI_DESCRIBE_OPERATION) &&
            remoteReceiveSet.contains(MessageType.RMI_DESCRIBE_SUBJECT) &&
            remoteReceiveSet.contains(MessageType.RMI_REQUEST);
        supportsMessagePart = remoteReceiveSet.contains(MessageType.PART);
        supportsComboResponse = remoteReceiveSet.contains(MessageType.RMI_RESPONSE);
    }

    void setSupportTargetRouteProtocol(boolean supportTargetRouteProtocol) {
        this.supportTargetRouteProtocol = supportTargetRouteProtocol;
    }

    synchronized void close() {
        while (true) {
            ComposedMessage message = queues.get(RMIQueueType.REQUEST).remove();
            if (message == null)
                break;
            if (message.kind().isRequest())
                ((RMIRequestImpl<?>) message.getObject()).setFailedState(RMIExceptionType.DISCONNECTION, null);
            ComposedMessage.releaseComposedMessage(message);
        }
    }

    /**
     * Process messages of specified type
     *
     * @return - {@code true} if not all messages was completely processed
     * <br/> - {@code false} if all messages in the queue was successfully processed.
     */
    synchronized boolean retrieveRMIMessages(MessageVisitor visitor, RMIQueueType type) {
        if (type == RMIQueueType.ADVERTISE && !connection.side.hasServer())
            throw new AssertionError();
        ComposedMessageQueue queue = queues.get(type);
        boolean hasCapacity = queue.size() == 0 || (supportsMessagePart && queue.size() < MAX_CONCURRENT_RMI_MESSAGES);
        if (hasCapacity && hasMoreMessages(type))
            enqueueMessage(type);
        MessageQueueState queueState = retrieveMessageImpl(visitor, queue);
        return queueState.hasMoreWork() || hasMoreMessages(type);
    }

    // ==================== private implementation ====================

    // @return <tt>VISITOR_FULL</tt> if the whole message was not processed because the visitor is full
    //         and <tt>NO_MORE_MESSAGES</tt> if the message was successfully processed.
    private MessageQueueState retrieveMessageImpl(MessageVisitor visitor, ComposedMessageQueue queue) {
        ComposedMessage message;
        do {
            message = queue.remove();
            if (message == null)
                return MessageQueueState.NO_MORE_MESSAGES;  // no more messages
            //RMI_DESCRIBE_OPERATION and RMI_DESCRIBE_SUBJECT are sent before RMI_REQUEST,
            // but avoid starvation -- send just a few, "canSendMessage" will check if relevant ones were sent
            if (message.kind().isRequest()) {
                for (int i = 0; i < DESCRIBE_AHEAD_LIMIT; i++) {
                    MessageQueueState state = retrieveMessageImpl(visitor, queues.get(RMIQueueType.DESCRIBE));
                    if (state == MessageQueueState.NO_MORE_MESSAGES)
                        break;
                    if (state == MessageQueueState.VISITOR_FULL)
                        return state;
                }
            }
        } while (!canSendMessage(message, queue));

        if (sendRetrievedMessage(visitor, message, queue))
            return MessageQueueState.VISITOR_FULL;

        if (message.isEmpty())
            messageSentCompletely(message);
        else
            queue.addLast(message);
        return MessageQueueState.NOT_ALL_SENT;
    }

    private void completeMessageImpl(ComposedMessageQueue queue, ComposedMessage message) {
        message.flushOutputChunks();
        if (message.chunksCount() > 1) { // the message is going to be partitioned
            if (supportsMessagePart) {
                message.completeMessageParts(sequence++, aux);
            } else {
                message.completeMonolithicMessage();
            }
        }
        queue.addLast(message);
    }

    // @return <tt>true</tt> if the whole message was not processed because the visitor is full
    //         and <tt>false</tt> if the message was successfully processed.
    private boolean sendRetrievedMessage(MessageVisitor visitor, ComposedMessage message, ComposedMessageQueue queue) {
        Chunk chunk = message.firstChunk();
        if (visitor.visitOtherMessage(message.type(), chunk.getBytes(), chunk.getOffset(), chunk.getLength())) {
            queue.addFirst(message);
            return true; // visitor was full... retry it next time
        }
        message.chunkTransmitted();
        return false;
    }

    private boolean canSendMessage(ComposedMessage message, ComposedMessageQueue queue) {
        RMIMessageKind type = message.kind();
        if (!type.isRequest())
            return true; // only for requests can abort or wait to be sent
        RMIRequestImpl<?> request = (RMIRequestImpl<?>) message.getObject();
        // check request timeout
        if (!message.startedTransmission() && request.getRequestMessage().getRequestType() == RMIRequestType.DEFAULT) {
            if (!request.isNestedRequest() && System.currentTimeMillis() - request.getSendTime() >
                connection.endpoint.getClient().getRequestSendingTimeout())
            {
                request.setFailedState(RMIExceptionType.REQUEST_SENDING_TIMEOUT, null);
            }
            if (request.getState() != RMIRequestState.SENDING) {
                if (abortRequest(message))
                    return false;
            }
        }
        // handle cancelled requests
        if (request.getState() == RMIRequestState.CANCELLING || request.getState() == RMIRequestState.FAILED) {
            if (abortRequest(message))
                return false;
        }
        if (message.startedTransmission())
            return true; // always continue if started
        // check that subject and operation were described
        if (subjects.hasOutgoingSubject(request.getMarshalledSubject()) ||
            operations.hasOutgoingOperation(request.getOperation()))
        {
            // do not send the request if its subject and/or operation is not described yet.
            queue.addLast(message);
            return false;
        }
        return true;
    }

    private void messageSentCompletely(ComposedMessage message) {
        if (message.kind() != null) {
            switch (message.kind()) {
                case DESCRIBE_SUBJECT:
                    subjects.removeOutgoingSubject((Marshalled<?>) message.getObject());
                    break;
                case DESCRIBE_OPERATION:
                    operations.removeOutgoingOperation((RMIOperation<?>) message.getObject());
                    break;
            }
        }
        if (message.kind().isRequest())
            ((RMIRequestImpl<?>) message.getObject()).setSentState(connection);
        ComposedMessage.releaseComposedMessage(message);
    }

    // returns true if request message transmission has not been started yet
    private boolean abortRequest(ComposedMessage message) {
        ((RMIRequestImpl<?>) message.getObject()).setFailedState(RMIExceptionType.CANCELLED_BEFORE_EXECUTION, null);
        if (!message.startedTransmission()) {
            ComposedMessage.releaseComposedMessage(message);
            return true;
        }
        message.abortRemainingMessageParts();
        return false;
    }

    private void completeMessage(ComposedMessage message) {
        completeMessageImpl(queues.get(RMIQueueType.DESCRIBE), message);
    }

    // ==================== compose messages ====================

    private void composeComboRequest(RMIRequestImpl<?> request, int subjectId, int operationId) {
        try {
            RMIRequestMessage<?> requestMessage = request.getRequestMessage();
            JVMId.WriteContext ctx = new JVMId.WriteContext();
            ComposedMessage message =
                ComposedMessage.allocateComposedMessage(MessageType.RMI_REQUEST, request.getKind(), request);
            message.output().writeCompactLong(request.getId());
            message.output().writeCompactInt(message.kind().getId());
            if (message.kind().hasChannel())
                message.output().writeCompactLong(request.getChannelId());
            message.output().writeCompactInt(requestMessage.getRequestType().getId());
            composeRoute(message, requestMessage.getRoute(), ctx);
            RMIServiceId.writeRMIServiceId(message.output(), request.getTentativeTarget(), ctx);
            message.output().writeCompactInt(subjectId);
            message.output().writeCompactInt(operationId);
            message.output().writeMarshalled(requestMessage.getParameters());
            completeMessageImpl(queues.get(RMIQueueType.REQUEST), message);
        } catch (IOException e) {
            throw new AssertionError("Unexpected IOException"); // should never happen
        }
    }

    private void composeComboResponse(RMIResponseMessage responseMessage, long channelId, long requestId,
        RMIMessageKind kind)
    {
        try {
            ComposedMessage message =
                ComposedMessage.allocateComposedMessage(MessageType.RMI_RESPONSE, kind, responseMessage);
            message.output().writeCompactLong(requestId);
            message.output().writeCompactInt(kind.getId());
            if (kind.hasChannel())
                message.output().writeCompactLong(channelId);
            composeRoute(message, responseMessage.getRoute(), new JVMId.WriteContext());
            message.output().writeMarshalled(responseMessage.getMarshalledResult());
            completeMessageImpl(queues.get(RMIQueueType.RESPONSE), message);
        } catch (IOException e) {
            throw new AssertionError("Unexpected IOException"); // should never happen
        }
    }

    private void composeAdvertiseServicesMessage(List<RMIServiceDescriptor> descriptors) {
        try {
            if (descriptors.isEmpty())
                return;
            if (RMIEndpointImpl.RMI_TRACE_LOG)
                log.trace("Compose advertise services " + descriptors + " at " + connection);
            ComposedMessage message = ComposedMessage.allocateComposedMessage(
                MessageType.RMI_ADVERTISE_SERVICES, RMIMessageKind.ADVERTISE, descriptors);
            JVMId.WriteContext ctx = new JVMId.WriteContext();
            for (RMIServiceDescriptor serviceDescriptor : descriptors) {
                RMIServiceId.writeRMIServiceId(message.output(), serviceDescriptor.getServiceId(), ctx);
                message.output().writeCompactInt(serviceDescriptor.getDistance());
                message.output().writeCompactInt(serviceDescriptor.getIntermediateNodes().size());
                for (EndpointId endpointId : serviceDescriptor.getIntermediateNodes()) {
                    EndpointId.writeEndpointId(message.output(), endpointId, ctx);
                }
                message.output().writeCompactInt(serviceDescriptor.getProperties().size());
                for (Map.Entry<String, String> propEntry : serviceDescriptor.getProperties().entrySet()) {
                    message.output().writeUTFString(propEntry.getKey());
                    message.output().writeUTFString(propEntry.getValue());
                }
            }
            completeMessageImpl(queues.get(RMIQueueType.ADVERTISE), message);
        } catch (IOException e) {
            throw new AssertionError("Unexpected IOException"); // should never happen
        }
    }

    private static void composeRoute(ComposedMessage message, RMIRoute route, JVMId.WriteContext ctx)
        throws IOException
    {
        int size = message.kind().isRequest() ? route.size() - 1 : route.size();
        message.output().writeCompactInt(size);
        for (int i = 0; i < size; i++) {
            EndpointId.writeEndpointId(message.output(), route.get(i), ctx);
        }
    }

    // ==================== compose legacy (backwards-compatible) messages ====================

    private void composeOldRequest(RMIRequestImpl<?> request, int subjectId, int operationId) {
        try {
            ComposedMessage message = ComposedMessage.allocateComposedMessage(
                MessageType.RMI_REQUEST, RMIMessageKind.REQUEST, request);
            message.output().writeCompactLong(request.getId());
            int typeId = request.getRequestMessage().getRequestType().getId();
            RMIRoute route = request.getRequestMessage().getRoute();
            JVMId.WriteContext ctx = new JVMId.WriteContext();
            if (supportTargetRouteProtocol) {
                if (route.isNotEmptyWithLast(connection.endpoint.getEndpointId()))
                    typeId |= RMIMessageConstants.REQUEST_WITH_ROUTE;
                if (request.getTentativeTarget() != null)
                    typeId |= RMIMessageConstants.REQUEST_WITH_TARGET;
            }
            message.output().writeCompactInt(typeId);
            if ((typeId & RMIMessageConstants.REQUEST_WITH_ROUTE) != 0)
                composeRoute(message, request.getRequestMessage().getRoute(), ctx);
            if ((typeId & RMIMessageConstants.REQUEST_WITH_TARGET) != 0)
                RMIServiceId.writeRMIServiceId(message.output(), request.getTentativeTarget(), ctx);
            message.output().writeCompactInt(subjectId);
            message.output().writeCompactInt(operationId);
            message.output().writeMarshalled(request.getRequestMessage().getParameters());
            completeMessageImpl(queues.get(RMIQueueType.REQUEST), message);
        } catch (IOException e) {
            throw new AssertionError("Unexpected IOException"); // should never happen
        }
    }

    private void composeOldCancel(RMIRequestImpl<?> cancel) {
        try {
            ComposedMessage message =
                ComposedMessage.allocateComposedMessage(MessageType.RMI_CANCEL, null, null);
            message.output().writeCompactLong((Long) cancel.getParameters()[0]);
            message.output().writeCompactInt((Integer) cancel.getParameters()[1]);
            completeMessageImpl(queues.get(RMIQueueType.REQUEST), message);
        } catch (IOException e) {
            throw new AssertionError("Unexpected IOException"); // should never happen
        }
    }

    private void composeOldResponse(RMITaskResponse taskResponse, MessageType messageType) {
        try {
            RMIMessageKind kind = taskResponse.state == RMITaskState.SUCCEEDED ? RMIMessageKind.SUCCESS_RESPONSE :
                RMIMessageKind.ERROR_RESPONSE;
            ComposedMessage message = ComposedMessage.allocateComposedMessage(messageType, kind, taskResponse);
            message.output().writeCompactLong(taskResponse.requestId);
            message.output().writeMarshalled(taskResponse.responseMessage.getMarshalledResult());
            if (!taskResponse.responseMessage.getRoute().isEmpty())
                composeRoute(message, taskResponse.responseMessage.getRoute(), new JVMId.WriteContext());
            completeMessageImpl(queues.get(RMIQueueType.RESPONSE), message);
        } catch (IOException e) {
            throw new AssertionError("Unexpected IOException"); // should never happen
        }
    }

    // ==================== enqueue messages ====================

    private void enqueueMessage(RMIQueueType type) {
        switch (type) {
            case REQUEST:
                enqueueRequest();
                break;
            case RESPONSE:
                enqueueResponse();
                break;
            case ADVERTISE:
                enqueueAdvertise();
                break;
        }
    }

    private boolean hasMoreMessages(RMIQueueType type) {
        switch (type) {
            case REQUEST:
                return canEnqueueRequest && connection.requestsManager.hasOutgoingRequest();
            case RESPONSE:
                return connection.tasksManager.hasCompletedTask();
            case ADVERTISE:
                return connection.side.hasServer() && connection.serverDescriptorsManager.hasDescriptor();
            default:
                return false;
        }
    }

    private void enqueueRequest() {
        RMIRequestImpl<?> request = connection.requestsManager.pollOutgoingRequest();
        if (request == null)
            return;
        if (RMIEndpointImpl.RMI_TRACE_LOG)
            log.trace("Compose request " + request + " at " + connection);
        if (request.isCancelRequest() && !supportsComboResponse)
            composeOldCancel(request);
        if (!request.setSendingState(connection)) {
            request.setFailedState(RMIExceptionType.CANCELLED_BEFORE_EXECUTION, null);
            return;
        }
        Integer subjectId = subjects.getOrComposeSubject(request);
        if (subjectId == null)
            return;
        Integer operationId = operations.getOrComposeOperation(request.getOperation());
        if (supportsComboResponse)
            composeComboRequest(request, subjectId, operationId);
        else
            composeOldRequest(request, subjectId, operationId);
    }

    private void enqueueResponse() {
        RMITaskResponse taskResponse = connection.tasksManager.pollCompletedTask();
        if (taskResponse == null)
            return;
        if (RMIEndpointImpl.RMI_TRACE_LOG)
            log.trace("Compose response " + taskResponse + " at " + connection);
        RMIResponseMessage message = taskResponse.responseMessage;
        RMIMessageKind kind = taskResponse.kind;
        if (supportsComboResponse) {
            composeComboResponse(taskResponse.responseMessage, taskResponse.channelId, taskResponse.requestId, kind);
        } else {
            composeOldResponse(taskResponse,
                message.getType() == RMIResponseType.SUCCESS ? MessageType.RMI_RESULT : MessageType.RMI_ERROR);
        }
    }

    private void enqueueAdvertise() {
        List<RMIServiceDescriptor> descriptors = connection.serverDescriptorsManager.pollServiceDescriptors();
        if (descriptors == null)
            return;
        composeAdvertiseServicesMessage(descriptors);
    }

    // ==================== private inner/nested classes ====================

    private enum MessageQueueState {
        VISITOR_FULL(true),
        NO_MORE_MESSAGES(false),
        NOT_ALL_SENT(true);

        public boolean hasMoreWork() {
            return hasMoreWork;
        }

        private final boolean hasMoreWork;

        MessageQueueState(boolean hasMoreWork) {
            this.hasMoreWork = hasMoreWork;
        }
    }

    private class Subjects {
        private final List<Integer> freeIds = new ArrayList<>();
        private final LinkedHashMap<Marshalled<?>, Integer> ids;
        private final Set<Marshalled<?>> outgoing = new IndexedSet<>(); // in the process set
        private int counter;

        Subjects() {
            this.ids = new LinkedHashMap<Marshalled<?>, Integer>(16, 0.5f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Marshalled<?>, Integer> eldest) {
                    int subjectLimit = connection.endpoint.side.hasClient() ?
                        connection.endpoint.getClient().getStoredSubjectsLimit() :
                        RMIClient.DEFAULT_STORED_SUBJECTS_LIMIT;
                    boolean remove = size() > subjectLimit;
                    if (remove)
                        freeIds.add(eldest.getValue());
                    return remove;
                }
            };
        }

        Integer getOrComposeSubject(RMIRequestImpl<?> request) {
            Marshalled<?> marshalledSubject = request.getMarshalledSubject();
            if (marshalledSubject == Marshalled.NULL)
                return 0;
            Integer id = ids.get(marshalledSubject);
            if (id != null)
                return id;
            ComposedMessage message = ComposedMessage.allocateComposedMessage(
                MessageType.RMI_DESCRIBE_SUBJECT, RMIMessageKind.DESCRIBE_SUBJECT, marshalledSubject);
            int n = freeIds.size();
            if (n > 0) {
                id = freeIds.remove(n - 1);
            } else {
                id = ++counter;
            }
            try {
                message.output().writeCompactInt(id);
                message.output().writeMarshalled(marshalledSubject);
            } catch (Throwable t) {
                request.setFailedState(RMIExceptionType.SUBJECT_MARSHALLING_ERROR, t);
                ComposedMessage.releaseComposedMessage(message);
                return null;
            }
            ids.put(marshalledSubject, id);
            outgoing.add(marshalledSubject);
            completeMessage(message);
            return id;
        }

        boolean hasOutgoingSubject(Marshalled<?> marshalledSubject) {
            return outgoing.contains(marshalledSubject);
        }

        void removeOutgoingSubject(Marshalled<?> marshalledSubject) {
            outgoing.remove(marshalledSubject);
        }
    }

    private class Operations {
        private final Map<RMIOperation<?>, Integer> ids = new HashMap<>();
        private final Set<RMIOperation<?>> outgoing = new IndexedSet<>();  // in the process set
        private int counter;

        Integer getOrComposeOperation(RMIOperation<?> operation) {
            Integer id = ids.get(operation);
            if (id != null)
                return id;
            ComposedMessage message = ComposedMessage.allocateComposedMessage(
                MessageType.RMI_DESCRIBE_OPERATION, RMIMessageKind.DESCRIBE_OPERATION, operation);
            id = ++counter;
            try {
                message.output().writeCompactInt(id);
                message.output().writeUTFString(operation.getSignature());
            } catch (IOException e) {
                throw new AssertionError("Unexpected IOException"); // should never happen
            }
            ids.put(operation, id);
            outgoing.add(operation);
            completeMessage(message);
            return id;
        }

        boolean hasOutgoingOperation(RMIOperation<?> operation) {
            return outgoing.contains(operation);
        }

        void removeOutgoingOperation(RMIOperation<?> operation) {
            outgoing.remove(operation);
        }
    }

    private static class Queues {
        private final EnumMap<RMIQueueType, ComposedMessageQueue> separateQueues = new EnumMap<>(RMIQueueType.class);

        Queues(RMIEndpoint.Side side) {
            separateQueues.put(RMIQueueType.DESCRIBE, new ComposedMessageQueue());
            separateQueues.put(RMIQueueType.RESPONSE, new ComposedMessageQueue());
            separateQueues.put(RMIQueueType.REQUEST, new ComposedMessageQueue());
            if (side.hasServer()) {
                separateQueues.put(RMIQueueType.ADVERTISE, new ComposedMessageQueue());
            }
        }

        ComposedMessageQueue get(RMIQueueType type) {
            return separateQueues.get(type);
        }
    }
}
