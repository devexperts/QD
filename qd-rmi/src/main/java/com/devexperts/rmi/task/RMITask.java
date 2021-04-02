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
package com.devexperts.rmi.task;


import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.io.Marshalled;
import com.devexperts.logging.Logging;
import com.devexperts.rmi.RMIClient;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.impl.RMITaskImpl;
import com.devexperts.rmi.message.RMIErrorMessage;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIResponseMessage;
import com.devexperts.rmi.message.RMIResponseType;
import com.devexperts.rmi.message.RMIResultMessage;
import com.devexperts.rmi.message.RMIRoute;
import com.devexperts.util.TypedMap;
import com.dxfeed.promise.Promise;

import java.io.InvalidClassException;
import java.util.concurrent.Callable;

/**
 * This class is container for storing task that need to execute on some remote {@link RMIService}.
 * The RMITask contains: {@link RMIRequestMessage parameters of the operation}, {@link RMITaskState the execution state},
 * {@link RMIResponseMessage response} from the {@link RMIService}.
 * The RMITask has two final states ({@link RMITaskState#SUCCEEDED SUCCEEDED} and {@link RMITaskState#FAILED FAILED}).
 * After the transition to these states, the RMITask could not will change. The {@link RMIResponseMessage}
 * is <code>null</code>, if {@link RMITaskState state} is not {@link RMITaskState#isCompleted() final}.
 *
 * <p>If the RMITask is completed successfully on the {@link RMIService}, it is transitioned to
 * {@link RMITaskState#SUCCEEDED} state by the method {@link #complete(Object)} or
 * {@link #completeResponse(RMIResponseMessage)}.
 *
 * <p>If the RMITask failed normally execute on the {@link RMIService}, it is transitioned to {@link RMITaskState#FAILED}
 * state by the method {@link #completeExceptionally(Throwable)} or {@link #completeResponse(RMIResponseMessage)}.
 *
 * <p>If the RMITask is interrupted, it is transitioned to a final state by the method {@link #cancel(RMIExceptionType)}
 *
 * <p>If the {@link RMIClient client} has canceled the task with confirmation, it is transitioned to
 * {@link RMITaskState#CANCELLING} state by the method {@link #cancelWithConfirmation()}.
 *
 * <p>RMITask supports two kinds of event notifications:
 *
 * <ul>
 *     <li>
 *         {@link RMITaskCancelListener} interface with a single
 *         {@link RMITaskCancelListener#taskCompletedOrCancelling(RMITask)} method that provides notification to a
 *         {@link RMIService} implementation that RMITask had completed for any reason or is requested to be canceled
 *          with confirmation.
 *     </li>
 *     <li>
 *         There is a protected abstract method {@link #taskCompleted(RMITask, RMIResponseMessage, boolean)} that provides
 *         notification to a party that had created this RMITask, that the task had completed and what
 *         {@link RMIResponseMessage} has generated during transition to this final state.
 *     </li>
 * </ul>
 *
 * <p>{@link RMIService} implementation must {@link #setCancelListener(RMITaskCancelListener) set}
 * {@link RMITaskCancelListener} even if it does not support cancellation.
 * {@link RMITaskCancelListener cancelListener} can be set only once. When this notification is invoked because
 * the RMITask was cancelled, the task already in {@link RMITaskState#CANCELLING} or {@link RMITaskState#FAILED}
 * states depending on the type of incoming cancel and it should invoke cancel to transition (non-final)
 * {@link RMITaskState#CANCELLING} state into {@link RMITaskState#FAILED}.
 *
 * <p><b>This class is thread-safe.</b>
 *
 * @param <T> type of the task's expected result or a super class of it.
 */
public abstract class RMITask<T> {
    // ==================== private static fields ====================

    private static final Logging log = Logging.getLogging(RMITask.class);

    private static final RMITaskCancelListener DEFAULT_CANCEL_LISTENER = RMITask::cancel;

    // ==================== package-private static fields ====================

    static final ThreadLocal<RMITaskImpl<?>> THREAD_TASK = new ThreadLocal<>();

    // ==================== private instance fields ====================

    private final RMIRequestMessage<T> requestMessage;

    private RMITaskCancelListener cancelListener = DEFAULT_CANCEL_LISTENER; // guarded by this

    private final long submissionTime;
    private final TypedMap connectionVariables;
    private TypedMap taskVariables; // guarded by this, initialized on first use

    private volatile RMITaskState state = RMITaskState.ACTIVE;  // guarded by this, unsync read
    private volatile RMIResponseMessage responseMessage;

    // ==================== public methods ====================

    /**
     * Returns the task that is currently executed by a thread, or <code>null</code> if no task is being executed.
     * @return the task that is currently executed by a thread, or <code>null</code> if no task is being executed.
     */
    public static RMITask<?> current() {
        return THREAD_TASK.get();
    }

    /**
     * Returns the task that is currently executed by a thread and checks that is has
     * an expected result type or its super class.
     * @param resultType expected result type.
     * @return the task that is currently executed by a thread.
     * @throws IllegalStateException if not task is currently executing or task's result class is not an super class
     *         or the expected one.
     */
    @SuppressWarnings("unchecked")
    public static <T> RMITask<T> current(Class<T> resultType) {
        RMITask<?> task = THREAD_TASK.get();
        if (task == null)
            throw new IllegalStateException("No task is currently executing");
        try {
            Class<?> taskResultType = task.getOperation().getResultMarshaller().getClasses(resultType.getClassLoader())[0];
            if (!taskResultType.isAssignableFrom(resultType))
                throw new IllegalStateException("Incompatible result type: " + taskResultType.getName() + ", expected: " + resultType.getName());
        } catch (InvalidClassException e) {
            throw new IllegalStateException("Cannot unmarshall result type is the expected class loader: " + task.getOperation().getResultMarshaller().getTypes(), e);
        }
        return (RMITask<T>) task;
    }

    /**
     * Returns time in milliseconds when this task was submitted for execution.
     * @return time in milliseconds when this task was submitted for execution.
     */
    public long getSubmissionTime() {
        return submissionTime;
    }

    /**
     * Returns {@link RMIRequestMessage requestMessage} of task.
     * @return {@link RMIRequestMessage requestMessage} of task.
     */
    public RMIRequestMessage<T> getRequestMessage() {
        return requestMessage;
    }

    /**
     * Returns {@link RMIOperation operation} of task.
     * @return {@link RMIOperation operation} of task.
     */
    public RMIOperation<T> getOperation() {
        return requestMessage.getOperation();
    }

    /**
     * Returns map of per-connection variables for this request. One of the most important connection
     * variables is {@link TransportConnection#REMOTE_HOST_ADDRESS_KEY}. Application can
     * define and store its custom connection variables in this map.
     * @return map of per-connection variables for this request.
     */
    public TypedMap getConnectionVariables() {
        return connectionVariables;
    }

    /**
     * Returns map of per-task variables. This map can be used to attach any external
     * information to this task object.
     * @return map of per-task variables.
     */
    public synchronized TypedMap getTaskVariables() {
        if (taskVariables == null)
            taskVariables = new TypedMap();
        return taskVariables;
    }

    /**
     * Returns {@link RMIResponseMessage response message} of task. If the {@link #getState() current state}
     * of the task equal {@link RMITaskState#ACTIVE} or {@link RMITaskState#CANCELLING},
     * then this method returns {@code null}.
     * @return {@link RMIResponseMessage response message} of task.
     */
    public RMIResponseMessage getResponseMessage() {
        return responseMessage;
    }

    /**
     * Returns the current state of the task.
     * @return the current state of the task.
     * @see RMITaskState
     */
    public RMITaskState getState() {
        return state;
    }

    /**
     * Returns {@link RMIChannel channel} inside of which a task is processed if it is nested task. If it is
     * not a nested task, then it returns a {@link RMIChannel channel} that has been opened by this task.
     *
     * @return channel
     */
    public abstract RMIChannel getChannel();

    /**
     * Sets {@link RMITaskCancelListener RMITaskCancelListener} that provides notification to a service
     * implementation that task had completed for any reason or is requested to be canceled with confirmation.
     * The listener's {@link RMITaskCancelListener#taskCompletedOrCancelling(RMITask) taskCompletedOrCancelling} method
     * is immediately invoked if the task is already complete and is being cancelled.
     *
     * <p>{@link RMITaskCancelListener RMITaskCancelListener} can be set only once.
     * If listener is set again, then {@link IllegalStateException} is thrown.
     *
     * @param listener the listener that provides notification to a service
     *   implementation that task had completed for any reason or is requested to be canceled with confirmation.
     * @see RMIService#openChannel(RMITask)
     */
    public void setCancelListener(RMITaskCancelListener listener) {
        if (listener == null)
            throw new NullPointerException();
        boolean notifyImmediately = false;
        synchronized (this) {
            RMITaskCancelListener oldTaskCancelListener = getTaskCancelListener(cancelListener);
            if (oldTaskCancelListener != DEFAULT_CANCEL_LISTENER)
                throw new IllegalStateException();
            if (cancelListener instanceof RMITaskCancelListenerImpl)
                cancelListener = new RMITaskCancelListenerImpl(listener,
                    ((RMITaskCancelListenerImpl) cancelListener).suspendedCancelListener);
            else
                cancelListener = listener;
            if (state.isCompletedOrCancelling())
                notifyImmediately = true;
        }
        if (notifyImmediately)
            listener.taskCompletedOrCancelling(this);
    }

    /**
     * Returns {@code true}, if the {@link RMITaskCancelListener cancelListener} was installed, otherwise {@code false}.
     * @return {@code true}, if the {@link RMITaskCancelListener cancelListener} was installed.
     */
    public synchronized boolean hasCancelListener() {
        return getTaskCancelListener(cancelListener) != DEFAULT_CANCEL_LISTENER;
    }

    /**
     * Returns {@code true} when task has completed.
     * @return {@code true} when task has completed.
     */
    public boolean isCompleted() {
        return state.isCompleted();
    }

    /**
     * Completes task normally with a specified result. Task changes its state to {@link RMITaskState#SUCCEEDED}
     *
     * <p>If completed normally, then {@link #getResponseMessage()} will return the {@link RMIResultMessage}
     * with a specified result.
     *
     * <p>This method does nothing if the task is {@link #isCompleted() completed}.
     *
     * @param result the result of task
     */
    // NOTE: This method MUST NOT be called under task lock
    public void complete(T result) {
        completeResponse(new RMIResultMessage<>(requestMessage.getOperation(), result));
    }

    /**
     * Completes task exceptionally with a specified application exception.
     * Task changes its state to {@link RMITaskState#FAILED}
     *
     * <p>If completed exceptionally, then {@link #getResponseMessage()} will return the {@link RMIErrorMessage}
     * with a specified exception.
     *
     * <p>This method does nothing if the task is {@link #isCompleted() completed}.
     *
     * <p>This is a shortcut for
     * <code>{@link #completeExceptionally(RMIExceptionType, Throwable) completeExceptionally}({@link RMIExceptionType RMIExceptionType}.{@link RMIExceptionType#APPLICATION_ERROR APPLICATION_ERROR}, exception)</code>,
     * and it also <b>logs errors</b>, except for checked application exceptions.
     *
     * @param exception the application exception
     * @throws NullPointerException if {@link RMIException} is null.
     */
    public void completeExceptionally(Throwable exception) {
        completeExceptionally(RMIExceptionType.APPLICATION_ERROR, exception);
    }

    /**
     * Completes task exceptionally with a specified exception type and exception.
     * Task changes its state to {@link RMITaskState#FAILED}
     *
     * <p>If completed exceptionally, then {@link #getResponseMessage()} will return the {@link RMIErrorMessage}
     * with a specified exception.
     *
     * <p>This method does nothing if the task is {@link #isCompleted() completed}.
     *
     * <p>This method is shortcut for
     * <code>{@link #completeResponse(RMIResponseMessage) completeResponse}(new {@link RMIResponseMessage#RMIResponseMessage(RMIExceptionType, Throwable, RMIRoute) RMIResponseMessage}(type, exception, null))</code>,
     * but it also <b>logs errors</b>, except for checked application exceptions.
     *
     * @param exception the {@link RMIException}
     * @throws NullPointerException if {@link RMIException} is null and type is {@link RMIExceptionType#APPLICATION_ERROR}.
     */
    public void completeExceptionally(RMIExceptionType type, Throwable exception) {
        if (state.isCompleted())
            return; // fail fast, don't log twice, but we don't care about absolute consistency (sometimes may log twice)
        logError(type, exception);
        completeResponse(new RMIErrorMessage(type, exception, null));
    }

    /**
     * Completes task with a specified {@link RMIResponseMessage}.
     *
     * <p>Method {@link #getResponseMessage()} will return this specified {@link RMIResponseMessage}
     * If a specified {@link RMIResponseMessage} is {@link RMIErrorMessage}, then task changes its state
     * to {@link RMITaskState#FAILED}, else {@link RMITaskState#SUCCEEDED}.
     *
     * <p>This method does nothing if the task is {@link #isCompleted() completed}.
     *
     * @param responseMessage the responseMessage of task
     * @see #getResponseMessage()
     * @throws IllegalStateException
     */
    // NOTE: This method MUST NOT be called under task lock
    public void completeResponse(RMIResponseMessage responseMessage) {
        completeResponseImpl(responseMessage, true);
    }

    protected void completeResponseImpl(RMIResponseMessage responseMessage, boolean submitNextInQueue) {
        RMITaskCancelListener cancelListener;
        synchronized (this) {
            if (state.isCompleted())
                return;
            if (responseMessage.getType() == RMIResponseType.SUCCESS && state == RMITaskState.CANCELLING) {
                // cannot have result, because already was already cancelling
                responseMessage = new RMIErrorMessage(RMIExceptionType.CANCELLED_AFTER_EXECUTION, null, null);
            }
            responseMessage = ensureSerialized(responseMessage);
            this.responseMessage = responseMessage;
            state = responseMessage.getType() == RMIResponseType.ERROR ? RMITaskState.FAILED : RMITaskState.SUCCEEDED;
            cancelListener = this.cancelListener;
        }
        cancelListener.taskCompletedOrCancelling(this);
        taskCompleted(this, this.responseMessage, submitNextInQueue);
    }

    /**
     * Puts the task in a {@link RMITaskState#SUSPENDED SUSPENDED} state, if it was in an
     * {@link RMITaskState#ACTIVE ACTIVE} state and returns {@link RMIContinuation}.
     * Calling {@link RMIContinuation#resume(Callable) resume} method
     * on this resulting continuation, if the task has not been completed yet,
     * allocates an execution thread, transforms the task back to {@link RMITaskState#ACTIVE ACTIVE}
     * state, and invokes {@link Callable#call() call} method on the
     * argument of {@code resume} inside execution thread.
     * The resulting continuation shall be invoked at most once, otherwise {@link IllegalStateException}
     * may be produced when invoking it on a task that is already {@link RMITaskState#ACTIVE ACTIVE}.
     * The resulting continuation does nothing when the task is already
     * completed or is being cancelled.
     *
     * <p>When task is in {@link RMITaskState#SUSPENDED SUSPENDED} state the server thread can be released
     * (execution can return from the method) without producing a result or result default for primitives.
     *
     * <p> The goal of this method is to enable easy-to-use transition of methods that block RMI execution thread
     * while doing some background or remote action into methods that do not block RMI execution thread.
     *
     * @param listener {@link RMITaskCancelListener#taskCompletedOrCancelling(RMITask) taskCompletedOrCancelling} method is invoked
     *    if the original task completes normally or exceptionally, or goes into {@link RMITaskState#CANCELLING CANCELLING} state
     *    while it is being suspend and provides a chance to cancel whatever background process was going on
     *    while the task was suspended.
     * @return {@link RMIContinuation} that can be used to get an execution thread assigned this task again.
     * @throws IllegalStateException if the task is already in {@link RMITaskState#SUSPENDED} state
     *            or if this method was invoked by wrong thread (not the thread that is executing the task)
     */
    @SuppressWarnings("unchecked")
    public final RMIContinuation<T> suspend(RMITaskCancelListener listener) {
        if (listener == null)
            throw new NullPointerException();
        if (this != current()) {
            RuntimeException e =
                new IllegalStateException("RMITask.suspend method was invoked by wrong thread");
            completeExceptionally(RMIExceptionType.INVALID_SUSPEND_STATE, e);
            throw e;
        }
        if (!suspendImpl(listener)) {
            listener.taskCompletedOrCancelling(this);
            return (RMIContinuation<T>) RMIContinuation.EMPTY;
        }
        return new RMIContinuation<T>() {
            private volatile boolean wasUsed; // to fail fast if used improperly (twice)

            @Override
            public void resume(Callable<T> callable) {
                if (callable == null)
                    throw new NullPointerException();
                if (wasUsed) {
                    RuntimeException e =
                        new IllegalStateException("RMIContinuation can only be used once");
                    completeExceptionally(RMIExceptionType.INVALID_SUSPEND_STATE, e);
                    return;
                }
                wasUsed = true;
                if (resumeImpl())
                    scheduleCallableOnResume(callable);
            }
        };
    }

    /**
     * This method synchronizes the RMITask and {@link Promise}. If the task is active,
     * it will be moved to {@link RMITaskState#SUSPENDED SUSPENDED} state and completed SUCCEEDED or FAILED
     * in accordance with the promise.
     *
     * <p> When the task is completed independently of the promise, it will be closed.
     *
     * @param promise with which is synchronized task.
     */
    public void completePromise(Promise<T> promise) {
        promise.whenDone(p -> {
            if (p.hasResult())
                complete(p.getResult());
            else
                completeExceptionally(p.getException());
        });
        suspend(unused -> promise.cancel());
    }

    /**
     * Cancels the task. Task changes its state to {@link RMITaskState#FAILED} with
     * {@link RMIExceptionType#CANCELLED_BEFORE_EXECUTION RMIExceptionType.CANCELLED_BEFORE_EXECUTION} or
     * {@link RMIExceptionType#CANCELLED_DURING_EXECUTION RMIExceptionType.CANCELLED_DURING_EXECUTION}
     * exception type depending on the actual execution state,
     * {@link RMITaskCancelListener} notified about a change of state.
     *
     * <p>This is a shortcut for
     * <code>{@link #cancel(RMIExceptionType) cancel}(<b>null</b>)</code>
     * which corrects cancel type appropriately to the state.
     */
    // NOTE: This method MUST NOT be called under task lock
    public void cancel() {
        cancel(null);
    }

    /**
     * Cancel the task with type. Task changes its state to {@link RMITaskState#FAILED}.
     * {@link RMITaskCancelListener} notified about a change of state.
     *
     * @param type type of the exception, {@code null} to set
     * {@link RMIExceptionType#CANCELLED_BEFORE_EXECUTION RMIExceptionType.CANCELLED_BEFORE_EXECUTION} or
     * {@link RMIExceptionType#CANCELLED_DURING_EXECUTION RMIExceptionType.CANCELLED_DURING_EXECUTION}
     * based on the actual state of execution.
     */
    // NOTE: This method MUST NOT be called under task lock
    public void cancel(RMIExceptionType type) {
        RMITaskCancelListener cancelListener;
        synchronized (this) {
            if (type == null)
                type = getCancelTypeSyncImpl();
            if (state.isCompleted())
                return;
            responseMessage = new RMIErrorMessage(type, null, null);
            state = RMITaskState.FAILED;
            cancelListener = this.cancelListener;
        }
        cancelListener.taskCompletedOrCancelling(this);
        taskCompleted(this, responseMessage, true);
    }

    /**
     * Cancel the task with confirmation. Task changes its state to {@link RMITaskState#CANCELLING}.
     * {@link RMITaskCancelListener} notified about a change of state.
     * If {@link RMIService#processTask(RMITask)} fails to install {@link RMITaskCancelListener}, then RMITask completes
     * exceptionally.
     * This method does nothing if the task is {@link #isCompleted() completed}.
     */
    public void cancelWithConfirmation() {
        RMITaskCancelListener cancelListener;
        synchronized (this) {
            if (state.isCompleted())
                return;
            state = RMITaskState.CANCELLING;
            cancelListener = this.cancelListener;
        }
        cancelListener.taskCompletedOrCancelling(this);
    }

    /**
     * Returns <code>true</code> if this is an inner task processed within the {@link RMIChannel channel}.
     * @return <code>true</code> if this is an inner task  task processed within the {@link RMIChannel channel}.
     */
    public boolean isNestedTask() {
        return getChannel().getOwner() != this;
    }

    @Override
    public String toString() {
        // Note: It is use as a part of RMIExecutionTaskImpl.toString() method on processing failures
        StringBuilder sb = new StringBuilder();
        sb.append("state=").append(state.name()).append(", ").append("request=").append(requestMessage);
        if (responseMessage != null)
            sb.append(", ").append("response=").append(responseMessage);
        if (isNestedTask())
            sb.append(", ").append("channel=").append(getChannel());
        return sb.toString();
    }

    // ==================== protected methods for impl ====================

    protected abstract RMIExceptionType getCancelTypeSyncImpl();

    /**
     * Method provides notification to a party that had created this task,
     * that the task had completed and what {@link RMIResponseMessage response message} has generated
     * during transition to this final state.
     * @param task the RMITask
     * @param response the {@link RMIResponseMessage response message}
     * @param submitNextInQueue set to true (normally) if next task in the {@link RMIChannel} queue must be submitted,
     *                          false is set to avoid recursion.
     * @see #isCompleted()
     */
    protected abstract void taskCompleted(RMITask<T> task, RMIResponseMessage response, boolean submitNextInQueue);

    /**
     * Method schedule callable in queue task for RMI ThreadPool.
     * @param callable the {@link Callable}
     */
    protected abstract void scheduleCallableOnResume(Callable<T> callable);

    protected abstract void logError(RMIExceptionType type, Throwable exception);

    protected synchronized boolean suspendImpl(RMITaskCancelListener listener) {
        if (state == RMITaskState.SUSPENDED)
            throw new IllegalStateException("Task is already SUSPENDED");
        if (state.isCompletedOrCancelling())
            return false;
        assert state == RMITaskState.ACTIVE; // the only other state
        state = RMITaskState.SUSPENDED;
        cancelListener = new RMITaskCancelListenerImpl(getTaskCancelListener(this.cancelListener), listener);
        return true;
    }

    // ==================== protected constructor ====================

    /**
     * Creates RMI Task.
     */
    protected RMITask(RMIRequestMessage<T> requestMessage, TypedMap connectionVariables) {
        this.requestMessage = requestMessage;
        this.connectionVariables = connectionVariables;
        this.submissionTime = System.currentTimeMillis();
    }

    // ==================== private methods ====================

    private synchronized boolean resumeImpl() {
        if (state == RMITaskState.ACTIVE)
            throw new IllegalStateException("Task is already ACTIVE");
        if (state.isCompletedOrCancelling())
            return false;
        assert state == RMITaskState.SUSPENDED; // the only other state
        state = RMITaskState.ACTIVE;
        cancelListener = getTaskCancelListener(cancelListener); // remove continuation's cancel listener
        return true;
    }

    private RMIResponseMessage ensureSerialized(RMIResponseMessage responseMessage) {
        try {
            // try to serialize response
            responseMessage.getMarshalledResult().ensureBytes();
        } catch (Throwable t1) {
            responseMessage = new RMIErrorMessage(RMIExceptionType.RESULT_MARSHALLING_ERROR, t1, null);
            try {
                // try to serialize marshalling error
                responseMessage.getMarshalledResult().ensureBytes();
            } catch (Throwable t2) {
                // just in case...
                log.error("MarshallingException can not be serialized", t2);
                responseMessage = new RMIErrorMessage(RMIExceptionType.RESULT_MARSHALLING_ERROR, null, null);
                responseMessage.getMarshalledResult().ensureBytes(); // this should always serialize
            }
        }
        return responseMessage;
    }

    private static RMITaskCancelListener getTaskCancelListener(RMITaskCancelListener cancelListener) {
        return cancelListener instanceof RMITaskCancelListenerImpl ?
            ((RMITaskCancelListenerImpl) cancelListener).taskCancelListener : cancelListener;
    }

    public Marshalled<?> getSubject() {
        return getChannel().getSubject();
    }

    private static class RMITaskCancelListenerImpl implements RMITaskCancelListener {

        private final RMITaskCancelListener taskCancelListener;
        private final RMITaskCancelListener suspendedCancelListener;

        private RMITaskCancelListenerImpl(RMITaskCancelListener taskCancelListener,
            RMITaskCancelListener suspendedCancelListener)
        {
            this.taskCancelListener = taskCancelListener;
            this.suspendedCancelListener = suspendedCancelListener;
        }

        @Override
        public void taskCompletedOrCancelling(RMITask<?> task) {
            taskCancelListener.taskCompletedOrCancelling(task);
            if (suspendedCancelListener != null)
                suspendedCancelListener.taskCompletedOrCancelling(task);
        }
    }
}
