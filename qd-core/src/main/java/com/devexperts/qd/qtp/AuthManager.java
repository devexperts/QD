/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp;

import com.devexperts.auth.AuthSession;
import com.devexperts.auth.AuthToken;
import com.devexperts.auth.SessionCloseListener;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.auth.QDAuthRealm;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;
import com.devexperts.util.TypedMap;
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.PromiseHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.annotation.concurrent.GuardedBy;

class AuthManager implements PromiseHandler<AuthSession> {

    private static final long AUTHENTICATE_TIMEOUT = TimePeriod.valueOf(
        SystemProperties.getProperty(AuthManager.class, "authenticateTimeout", "5m")).getTime();

    private static final Logging log = Logging.getLogging(AuthManager.class);

    /**
     * <pre>
     *         Legend:
     *                  ----->    in updateState()
     *                  **** >    in authenticate() or callback methods
     *
     *  _____________________________________________________________________________________________
     *
     *    ************************************ > AUTHENTICATE              AUTH_FAILED ---
     *    *             ^                             ^     *                   ^         |
     *    *             *                             |     *                   *         |
     *    *     WAITING_OTHER_SIDE                    |     *********************         |
     *    *             ^                             |     *                   *         |
     *    *             |                             V     *                   V         |
     *   NEW ---> AUTH_PREPARING *** > AUTHENTICATE_AND_AUTH_PREPARING       AUTH_OK -----|-----> DATA_PREPARING
     *                  ^                                                                 |              |
     *                  |                                                                 |              V
     *                   -----------------------------------------------------------------           COMPLETED
     * </pre>
     */
    private enum AuthState {
        NEW,
        AUTH_PREPARING,
        AUTH_FAILED,
        AUTH_OK,
        DATA_PREPARING,
        WAITING_OTHER_SIDE,
        AUTHENTICATE,
        AUTHENTICATE_AND_AUTH_PREPARING,
        COMPLETED
    }

    private final MessageAdapter messageAdapter;
    private final QDAuthRealm realm;
    private AuthSession session;
    private String reason;
    private AuthState state = AuthState.NEW;
    private boolean firstAuthProtocolWasSent = false;
    private boolean authenticatePreparing;
    private TypedMap connectionVariables;
    private SessionCloseListener listener;
    private long startTime;

    // set to null when closed, does not add anymore
    @GuardedBy("this")
    private List<Promise<AuthSession>> promises = new ArrayList<>();

    AuthManager(MessageAdapter messageAdapter, QDAuthRealm realm) {
        this.messageAdapter = messageAdapter;
        this.realm = realm;
        this.reason = MessageAdapter.AUTHENTICATION_LOGIN_REQUIRED + realm.getAuthenticationInfo();
        this.startTime = System.currentTimeMillis();
    }

    long getAuthDisconnectTime() {
        return authIsOk() ? Long.MAX_VALUE : startTime + AUTHENTICATE_TIMEOUT;
    }

    void authenticate(AuthToken authToken, TypedMap connectionVariables) {
        authenticateSync(connectionVariables);
        Promise<AuthSession> authSession = realm.authenticate(authToken, connectionVariables);
        authSession.whenDone(this);
        syncAddPromise(authSession);
    }

    private synchronized void authenticateSync(TypedMap connectionVariables) {
        this.connectionVariables = connectionVariables;
        if (state == AuthState.AUTH_PREPARING)
            state = AuthState.AUTHENTICATE_AND_AUTH_PREPARING;
        else
            state = AuthState.AUTHENTICATE;
    }

    synchronized String getReason() {
        String temp = reason;
        reason = null;
        return temp;
    }

    synchronized boolean authenticatePreparing() {
        return authenticatePreparing;
    }

    synchronized boolean firstAuthProtocolWasSent() {
        return firstAuthProtocolWasSent;
    }

    void updateState(boolean beforePreparing) {
        if (updateSync(beforePreparing))
            messageAdapter.addMask(MessageAdapter.getMessageMask(MessageType.DESCRIBE_PROTOCOL));
    }

    //returns true if state == AuthState.AUTH_OK
    private synchronized boolean updateSync(boolean beforePreparing) {
        if (beforePreparing) {
            updateBefore();
            return false;
        }
        return updateAfter();
    }

    private synchronized boolean authIsOk() {
        return state == AuthState.AUTH_OK || state == AuthState.DATA_PREPARING || state == AuthState.COMPLETED;
    }


    @Override
    public void promiseDone(Promise<? extends AuthSession> promise) {
        if (promise.hasResult()) {
            log.info(messageAdapter + " authentication success");
            messageAdapter.reinitConfiguration(promise.getResult());
            boolean addMask = successSync(promise.getResult());
            if (addMask)
                messageAdapter.addMask(MessageAdapter.getMessageMask(MessageType.DESCRIBE_PROTOCOL));
        } else if (promise.hasException() && !promise.isCancelled()) {
            if (failSync(promise.getException().getMessage()))
                messageAdapter.addMask(MessageAdapter.getMessageMask(MessageType.DESCRIBE_PROTOCOL));
            log.warn(messageAdapter + " authentication FAIL: " + reason);
        }
        syncRemovePromise(promise);
    }

    private synchronized boolean successSync(AuthSession session) {
        if (this.session != null)
            return false;
        this.session = Objects.requireNonNull(session, "null auth session");
        connectionVariables.set(TransportConnection.SUBJECT_KEY, session.getSubject());
        boolean addMask = (state == AuthState.AUTHENTICATE_AND_AUTH_PREPARING ||
            (state == AuthState.AUTHENTICATE && firstAuthProtocolWasSent));
        state = AuthState.AUTH_OK;
        listener = (session1, closeReason) -> messageAdapter.close();
        session.addCloseListener(listener);
        return addMask;
    }

    private synchronized boolean failSync(String reason) {
        boolean addMask =  ((state == AuthState.AUTHENTICATE && firstAuthProtocolWasSent) ||
            (state == AuthState.AUTHENTICATE_AND_AUTH_PREPARING && this.reason == null));
        state = AuthState.AUTH_FAILED;
        this.reason = reason;
        return addMask;
    }

    void close() {
        List<Promise<AuthSession>> promises = syncClose();
        if (promises != null)
            promises.forEach(Promise::cancel);
    }

    private synchronized List<Promise<AuthSession>> syncClose() {
        if (session != null) {
            session.removeCloseListener(listener);
            session.close("Connection close");
        }
        List<Promise<AuthSession>> promises = this.promises;
        this.promises = null;
        return promises;
    }

    private void syncAddPromise(Promise<AuthSession> promise) {
        boolean added;
        synchronized (this) {
            if (added = (promises != null))
                promises.add(promise);
        }
        if (!added)
            promise.cancel();
    }

    private synchronized void syncRemovePromise(Promise<? extends AuthSession> promise) {
        if (promises != null)
            promises.remove(promise);
    }

    private void updateBefore() {
        switch (state) {
        case NEW:
            state = AuthState.AUTH_PREPARING;
            firstAuthProtocolWasSent = true;
            authenticatePreparing = true;
            break;
        case AUTH_FAILED:
            state = AuthState.AUTH_PREPARING;
            authenticatePreparing = true;
            break;
        case AUTH_OK:
            state = AuthState.DATA_PREPARING;
            break;
        case AUTHENTICATE:
            state = AuthState.AUTHENTICATE_AND_AUTH_PREPARING;
            firstAuthProtocolWasSent = true;
            authenticatePreparing = true;
            break;
        default:
            throw new AssertionError();
        }
    }

    //returns true if state == AuthState.AUTH_OK
    private boolean updateAfter() {
        authenticatePreparing = false;
        switch (state) {
        case AUTH_PREPARING:
            state = AuthState.WAITING_OTHER_SIDE;
            break;
        case AUTHENTICATE_AND_AUTH_PREPARING:
            state = AuthState.AUTHENTICATE;
            break;
        case AUTH_OK:
            return true;
        case AUTH_FAILED:
            break;
        case DATA_PREPARING:
            state = AuthState.COMPLETED;
            break;
        default:
            throw new AssertionError();
        }
        return false;
    }
}
