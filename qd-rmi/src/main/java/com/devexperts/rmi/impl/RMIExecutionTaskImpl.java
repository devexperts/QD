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

import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIExecutionTask;
import com.devexperts.rmi.RMIExecutionTaskState;
import com.devexperts.rmi.message.RMIErrorMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.task.RMIService;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.util.TypedMap;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import javax.annotation.concurrent.GuardedBy;

/**
 * A task that is submitted to the endpoint executor on the server side
 * for every request.
 *
 * <p><b>This class is separate from {@link RMITask} only for backwards-compatibility.</b>
 *
 * @see RMIEndpoint#getDefaultExecutor()
 */
class RMIExecutionTaskImpl<T> extends RMIExecutionTask<T> {

    // ==================== private instance fields ====================

    private RMIService<T> service; // Note: service changes on "resume", guarded by this
    private final RMIConnection connection;
    private final long requestId;
    private final RMITaskImpl<T> task;
    private final Executor executor; // this is an executor of the initial service

    // State variables are guarded by task

    @GuardedBy("task")
    private volatile RMIExecutionTaskState state = RMIExecutionTaskState.NEW; // always use getState() !!! to sync with task.getState()

    // ==================== Public API ====================

    @Override
    public TypedMap getConnectionVariables() {
        return task.getConnectionVariables();
    }

    @Override
    public long getSubmissionTime() {
        return task.getSubmissionTime();
    }

    @Override
    public RMIExecutionTaskState getState() {
        synchronized (task) {
            switch (task.getState()) {
            case FAILED:
                state = RMIExecutionTaskState.FAILED;
                break;
            case SUCCEEDED:
                state = RMIExecutionTaskState.SUCCEEDED;
                break;
            case SUSPENDED:
                // make sure we don't overwrite any final states just in case...
                if (state == RMIExecutionTaskState.RUNNING)
                    state = RMIExecutionTaskState.SUSPENDED;
                break;
            }
            return state;
        }
    }

    // NOTE: This method MUST NOT be called under task lock
    void updateState(boolean submitNextInQueue) {
        if (submitNextNow() && submitNextInQueue)
            task.channel.submitNextTask(this);
    }

    @Override
    public boolean isOneWayRequest() {
        return task.getRequestMessage().getRequestType() == RMIRequestType.ONE_WAY;
    }

    @Override
    public RMITask<T> getTask() {
        return task;
    }

    // It is written to log on failures
    @Override
    public String toString() {
        return RMILog.composeExecutionTaskString(connection, requestId, task.getChannelId(), task.toString());
    }

    // ==================== Implementation ====================

    // State transition: NEW
    RMIExecutionTaskImpl(long requestId, RMIConnection connection,
        RMITaskImpl<T> task, RMIService<T> service, Executor executor)
    {
        this.task = task;
        this.connection = connection;
        this.service = service;
        this.requestId = requestId;
        this.executor = executor;
        task.setExecutionTask(this);
    }

    // State transition: NEW,SUSPENDED,RESUMED_WHILE_RUNNING -> SUBMITTED
    // NOTE: This method MUST NOT be called under task lock
    // returns false if failed to submit or it was already submitted.
    boolean submitExecutionNow() {
        try {
            if (makeSubmitted()) {
                // The code below is needed for backwards-compatibility with a legacy code that
                // provides ExecutorService implementation with the only "submit" method overridden,
                // because that was the method that was originally used in the initial version of QD RMI API.
                if (executor instanceof ExecutorService)
                    ((ExecutorService) executor).submit(this);
                else
                    executor.execute(this);
                return true;
            } else {
                return false;
            }
        } catch (RejectedExecutionException e) {
            // We cannot use task.completeExceptionally here, because it might produce very deep recursion
            // So, there is a special completeResponseImpl call to avoid recursion
            // Caller of this method will submit next task for execution upon false result from this submitExecutionNow
            task.logError(RMIExceptionType.EXECUTION_REJECTION, e);
            task.completeResponseImpl(new RMIErrorMessage(RMIExceptionType.EXECUTION_REJECTION, e, null), false);
            return false;
        }
    }

    // State transition: NEW -> SUBMITTED
    // State transition: SUSPENDED,RESUMED_WHILE_RUNNING -> SUBMITTED_ON_RESUME
    private boolean makeSubmitted() {
        synchronized (task) {
            switch (getState()) { // task might have been cancelled before we got here....
            case NEW:
                this.state = RMIExecutionTaskState.SUBMITTED;
                return true;
            case SUSPENDED:
            case RESUMED_WHILE_RUNNING:
                this.state = RMIExecutionTaskState.SUBMITTED_ON_RESUME;
                return true;
            default:
                return false;
            }
        }
    }

    @Override
    public void run() {
        RMIService<T> service = makeRunning();
        if (service == null)
            return;
        try {
            if (!task.isNestedTask()) {
                if (task.isCompleted())
                    return;
                // Note: OneWay requests are not even registered when they are created
                // See RMITaskImpl.createTopLevelTask
                if (!isOneWayRequest()) {
                    service.openChannel(task);
                    task.channel.open(connection);
                }
            }
            if (!task.isCompleted())
                service.processTask(task);
        } catch (Throwable e) {
            task.completeExceptionally(RMIExceptionType.EXECUTION_ERROR, e);
        } finally {
            finishRun();
        }
    }

    // State transition: SUBMITTED,SUBMITTED_ON_RESUME -> RUNNING
    // And returns currently scheduled service to execute (initial or the most recent resume)
    private RMIService<T> makeRunning() {
        RMIService<T> result = null;
        boolean submitNextNow;
        synchronized (task) {
            switch (getState()) {
            case SUBMITTED:
            case SUBMITTED_ON_RESUME:
                this.state = RMIExecutionTaskState.RUNNING;
                result = this.service;
            }
            submitNextNow = submitNextNow();
        }
        if (submitNextNow)
            task.channel.submitNextTask(this);
        return result;
    }

    // State transition: RESUMED_WHILE_RUNNING -> SUBMITTED
    // State transition: SENDING -> SUSPENDED or FAILED if task cancel listener was not set
    private void  finishRun() {
        boolean resubmitExecution = false;
        RMIExceptionType exceptionType = null; // complete exception if not null
        synchronized (task) {
            switch (getState()) { // task might have been cancelled before we got here....
            case RESUMED_WHILE_RUNNING:
                resubmitExecution = true; // was suspended & already resumed -- restart execution
                break;
            case RUNNING:
                // If service is still running, then make sure it had installed cancel listener (regardless of anything else)
                if (!task.hasCancelListener()) {
                    exceptionType = RMIExceptionType.TASK_CANCEL_LISTENER_NOT_SET;
                } else {
                    task.suspendImpl();
                }
            }
        }
        if (exceptionType != null)
            task.completeExceptionally(exceptionType, null);
        if (resubmitExecution && submitExecutionNow())
            return; // done -- resubmitted this execution
        // otherwise -- try to submit next one in this channel
        task.channel.submitNextTask(this);
    }

    // State transition: IN_PROCESS -> WAITING_PROCESS
    void resume(RMIService<T> continuationService) {
        boolean submitExecution = false;
        synchronized (task) {
            this.service = continuationService;
            switch (getState()) { // task might have been cancelled before we got here....
            case SUSPENDED:
                submitExecution = true;
                break;
            case RUNNING:
                state = RMIExecutionTaskState.RESUMED_WHILE_RUNNING;
                break;
            }
        }
        if (submitExecution)
            enqueueForSubmissionSerially();
    }

    void enqueueForSubmissionSerially() {
        task.channel.enqueueForSubmissionSerially(this);
    }

    boolean submitNextNow() {
        synchronized (task) {
            RMIExecutionTaskState state = getState();
            // Note: running top-level task always releases channel tasks that wait it, because
            // channel tasks execute sequentially among themselves but concurrently with their parent task
            return state.isCompleted() || state.isSuspended() ||
                !task.isNestedTask() && state == RMIExecutionTaskState.RUNNING;
        }
    }
}
