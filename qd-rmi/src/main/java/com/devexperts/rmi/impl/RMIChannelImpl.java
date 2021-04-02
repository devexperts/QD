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
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.message.RMICancelType;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.task.RMIChannel;
import com.devexperts.rmi.task.RMIChannelState;
import com.devexperts.rmi.task.RMIChannelType;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMIServiceImplementation;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import javax.annotation.concurrent.GuardedBy;

class RMIChannelImpl extends RMIClientPortImpl implements RMIChannel {
    static final IndexerFunction.LongKey<RMIChannelImpl> CHANNEL_INDEXER_BY_REQUEST_ID = value -> value.channelId;

    private final ChannelRequestSender requestSender = new ChannelRequestSender();
    private final RMIChannelOwner owner;
    private final long channelId;
    private final RMIChannelType type;

    @GuardedBy("this")
    private final IndexedSet<String, RMIService<?>> handlers = IndexedSet.create(RMIService.RMI_SERVICE_INDEXER);

    @GuardedBy("this")
    private volatile RMIChannelState state = RMIChannelState.NEW;

    @GuardedBy("this")
    private List<RMIRequestImpl<?>> preOpenOutgoingRequests; // initialized on first need, can be != null only when state = NEW

    @GuardedBy("this")
    private List<ServerRequestInfo> preOpenIncomingRequests; // initialized on first need, can be != null only when state = NEW

    /**
     * Connection is set to non-null by {@link #registerChannel(RMIConnection)}.
     * It means that channel was added to connection's ChannelsManager and needs to be removed from there on close.
     * This shall always happen before channel is open.
     */
    @GuardedBy("this")
    private RMIConnection connection;

    /**
     * This is to control that all task in a given channel execute sequentially.
     * Initialized lazily. The first task in the currently execution one, when it completes or suspends,
     * then it gets removed from this queue and the next one is submitted.
     */
    @GuardedBy("this")
    private ArrayDeque<RMIExecutionTaskImpl<?>> executionTasks;

    @SuppressWarnings("unchecked")
    RMIChannelImpl(RMIEndpointImpl endpoint, Marshalled<?> subject, long channelId, RMIChannelOwner owner) {
        super(endpoint, subject);
        assert subject != null; // always captured at the channel creation time
        this.owner = owner;
        type = owner.getChannelType();
        this.channelId = channelId;
    }

    @Override
    public Object getOwner() {
        return owner;
    }

    @Override
    public <T> RMIRequest<T> createRequest(RMIRequestType type, RMIOperation<T> operation, Object... parameters) {
        return new RMIRequestImpl<>(requestSender, this, createRequestMessage(type, operation, parameters));
    }

    @Override
    public <T> RMIRequest<T> createRequest(RMIRequestMessage<T> message) {
        return new RMIRequestImpl<>(requestSender, this, updateRequestMessage(message));
    }

    @Override
    public boolean isOpen() {
        return state == RMIChannelState.OPEN;
    }

    @Override
    public synchronized void addChannelHandler(RMIService<?> handler) {
        if (state != RMIChannelState.NEW)
            throw new IllegalStateException("The channel has already been opened or closed");
        if (handlers.containsKey(handler.getServiceName()))
            throw new IllegalArgumentException("Handler named " + handler.getServiceName() + " has been added");
        handlers.add(handler);
    }

    @Override
    public RMIChannelState getState() {
        return state;
    }

    @Override
    public <T> void addChannelHandler(T implementation, Class<T> handlerInterface) {
        addChannelHandler(new RMIServiceImplementation<>(implementation, handlerInterface, RMIService.getServiceName(handlerInterface)));
    }

    @Override
    public synchronized void removeChannelHandler(RMIService<?> handler) {
        handlers.removeValue(handler);
    }

    @Override
    public RMIChannelType getType() {
        return type;
    }

    @Override
    protected RequestSender getRequestSender() {
        return requestSender;
    }

    long getChannelId() {
        return channelId;
    }

    /**
     * Register this channel with connection, so that is can receive channel messages.
     */
    synchronized void registerChannel(RMIConnection connection) {
        assert this.connection == null;
        this.connection = connection;
        connection.channelsManager.addChannel(this);
    }

    @SuppressWarnings("unchecked")
    void open(RMIConnection connection) {
        List<ServerRequestInfo> preOpenIncomingRequests;
        synchronized (this) {
            if (state == RMIChannelState.CLOSED)
                return;
            if (connection.closed)
                return;
            if (type == RMIChannelType.CLIENT_CHANNEL) {
                // Client channels are registered when they become open
                assert this.connection == null;
                registerChannel(connection);
            }
            if (state == RMIChannelState.CANCELLING) {
                if (preOpenOutgoingRequests != null && !preOpenOutgoingRequests.isEmpty())
                    connection.requestsManager.addOutgoingRequest(preOpenOutgoingRequests.get(0));
                preOpenOutgoingRequests = null;
                this.preOpenIncomingRequests = null;
                return;
            }
            state = RMIChannelState.OPEN;
            if (preOpenOutgoingRequests != null) {
                preOpenOutgoingRequests.forEach(connection.requestsManager::addOutgoingRequest);
                preOpenOutgoingRequests = null;
            }
            // must submit incoming requests outside of lock
            preOpenIncomingRequests = this.preOpenIncomingRequests;
            this.preOpenIncomingRequests = null;
        }
        if (preOpenIncomingRequests != null) {
            for (ServerRequestInfo requestInfo : preOpenIncomingRequests)
                connection.messageProcessor.createAndSubmitTask(this, requestInfo);
        }
    }

    synchronized void close() {
        if (state == RMIChannelState.CLOSED)
            return;
        if (state == RMIChannelState.NEW) {
            preOpenOutgoingRequests = null;
            preOpenIncomingRequests = null;
        }
        state = RMIChannelState.CLOSED;
        if (connection != null) {
            connection.channelsManager.removeChannel(channelId, type);
            connection.tasksManager.notifyTaskCompleted(owner, channelId);
        }
    }

    synchronized RMIService<?> getHandler(String handlerName) {
        RMIService<?> result = handlers.getByKey(handlerName);
        if (result != null)
            return result;
        return handlers.getByKey("*");
    }

    @Override
    public String toString() {
        return "Channel{" +
            "channelId=" + channelId + ", " +
            "type=" + type + ", " +
            "owner=" + owner + ", " +
            "state=" + state + "}";
    }

    synchronized void cancel(RMICancelType cancel) {
        if (type == RMIChannelType.SERVER_CHANNEL) {
            if (cancel == RMICancelType.ABORT_RUNNING)
                ((RMITaskImpl<?>) owner).cancel();
            else
                ((RMITaskImpl<?>) owner).cancelWithConfirmation();
            return;
        }
        switch (state) {
        case NEW:
            if (preOpenOutgoingRequests == null)
                preOpenOutgoingRequests = new ArrayList<>();
            for (RMIRequestImpl<?> request : preOpenOutgoingRequests)
                request.setFailedState(RMIExceptionType.CHANNEL_CLOSED, null);
            preOpenOutgoingRequests.clear();
            break;
        case OPEN:
            connection.tasksManager.cancelAllTasks(getChannelId(), cancel.getId(), type);
            break;
        default:
            return;
        }
        //for top-level request
        RMIRequest<Void> cancelChannel = createRequest(new RMIRequestMessage<>(RMIRequestType.ONE_WAY,
            cancel == RMICancelType.ABORT_RUNNING ? RMIRequestImpl.ABORT_CANCEL : RMIRequestImpl.CANCEL_WITH_CONFIRMATION,
            0L));
        cancelChannel.setListener(request -> this.close());
        state = RMIChannelState.CANCELLING;
        cancelChannel.send();
    }

    // returns false when channel is already closed
    boolean addIncomingRequest(ServerRequestInfo request) {
        synchronized (this) {
            switch (state) {
            case NEW:
                if (preOpenIncomingRequests == null)
                    preOpenIncomingRequests = new ArrayList<>();
                preOpenIncomingRequests.add(request);
                return true; // wait until open
            case CLOSED:
                return false; // don't do anything -- too late (channel already closed)
            default:
                break; // try to submit outside of lock
            }
        }
        connection.messageProcessor.createAndSubmitTask(this, request);
        return true;
    }

    private void addOutgoingRequestImpl(RMIRequestImpl<?> request) {
        synchronized (this) {
            switch (state) {
            case NEW:
                if (preOpenOutgoingRequests == null)
                    preOpenOutgoingRequests = new ArrayList<>();
                preOpenOutgoingRequests.add(request);
                return; // ok
            case OPEN:
                connection.requestsManager.addOutgoingRequest(request);
                return; // ok
            case CANCELLING:
                if (preOpenOutgoingRequests == null)
                    connection.requestsManager.addOutgoingRequest(request);
                else
                    preOpenOutgoingRequests.add(request);
                return;
            }
            // otherwise (state==CLOSED) make failed outside of the lock!
        }
        // Must update request outside of the lock (see Lock Hierarchy in RMIRequestImpl)
        request.setFailedState(RMIExceptionType.CHANNEL_CLOSED, null);
    }

    private synchronized boolean dropPendingRequestImpl(RMIRequestImpl<?> request) {
        return preOpenOutgoingRequests != null && preOpenOutgoingRequests.remove(request);
    }

    Executor getExecutor() {
        return owner.getExecutor();
    }

    void enqueueForSubmissionSerially(RMIExecutionTaskImpl<?> executionTask) {
        boolean submitNow;
        synchronized (this) {
            if (executionTasks == null)
                executionTasks = new ArrayDeque<>(2);
            submitNow = executionTasks.isEmpty();
            executionTasks.add(executionTask);
        }
        if (submitNow) {
            if (!executionTask.submitExecutionNow())
                submitNextTask(executionTask);
        }
    }

    void submitNextTask(RMIExecutionTaskImpl<?> executionTask) {
        do {
            assert executionTask.submitNextNow();
            synchronized (this) {
                if (executionTasks.peekFirst() != executionTask)
                    return; // not a first task -- bail out
                executionTasks.removeFirst();
                // look at next task to execute
                executionTask = executionTasks.peekFirst();
                if (executionTask == null)
                    return; // not more tasks to execute
            }
        } while (!executionTask.submitExecutionNow());
    }

    private class ChannelRequestSender extends RequestSender {
        @Override
        RMIEndpointImpl getEndpoint() {
            return RMIChannelImpl.this.getEndpoint();
        }

        @Override
        public Executor getExecutor() {
            return RMIChannelImpl.this.getExecutor();
        }

        @Override
        public void addOutgoingRequest(RMIRequestImpl<?> request) {
            addOutgoingRequestImpl(request);
        }

        @Override
        public boolean dropPendingRequest(RMIRequestImpl<?> request) {
            return dropPendingRequestImpl(request);
        }
    }

}
