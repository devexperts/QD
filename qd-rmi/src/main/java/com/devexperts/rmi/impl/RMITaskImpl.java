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
import com.devexperts.rmi.RMIExecutionTask;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.message.RMIResponseMessage;
import com.devexperts.rmi.security.SecurityController;
import com.devexperts.rmi.task.RMIChannel;
import com.devexperts.rmi.task.RMIChannelType;
import com.devexperts.rmi.task.RMILocalService;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.util.IndexerFunction;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.function.Function;

public class RMITaskImpl<T> extends RMITask<T> implements RMIChannelOwner {

    static final IndexerFunction.LongKey<RMITaskImpl<?>> TASK_INDEXER_BY_ID = value -> value.requestId;

    // ==================== lock hierarchy ====================

    /*
                       ServerSideServices                RunningTask
                       |                |                |         |
                       V                V                V         V
        ServerDescriptorManager  ServiceRouter     RMITask         RMIChannelImpl
                                                                           |
                                                                           V
                                                                     ChannelManager
     */
    // ==================== private instance fields ====================

    private final long requestId;
    private final TasksManager tasksManager;
    private final SecurityController securityController;
    final RMIChannelImpl channel;

    // both executor and executionTask are set immediately after construction and are effectively final after that
    private Executor executor;
    private RMIExecutionTaskImpl<T> executionTask;

    // ==================== constructors ====================

    static <T> RMITaskImpl<T> createTopLevelTask(Marshalled<?> subject, RMIRequestMessage<T> requestMessage, RMIConnection connection, long requestId) {
        RMITaskImpl<T> task = new RMITaskImpl<>(requestMessage, connection, requestId,
            owner -> new RMIChannelImpl(connection.endpoint, subject, requestId, owner));
        // Note: OneWay requests do not open a channel and need not be registered
        // See RMIExecutionTaskImpl.run
        if (requestMessage.getRequestType() != RMIRequestType.ONE_WAY)
            task.channel.registerChannel(connection);
        return task;
    }

    static <T> RMITaskImpl<T> createNestedTask(RMIRequestMessage<T> requestMessage, RMIConnection connection, RMIChannelImpl channel, long requestId) {
        return new RMITaskImpl<>(requestMessage, connection, requestId, owner -> channel);
    }

    private RMITaskImpl(RMIRequestMessage<T> requestMessage, RMIConnection connection, long requestId,
        Function<RMITaskImpl<?>, RMIChannelImpl> channelSource)
    {
        super(requestMessage, connection.variables());
        this.requestId = requestId;
        this.tasksManager = connection.tasksManager;
        this.securityController = connection.endpoint.getSecurityController();
        this.channel = channelSource.apply(this);
    }

    // ==================== protected impl methods ====================

    @Override
    protected RMIExceptionType getCancelTypeSyncImpl() {
        assert Thread.holdsLock(this);
        if (executionTask == null)
            return RMIExceptionType.CANCELLED_BEFORE_EXECUTION;
        switch (executionTask.getState()) {
        case NEW:
        case SUBMITTED:
            return RMIExceptionType.CANCELLED_BEFORE_EXECUTION;
        default:
            return RMIExceptionType.CANCELLED_DURING_EXECUTION;
        }
    }

    protected void suspendImpl() {
        suspendImpl(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void scheduleCallableOnResume(final Callable<T> callable) {
        executionTask.resume(new RMILocalService<T>("ContinuationService", null) {
            @Override
            public T invoke(RMITask<T> task) throws Exception {
                return callable.call();
            }
        });
    }

    // Makes it accessible in this package
    @Override
    protected void completeResponseImpl(RMIResponseMessage responseMessage, boolean submitNextInQueue) {
        super.completeResponseImpl(responseMessage, submitNextInQueue);
    }

    // NOTE: This method MUST NOT be called under task lock
    @Override
    protected void taskCompleted(RMITask<T> task, RMIResponseMessage response, boolean submitNextInQueue) {
        // ensure that response is fully serialized in this thread before notifying server-side
        response.getMarshalledResult().getBytes();
        if (!isNestedTask())
            channel.close();
        else
            tasksManager.notifyTaskCompleted(this);
        if (executionTask != null)
            executionTask.updateState(submitNextInQueue); // force sync of execution task's state
    }

    @Override
    protected void logError(RMIExceptionType type, Throwable exception) {
        if (type == RMIExceptionType.APPLICATION_ERROR && !(exception instanceof RuntimeException) && !(exception instanceof Error))
            return; // don't log checked application exceptions
        RMILog.logExecutionError(executionTask, type, exception);
    }

    // ==================== RMITask impl ====================

    @Override
    public RMIChannel getChannel() {
        return channel;
    }

    // ==================== RMIChannelOwner impl ====================

    @Override
    public Executor getExecutor() {
        return executor;
    }

    @Override
    public RMIChannelType getChannelType() {
        return RMIChannelType.SERVER_CHANNEL;
    }

    // ==================== other impl methods ====================

    public SecurityController getSecurityController() {
        return securityController;
    }

    public RMIExecutionTask<T> getExecutionTask() {
        return executionTask;
    }

    void setExecutionTask(RMIExecutionTaskImpl<T> executionTask) {
        this.executionTask = executionTask;
    }

    long getRequestId() {
        return requestId;
    }

    long getChannelId() {
        return channel.getChannelId();
    }

    void setExecutor(Executor executor) {
        this.executor = executor;
    }

}
