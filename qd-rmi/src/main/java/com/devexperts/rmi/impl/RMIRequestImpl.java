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

import com.devexperts.io.Marshalled;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.RMIRequestListener;
import com.devexperts.rmi.RMIRequestState;
import com.devexperts.rmi.message.RMICancelType;
import com.devexperts.rmi.message.RMIErrorMessage;
import com.devexperts.rmi.message.RMIRequestMessage;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.message.RMIResponseMessage;
import com.devexperts.rmi.message.RMIResultMessage;
import com.devexperts.rmi.message.RMIRoute;
import com.devexperts.rmi.task.RMIChannel;
import com.devexperts.rmi.task.RMIChannelState;
import com.devexperts.rmi.task.RMIChannelType;
import com.devexperts.rmi.task.RMIServiceId;
import com.dxfeed.promise.Promise;

import java.util.Comparator;
import java.util.concurrent.Executor;
import javax.annotation.concurrent.GuardedBy;

@SuppressWarnings({"ThrowableInstanceNeverThrown"})
public final class RMIRequestImpl<T> extends RMIRequest<T> implements RMIChannelOwner {
    // ==================== private static fields ====================

    static final Comparator<RMIRequestImpl<?>> REQUEST_COMPARATOR_BY_SENDING_TIME = (o1, o2) -> {
        int compare = Long.compare(o1.sendTime, o2.sendTime);
        if (compare != 0)
            return compare;
        return Long.compare(o1.id, o2.id);
    };

    static final RMIOperation<Void> ABORT_CANCEL = RMIOperation.valueOf("", Void.class, "ABORT_RUNNING", long.class);
    static final RMIOperation<Void> CANCEL_WITH_CONFIRMATION = RMIOperation.valueOf("", Void.class, "DEFAULT", long.class);

    static boolean isCancelOperation(RMIOperation<?> operation) {
        return operation.equals(ABORT_CANCEL) || operation.equals(CANCEL_WITH_CONFIRMATION);
    }

    // ==================== lock hierarchy ====================

    /*
                 QDEndpoint$Lock
                       |
                       V
                ClientSideServices   MessageComposer
                  |          |        |
                  V          V        V
          ServiceRouter     requestLock
                             |       |
                             V       V
                RMIChannelImpl    SentRequests
                  |         |
                  V         V
        channelsManager    OutgoingRequests
     */

    // ==================== private instance fields ====================

    private final long id;

    private volatile RMIRequestState state = RMIRequestState.NEW; // GuardedBy(this)

    /* INVARIANTS:
       NEW        -- definitely not in any map/set yet
       WAITING_TO_SEND    -- may be in endpoint or connection queue of outgoing requests
                     or in messageComposer.composedMessages
       SENDING    -- may be in assignedConnection.clientSide.sentRequests
       CANCELLING -- may be in assignedConnection.clientSide.sentRequests
                     my have assignedConnection.clientSide.requestCancellations
       SUCCEEDED, FAILED -- definitely already not in any map/set
     */

    private RequestSender requestSender;
    private final Marshalled<?> subject;
    private final RMIChannelImpl channel;
    private RMIRequestMessage<T> requestMessage;
    private RMIResponseMessage responseMessage;
    private Promise<T> promise;
    private volatile RMIServiceId tentativeTarget;

    private RMIRequestListener listener;

    private volatile long sendTime;
    private volatile long runningStartTime;
    private volatile long completionTime;

    private boolean wasUnmarshall;
    private final boolean nestedRequest;

    // NOTE: Must not invoke other lock-taking operation under this requestLock, with the exception
    // of marshalling/unmarshalling
    private final RequestLock requestLock = new RequestLock(); // a named object for a nice thread-dump

    /**
     * INVARIANT: assignedConnection definitely not null only when state == SENDING, assigned only when
     * state is WAITING_TO_SEND.
     */
    @GuardedBy("requestLock")
    private volatile RMIConnection assignedConnection;

    private Executor executor;

    private final RMIMessageKind kind;

    // ==================== Public API ====================

    // top-level request constructor
    public RMIRequestImpl(RequestSender requestSender, Marshalled<?> subject, RMIRequestMessage<T> requestMessage) {
        this.subject = subject;
        if ((requestSender == null) || (requestMessage.getOperation() == null))
            throw new NullPointerException();
        this.requestSender = requestSender;
        this.requestMessage = requestMessage;
        this.id = requestSender.createRequestId();
        tentativeTarget = requestMessage.getTarget();
        channel = new RMIChannelImpl(requestSender.getEndpoint(), this.subject, id, this);
        nestedRequest = false;
        this.kind = RMIMessageKind.REQUEST;
    }

    // nested request constructor
    public RMIRequestImpl(RequestSender requestSender, RMIChannelImpl channel, RMIRequestMessage<T> requestMessage) {
        this.subject = channel.getSubject();
        if ((requestSender == null) || (requestMessage.getOperation() == null))
            throw new NullPointerException();
        this.requestSender = requestSender;
        this.requestMessage = requestMessage;
        this.id = requestSender.createRequestId();
        tentativeTarget = null;
        this.channel = channel;
        nestedRequest = true;
        this.kind = channel.getType() == RMIChannelType.SERVER_CHANNEL ? RMIMessageKind.SERVER_CHANNEL_REQUEST : RMIMessageKind.CLIENT_CHANNEL_REQUEST;
    }

    @Override
    public void setListener(RMIRequestListener listener) {
        if (listener == null)
            throw new NullPointerException("The listener can not be null");
        Notifier notifier = null;
        synchronized (requestLock) {
            if (this.listener != null)
                throw new IllegalStateException("The listener has already been installed");
            this.listener = listener;
            if (isCompleted())
                notifier = new Notifier();
        }
        if (notifier != null)
            notifier.notifyRequestListener();
    }

    @Override
    public void setExecutor(Executor executor) {
        synchronized (requestLock) {
            if (state != RMIRequestState.NEW)
                throw new IllegalStateException("Executor can only be set before sending a request");
            this.executor = executor;
        }
    }

    @Override
    public void send() {
        long sendingStartTime = System.currentTimeMillis();
        synchronized (requestLock) {
            if (state != RMIRequestState.NEW)
                return; // bail out quickly if not new
        }
        try {
            requestMessage.getParameters().getBytes();
        } catch (Throwable t) {
            setFailedState(RMIExceptionType.PARAMETERS_MARSHALLING_ERROR, t);
            return;
        }
        synchronized (requestLock) {
            if (state != RMIRequestState.NEW)
                return; // somebody else did something bad
            this.sendTime = sendingStartTime;
            state = RMIRequestState.WAITING_TO_SEND;
        }
        // DO NOT GUARD client call by requestLock (there will be a deadlock!)
        requestSender.addOutgoingRequest(this);
    }

    @Override
    public boolean isCompleted() {
        return state.isCompleted();
    }

    @Override
    public boolean isOneWay() {
        return requestMessage.getRequestType() == RMIRequestType.ONE_WAY;
    }

    @Override
    public void cancelWithConfirmation() {
        cancel(RMICancelType.DEFAULT);
    }

    @Override
    public void cancelOrAbort() {
        cancel(RMICancelType.ABORT_RUNNING);
    }

    // NOTE: This method unmarshalls the actual outcome before returning the state.
    // It may change internal state from SUCCEEDED to FAILED in case it failed
    // to unmarshall the outcome but the general rule is that final state (the one
    // with isCompleted() == true) is never changed after it was once reported
    // outside.
    @Override
    public RMIRequestState getState() {
        synchronized (requestLock) {
            getResultImpl(); // may update state
            return state;
        }
    }

    // Lock-free method to check for sent state
    boolean isWaitingToSend() {
        return state == RMIRequestState.WAITING_TO_SEND;
    }

    @Override
    public T getBlocking() throws RMIException {
        synchronized (requestLock) {
            while (!isCompleted()) {
                try {
                    requestLock.wait();
                } catch (InterruptedException e) {
                    cancel(RMICancelType.ABORT_RUNNING);
                    Thread.currentThread().interrupt();
                }
            }
            switch (getState()) { // not just state, but getState(), because it unmarshalls the result and updates state
            case SUCCEEDED:
                return getResultImpl();
            case FAILED:
                //noinspection ConstantConditions
                throw getException();
            default:
                throw new AssertionError("Final state was expected");
            }
        }
    }

    @Override
    public T getNonBlocking() {
        synchronized (requestLock) {
            return getResultImpl();
        }
    }

    @Override
    public RMIException getException() {
        synchronized (requestLock) {
            getResultImpl(); // may update state to FAILED
            if (state == RMIRequestState.FAILED) {
                RMIException exception = (RMIException) responseMessage.getMarshalledResult().getObject();
                if (!exception.hasRequestInfo()) {
                    exception = new RMIException(exception, this);
                    responseMessage = new RMIErrorMessage(Marshalled.forObject(exception, RMIErrorMessage.getExceptionMarshaller()),
                        responseMessage.getRoute());
                }
                return exception;
            } else {
                return null;
            }
        }
    }

    @Override
    public long getSendTime() {
        return sendTime;
    }

    @Override
    public long getRunningStartTime() {
        return runningStartTime;
    }

    @Override
    public long getCompletionTime() {
        return completionTime;
    }

    @Override
    public Object getSubject() {
        return subject.getObject();
    }

    @Override
    public RMIOperation<T> getOperation() {
        return requestMessage.getOperation();
    }

    @Override
    public Object[] getParameters() {
        return requestMessage.getParameters().getObject();
    }

    @Override
    public RMIRequestMessage<T> getRequestMessage() {
        return requestMessage;
    }

    @Override
    public RMIChannelType getChannelType() {
        return RMIChannelType.CLIENT_CHANNEL;
    }

    @Override
    public RMIResponseMessage getResponseMessage() {
        return responseMessage;
    }

    @Override
    public Promise<T> getPromise() {
        Notifier notifier = null;
        synchronized (requestLock) {
            if (promise != null)
                return promise;
            promise = new RMIPromiseImpl<>(this);
            if (isCompleted())
                notifier = new Notifier();
        }
        if (notifier != null)
            notifier.notifyPromise();
        return promise;
    }

    @Override
    public RMIChannel getChannel() {
        return channel;
    }

    // ==================== Implementation ====================

    /* INVARIANTS:
       NEW        -- definitely not in any map/set yet
       WAITING_TO_SEND    -- may be in endpoint or connection queue of outgoing requests
                     or in messageComposer.composedMessages
       SENDING    -- may be in assignedConnection.clientSide.sentRequests
       CANCELLING -- may be in assignedConnection.clientSide.sentRequests
                     my have assignedConnection.clientSide.requestCancellations
       SUCCEEDED, FAILED -- definitely already not in any map/set
     */

    Marshalled<?> getMarshalledSubject() {
        return subject;
    }

    long getId() {
        return id;
    }

    long getChannelId() {
        return channel.getChannelId();
    }

    boolean isNestedRequest() {
        return nestedRequest;
    }

    RMIMessageKind getKind() {
        return kind;
    }

    boolean isCancelRequest() {
        return getRequestMessage().getOperation() == ABORT_CANCEL;
    }

    RMIServiceId getTentativeTarget() {
        // volatile read, no need to synchronize
        return tentativeTarget;
    }

    // NOTE: this method must be lock-free
    void setTentativeTarget(RMIServiceId tentativeTarget) {
        if (requestMessage.getTarget() == null)
            this.tentativeTarget = tentativeTarget; // volatile write, no need to synchronize
    }

    @Override
    public Executor getExecutor() {
        // We use executor only for notification. It can be sate while state is NEW
        // and is never used in this state, so there's always synchronization between
        // setExecutor() and getExecutor()
        if (executor != null)
            return executor;
        return executor = requestSender.getExecutor();
    }

    // Must be lock-free to honor lock-hierarchy
    void assignConnection(RMIConnection connection) {
        assignedConnection = connection;
    }

    /**
     * Try to switch request in {@link RMIRequestState#WAITING_TO_SEND} state to {@link RMIRequestState#SENDING} state
     * ( or {@link RMIRequestState#SUCCEEDED} if ONE_WAY) and associate with specified connection.
     * @param connection connection associated with request
     * @return {@code true} if transition was performed
     */
    boolean setSendingState(RMIConnection connection) {
        Notifier notifier = null;
        synchronized (requestLock) {
            if (state != RMIRequestState.WAITING_TO_SEND)
                return false;
            assert assignedConnection == connection; // must be set before setting to sending
            runningStartTime = System.currentTimeMillis();
            assert state == RMIRequestState.WAITING_TO_SEND;
            if (requestMessage.getRequestType() == RMIRequestType.ONE_WAY) {
                setSucceededStateInternal(Marshalled.forObject(null, getOperation().getResultMarshaller()), null);
                notifier = new Notifier();
            } else {
                state = RMIRequestState.SENDING;
                connection.requestsManager.addSentRequest(this);
                // must ensure that concurrent connection close does not leave a dangling request.
                // must check here (after "addSentRequest" !) if connection was closed
                if (connection.closed) {
                    setFailedStateInternal(RMIExceptionType.DISCONNECTION, null, null);
                    notifier = new Notifier();
                }
                // don't need to remove it from a closed connection -- it does not really matter
            }
        }
        // outside of the lock
        if (requestMessage.getRequestType() != RMIRequestType.ONE_WAY)
            requestSender.startTimeoutRequestMonitoringThread();
        if (notifier != null)
            notifier.notifyCompleted();
        return true;
    }

    void setSentState(RMIConnection connection) {
        synchronized (requestLock) {
            // Note: that below if applies to one way requests, too (they cannot be in SENDING state)
            if (state == RMIRequestState.SENDING) {
                assert assignedConnection == connection; // must be set before setting to sent
                state = RMIRequestState.SENT;
            }
            if (!nestedRequest) {
                RMIChannelState state = channel.getState();
                if (state == RMIChannelState.NEW || state == RMIChannelState.CANCELLING) {
                    channel.open(connection);
                }
            }
        }
    }


    // NOTE: never use it inside synchronized(lock) section, because it notifies listeners.
    // Use setSucceededStateInternal(...) in this case.
    // Note: the caller must ensure this request is not in any map/set
    @SuppressWarnings("unchecked")
    void setSucceededState(Marshalled<?> marshalledResult, RMIRoute route) {
        Notifier notifier;
        synchronized (requestLock) {
            if (isCompleted())
                return;
            setSucceededStateInternal((Marshalled<T>) marshalledResult, route);
            notifier = new Notifier();
        }
        notifier.notifyCompleted();
    }

    // NOTE: never use it from within synchronized(lock) section, because it notifies listeners.
    // Use setFailedStateInternal(...) in this case.
    // Note: the caller must ensure this request is not in any map/set
    void setFailedState(RMIExceptionType type, Throwable cause) {
        Notifier notifier;
        synchronized (requestLock) {
            if (isCompleted())
                return;
            setFailedStateInternal(type, cause, null);
            notifier = new Notifier();
        }
        notifier.notifyCompleted();
    }

    // NOTE: never use it from within synchronized(lock) section, because it notifies listeners.
    // Use setFailedStateInternal(...) in this case.
    // Note: the caller must ensure this request is not in any map/set
    void setFailedState(Marshalled<RMIException> marshalledCause, RMIRoute route) {
        Notifier notifier;
        synchronized (requestLock) {
            if (isCompleted())
                return;
            try {
                marshalledCause.ensureBytes();
                setFailedStateInternal(new RMIErrorMessage(marshalledCause, route));
            } catch (Throwable t) {
                setFailedStateInternal(new RMIErrorMessage(RMIExceptionType.RESULT_MARSHALLING_ERROR, t, route));
            } finally {
                notifier = new Notifier();
            }
        }
        notifier.notifyCompleted();
    }

    void abortOnTimeout(RMIRequestState expectedState) {
        assert expectedState == RMIRequestState.WAITING_TO_SEND || expectedState == RMIRequestState.SENT;
        Notifier notifier;
        synchronized (requestLock) {
            if (expectedState != state)
                return;
            RMIExceptionType type = state == RMIRequestState.WAITING_TO_SEND ?
                RMIExceptionType.REQUEST_SENDING_TIMEOUT : RMIExceptionType.REQUEST_RUNNING_TIMEOUT;
            if (type == RMIExceptionType.REQUEST_RUNNING_TIMEOUT)
                sendCancellationMessageInternal(RMICancelType.ABORT_RUNNING);
            // Failed state should be set after sending cancellation message because setting failed state
            // immediately terminates the channel on the client side
            setFailedStateInternal(type, null, null);
            notifier = new Notifier();
        }
        notifier.notifyCompleted();
    }

    // returns true if removed from any queue
    boolean removeFromSendingQueues() {
        // Note: cannot do dropPendingRequest under request lock
        if (requestSender.dropPendingRequest(this))
            return true;
        // otherwise -- was assigned to a specific connection
        synchronized (requestLock) {
            return assignedConnection != null && assignedConnection.requestsManager.removeOutgoingRequest(this);
        }
    }

    // This method may update state to FAILED on first attempt to get result
    @SuppressWarnings("unchecked")
    @GuardedBy("requestLock")
    private T getResultImpl() {
        if (requestMessage.getRequestType() == RMIRequestType.ONE_WAY || state != RMIRequestState.SUCCEEDED)
            return null;
        assert state == RMIRequestState.SUCCEEDED;
        if (wasUnmarshall)
            return (T) responseMessage.getMarshalledResult().getObject(); // we know it will not throw exception (!)
        wasUnmarshall = true;
        try {
            // May throw exception
            return (T) responseMessage.getMarshalledResult().getObject();
        } catch (Throwable t) {
            setFailedStateInternal(RMIExceptionType.RESULT_UNMARSHALLING_ERROR, t, responseMessage.getRoute());
            // NOTE: We don't need to do notifyCompleted() because it was already completed.
            return null;
        }
    }

    // Note: the caller must ensure this request is not in any map/set
    @GuardedBy("requestLock")
    private void setSucceededStateInternal(Marshalled<T> result, RMIRoute route) {
        if (!nestedRequest)
            channel.close();
        if (requestMessage.getRequestType() != RMIRequestType.ONE_WAY) {
            try {
                result.ensureBytes();
                if (!result.getMarshaller().equals(requestMessage.getOperation().getResultMarshaller()))
                    throw new IllegalArgumentException("used an incorrect marshaller");
            } catch (Throwable t) {
                setFailedState(RMIExceptionType.RESULT_MARSHALLING_ERROR, t);
                return;
            }
        }
        responseMessage = new RMIResultMessage<>(requestMessage.getOperation(), result, route);
        this.state = RMIRequestState.SUCCEEDED;
        this.completionTime = System.currentTimeMillis();
    }

    // Note: the caller must ensure this request is not in any map/set
    @GuardedBy("requestLock")
    private void setFailedStateInternal(RMIExceptionType type, Throwable cause, RMIRoute route) {
        setFailedStateInternal(new RMIErrorMessage(type, cause, route));
    }

    // Note: the caller must ensure this request is not in any map/set
    @GuardedBy("requestLock")
    private void setFailedStateInternal(RMIResponseMessage errorMessage) {
        if (!nestedRequest)
            channel.close();
        this.state = RMIRequestState.FAILED;
        this.responseMessage = errorMessage;
        this.completionTime = System.currentTimeMillis();
    }

    private void cancel(RMICancelType type) {
        Notifier notifier;
        boolean needToRemoveFromSendingQueue = false;
        synchronized (requestLock) {
            switch (state) {
            case NEW:
                if (!isNestedRequest())
                    channel.close();
                setFailedStateInternal(RMIExceptionType.CANCELLED_BEFORE_EXECUTION, null, null);
                break;
            case WAITING_TO_SEND:
                if (!isNestedRequest())
                    channel.close();
                needToRemoveFromSendingQueue = true;
                setFailedStateInternal(RMIExceptionType.CANCELLED_BEFORE_EXECUTION, null, null);
                break;
            case SENDING:
            case SENT:
                sendCancellationMessageInternal(type);
                if (type == RMICancelType.ABORT_RUNNING) {
                    // Note: In SENDING state we are already sending request to the other side and
                    // might have actually finished doing sending (but have not updated the state yet),
                    // so there is a chance the were are cancelling request during execution
                    state = RMIRequestState.FAILED;
                    responseMessage = new RMIErrorMessage(RMIExceptionType.CANCELLED_DURING_EXECUTION, null, null);
                    completionTime = System.currentTimeMillis();
                } else {
                    state = RMIRequestState.CANCELLING;
                    return; // not complete yet
                }
                break;
            case CANCELLING:
                if (type == RMICancelType.DEFAULT)
                    return; // not complete yet
                if (assignedConnection != null)
                    sendCancellationMessageInternal(RMICancelType.ABORT_RUNNING);
                setFailedStateInternal(RMIExceptionType.CANCELLED_DURING_EXECUTION, null, null);
                break;
            case FAILED:
            case SUCCEEDED:
                return; // nothing to do, was already notified
            default:
                throw new AssertionError("Unexpected non-final state: " + state);
            }
            notifier = new Notifier();
        }
        notifier.notifyCompleted();
        if (needToRemoveFromSendingQueue)
            removeFromSendingQueues();
    }

    private void sendCancellationMessageInternal(RMICancelType type) {
        if (!isNestedRequest()) {
            channel.cancel(type);
        } else {
            channel.createRequest(new RMIRequestMessage<>(RMIRequestType.ONE_WAY,
                type == RMICancelType.ABORT_RUNNING ? ABORT_CANCEL : CANCEL_WITH_CONFIRMATION, id)).send();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(nestedRequest ? "Nested " : "Top-level ")
            .append("Request{")
            .append("id=").append(id).append(", ")
            .append(requestMessage).append(", ");
        sb.append("state=").append(state).append(", ");
        if (nestedRequest)
            sb.append(", " + "channel=").append(channel).append(", ");
        sb.append("result=").append(responseMessage);
        sb.append("}");
        return sb.toString();
    }

    private class Notifier {
        // We give those fields the same name by design, to hider outer classes fields and
        // to make sure that we can use only them here.
        private final RMIRequestListener listenerNotifier = RMIRequestImpl.this.listener;
        private final Promise<T> promiseNotifier = RMIRequestImpl.this.promise;

        // GuardedBy(this)
        private Notifier() {
        }

        private void notifyCompleted() {
            synchronized (requestLock) {
                assert isCompleted();
                requestLock.notifyAll();
            }
            if (listenerNotifier != null || promiseNotifier != null) {
                getExecutor().execute(() -> {
                    if (listenerNotifier != null)
                        notifyRequestListener();
                    if (promiseNotifier != null)
                        notifyPromise();
                });
            }
        }

        private void notifyRequestListener() {
            listenerNotifier.requestCompleted(RMIRequestImpl.this);
        }

        private void notifyPromise() {
            RMIException e = getException();
            if (e == null)
                promiseNotifier.complete(getNonBlocking());
            else if (e.getType() == RMIExceptionType.APPLICATION_ERROR) {
                // the same behavior as in RMIInvocationHandler
                Throwable cause = e.getCause();
                RMIRequestInvocationHandler.extendRemoteStackTrace(cause);
                promiseNotifier.completeExceptionally(cause);
            } else
                promiseNotifier.completeExceptionally(e);
        }
    }

    // a named object for a nice thread-dump
    private static class RequestLock {
    }
}
