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


import com.devexperts.rmi.impl.RMIPromiseImpl;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIResponseMessage;
import com.devexperts.rmi.security.SecurityController;
import com.devexperts.rmi.task.RMIChannel;
import com.dxfeed.promise.Promise;

import java.util.concurrent.Executor;

/**
 * This class represents a request for single remote method execution
 * in an RMI framework.
 *
 * <p> An <code>RMIRequest</code> is created in a client-side {@link RMIClient} via
 * {@link RMIClient#createRequest(Object, RMIOperation, Object[]) createRequest(...)}
 * or {@link RMIClient#createRequest(RMIRequestMessage)} methods and then sent for remote execution.
 * The difference between one-way and two-way requests is that the former does
 * not receive any respond about its execution from remote side.
 *
 * <p> The <code>RMIRequest</code> provides both blocking and non-blocking RMI API.
 *
 * <p> For the first purpose one can use the {@link #getBlocking()} method which will
 * wait until the request execution is finished and then return the method invocation
 * result (or throw generated application error or corresponding {@link RMIException}
 * in case some RMI-layer failure occurred).
 *
 * <p> For the second purpose the {@link #getNonBlocking()} and {@link #getException()}
 * methods can be used. They return the execution result and occurred
 * {@link RMIException} correspondingly without blocking. In order to be notified
 * when the request execution is finished one can use {@link RMIRequestListener}
 * that can be set by {@link #setListener(RMIRequestListener)} method.
 *
 * <p> The request may be cancelled by user before it is completed. This can be done by
 * one of two methods: {@link #cancelWithConfirmation()} or {@link #cancelOrAbort()}.
 * The difference between them is that the former one may cancel the request only
 * if its actual execution did not start yet while the latter one will abort the
 * request in any phase of execution (if only it is not completed yet) and may interrupt
 * the corresponding working thread on server side.
 *
 * <p><b>This class is thread-safe.</b>
 */
public abstract class RMIRequest<T> {

    // ==================== Public API ====================

    /**
     * Sets the specified listener for this request. This listener will be
     * notified when the request become completed.
     *
     * @param listener {@link RMIRequestListener} to listen the request.
     * @throws NullPointerException if the listener can not be null.
     * @throws IllegalStateException if the listener has already been installed.
     */
    public abstract void setListener(RMIRequestListener listener);


    /**
     * Sets the specific {@link Executor} for perform notifications {@link RMIRequestListener} for this request.
     * Default request uses {@link Executor} with the {@link RMIClient}.
     * @param executor for perform notifications {@link RMIRequestListener} for this request.
     * @see RMIClient#getDefaultExecutor()
     */
    public abstract void setExecutor(Executor executor);

    /**
     * Initiates sending the request for remote execution.
     */
    public abstract void send();

    /**
     * This is the short-cut for {@link RMIRequestState#isCompleted()
     * getState().isCompleted()}.
     *
     * @return <tt>true</tt> if the request is completed.
     * @see RMIRequestState#isCompleted()
     */
    public abstract boolean isCompleted();

    /**
     * Returns <tt>true</tt> if this is a one-way request.
     * <p> One-way request differs from the two-way one by the fact that the former
     * gets {@link RMIRequestState#SUCCEEDED SUCCEEDED} state right after it becomes
     * sent and it does not receive any respond about its actual execution.
     *
     * @return <tt>true</tt> if this is a one-way request.
     * @deprecated Use <tt>{@link #getRequestMessage() getRequestMessage}().{@link RMIRequestMessage#getRequestType() getRequestType}()</tt>.
     */
    public abstract boolean isOneWay();

    /**
     * Cancels the request.
     * Successfully cancelled request gets {@link RMIRequestState#FAILED FAILED} state.
     *
     * <p> Unlike the {@link #cancelOrAbort()} method it will cancel the request
     * only if its actual execution did not started yet. On account of this
     * restriction this method can not cancel the request instantly for certain.
     * If the request was already executing remotely when this method was
     * called it gets the {@link RMIRequestState#CANCELLING CANCELLING} state and
     * a special cancellation message is send to remote side. Later such request
     * may complete normally as well as fail by
     * {@link RMIExceptionType#CANCELLED_BEFORE_EXECUTION CANCELLED_BEFORE_EXECUTION},
     * {@link RMIExceptionType#CANCELLED_DURING_EXECUTION CANCELLED_DURING_EXECUTION},
     * or {@link RMIExceptionType#CANCELLED_AFTER_EXECUTION CANCELLED_AFTER_EXECUTION}
     * depending on an outcome received from the remote side.
     * <p> More formally:
     *
     * <ul>
     * <li> If the current request state is {@link RMIRequestState#NEW NEW} then it
     * instantly becomes {@link RMIRequestState#FAILED FAILED} with exception of
     * type {@link RMIExceptionType#CANCELLED_BEFORE_EXECUTION CANCELLED_BEFORE_EXECUTION}.
     *
     * <li> If the current request state is {@link RMIRequestState#WAITING_TO_SEND WAITING_TO_SEND}
     * then an attempt to abort request sending locally is performed. In case of
     * success the request becomes {@link RMIRequestState#FAILED FAILED} by
     * {@link RMIExceptionType#CANCELLED_BEFORE_EXECUTION CANCELLED_BEFORE_EXECUTION}.
     * Otherwise the request gets {@link RMIRequestState#CANCELLING CANCELLING}
     * state and a cancellation message is send to remote side.
     *
     * <li> If the current request state is {@link RMIRequestState#SENDING SENDING}
     * or {@link RMIRequestState#SENT SENT}
     * then the request gets {@link RMIRequestState#CANCELLING CANCELLING}
     * state and a cancellation message is send to remote side.
     *
     * <li> If the current request state is {@link RMIRequestState#CANCELLING CANCELLING}
     * then nothing is done: the cancellation has been already initiated before.
     *
     * <li> If the current request state is {@link RMIRequestState#SUCCEEDED SUCCEEDED}
     * or {@link RMIRequestState#FAILED FAILED} then nothing is done: the request is
     * already completed.
     * </ul>
     */
    public abstract void cancelWithConfirmation();

    /**
     * Cancels this request depending on boolean confirmation flag.
     * Will {@link #cancelWithConfirmation()} when {@code true} and
     * {@link #cancelOrAbort()} when {@code false}.
     * @param confirmation confirmation flag.
     */
    public void cancelWith(boolean confirmation) {
        if (confirmation)
            cancelWithConfirmation();
        else
            cancelOrAbort();
    }

    /**
     * Cancels or aborts the request.
     * The request instantly gets {@link RMIRequestState#FAILED FAILED}
     * state with <tt>RMIException</tt> of type
     * {@link RMIExceptionType#CANCELLED_BEFORE_EXECUTION CANCELLED_BEFORE_EXECUTION}
     * or {@link RMIExceptionType#CANCELLED_DURING_EXECUTION CANCELLED_DURING_EXECUTION}.
     *
     * <p> Unlike the {@link #cancelWithConfirmation()} method it will
     * cancel or abort the request instantly irrespective of its execution
     * actual state (except the case it is already completed).
     * If the request is not send for remote execution yet then it will be
     * cancelled (which means that its execution did not started),
     * otherwise it will be aborted (which means that its real execution
     * status in fact is unknown).
     *
     * <p> More formally:
     * <ul>
     * <li> If the current request state is {@link RMIRequestState#NEW NEW} then it
     * instantly becomes {@link RMIRequestState#FAILED FAILED} with exception of
     * type {@link RMIExceptionType#CANCELLED_BEFORE_EXECUTION CANCELLED_BEFORE_EXECUTION}.
     *
     * <li> If the current request state is {@link RMIRequestState#WAITING_TO_SEND WAITING_TO_SEND}
     * then an attempt to abort request sending locally is performed. In case of
     * success the request becomes {@link RMIRequestState#FAILED FAILED} by
     * {@link RMIExceptionType#CANCELLED_BEFORE_EXECUTION CANCELLED_BEFORE_EXECUTION}.
     * Otherwise the request becomes failed {@link RMIRequestState#FAILED FAILED} with
     * {@link RMIExceptionType#CANCELLED_DURING_EXECUTION CANCELLED_DURING_EXECUTION} exception type
     * and an abortion message is send to remote side.
     *
     * <li> If the current request state is {@link RMIRequestState#SENDING SENDING}
     * or {@link RMIRequestState#CANCELLING CANCELLING} then the request becomes
     * failed {@link RMIRequestState#FAILED FAILED} with by
     * {@link RMIExceptionType#CANCELLED_DURING_EXECUTION CANCELLED_DURING_EXECUTION} exception type
     * and an abortion message is send to remote side.
     * <li> If the current request state is {@link RMIRequestState#SUCCEEDED SUCCEEDED}
     * or {@link RMIRequestState#FAILED FAILED} then nothing is done: the request is
     * already completed.
     * </ul>
     */
    public abstract void cancelOrAbort();

    /**
     * Returns the current state of the request.
     *
     * @return the current state of the request.
     * @see RMIRequestState
     */
    // NOTE: This method unmarshalls the actual outcome before returning the state.
    // It may change internal state from SUCCEEDED to FAILED in case it failed
    // to unmarshall the outcome but the general rule is that final state (the one
    // with isCompleted() == true) is never changed after it was once reported
    // outside.
    public abstract RMIRequestState getState();

    /**
     * This method waits the completion of the request and then returns
     * its execution result if it completes successfully or throws a
     * corresponding {@link RMIException} otherwise.
     *
     * @return result of the request execution in case it succeeded.
     * @throws RMIException if the request failed.
     */
    public abstract T getBlocking() throws RMIException;

    /**
     * Returns the request execution result or <code>null</code>
     * if it is not completed yet or failed.
     * <p> This method does not wait for request completion.
     *
     * @return the request execution result or <code>null</code>
     * if it is not completed yet or failed.
     */
    public abstract T getNonBlocking();

    /**
     * Returns the {@link RMIException} that occurred while executing the request
     * or <code>null</code> if it is not completed yet or did not fail.
     * <p> This method does not wait for request completion.
     *
     * @return the {@link RMIException} that occurred while executing the request
     * or <code>null</code> if it is not completed yet or did not fail.
     */
    public abstract RMIException getException();

    /**
     * Returns the time in milliseconds when the request sending was initiated.
     * @return the time in milliseconds when the request sending was initiated.
     */
    public abstract long getSendTime();

    /**
     * Returns the time in milliseconds when the request was started to be sent over the network.
     * @return the time in milliseconds when the request was started to be sent over the network.
     */
    public abstract long getRunningStartTime();

    /**
     * Returns the time in milliseconds when the request became completed.
     * @return the time in milliseconds when the request became completed.
     */
    public abstract long getCompletionTime();

    /**
     * Returns the subject that is associated with this request.
     * @return the subject that is associated with this request.
     * @see SecurityController
     */
    public abstract Object getSubject();

    /**
     * Returns the {@link RMIOperation} that this request performs.
     * @return the {@link RMIOperation} that this request performs.
     */
    public abstract RMIOperation<T> getOperation();

    /**
     * Returns the parameters that are passed to invoking method.
     * These parameters are always not null, even when the method has
     * no arguments (in this case it is an array with zero-length).
     *
     * @return the parameters that are passed to invoking method.
     */
    public abstract Object[] getParameters();

    /**
     * Return the request message of this request
     * @return the request message of this request
     */
    public abstract RMIRequestMessage<T> getRequestMessage();

    /**
     * Return the response message of this request
     * @return the response message of this request
     */
    public abstract RMIResponseMessage getResponseMessage();

    /**
     * Return result of this request.
     * The {@link #of(Promise) of} method does the inverse operation.
     *
     * @return {@link Promise} that will either return result or throw RMIException.
     */
    public abstract Promise<T> getPromise();

    /**
     * Returns {@link RMIChannel channel} inside of which a request is created if it is nested request. If it is
     * not a nested request, then it returns to {@link RMIChannel channel} that has been opened by this request.
     * @return channel
     */
    public abstract RMIChannel getChannel();

    /**
     * Returns request associated with the corresponding promise.
     * It is the inverse of {@link #getPromise()} method.
     *
     * @param promise the promise.
     * @param <T> the result type.
     * @return request associated with the corresponding promise or {@code null}.
     */
    public static <T> RMIRequest<T> of(Promise<T> promise) {
        return promise instanceof RMIPromiseImpl ? ((RMIPromiseImpl<T>) promise).getRequest() : null;
    }
}
