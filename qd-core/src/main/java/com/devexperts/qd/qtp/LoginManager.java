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
package com.devexperts.qd.qtp;

import com.devexperts.auth.AuthToken;
import com.devexperts.qd.qtp.auth.QDLoginHandler;
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.PromiseHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

class LoginManager implements PromiseHandler<AuthToken> {

    /**
     * <pre>
     *         Legend:
     *                  --- >    in updateState()
     *                  -*- >    in prepareProtocolDescriptor()
     *                  *** >    in loginSync()
     *                  *-* >    in updateAccessTokenSync()
     *                  +++ >    in completeLogin()
     *
     *  _____________________________________________________________________________________________
     *                                       *** > LOGIN
     *    1)               NEW               --- > START_DATA_PREPARING
     *                                       *** > END_LOGIN (if QDLoginHandler.getAuthToken() != null)
     *
     *
     *                                       *** > LOGIN_AND_START_DATA_PREPARING
     *    2)       START_DATA_PREPARING      -*- > FINISH_DATA_PREPARING
     *                                       *** > END_LOGIN (if QDLoginHandler.getAuthToken() != lastSendAccessToken)
     *
     *
     *                                       *** > LOGIN_AND_FINISH_DATA_PREPARING
     *    3)      FINISH_DATA_PREPARING      --- > WAITING_OTHER_SIDE
     *                                       *** > END_LOGIN (if QDLoginHandler.getAuthToken() != lastSendAccessToken)
     *
     *
     *                                       *** > LOGIN
     *    4)       WAITING_OTHER_SIDE        *** > END_LOGIN (if QDLoginHandler.getAuthToken() != lastSendAccessToken) + if reason != loginRequired => addMask
     *                                       +++ > COMPLETED
     *
     *
     *                                       --- > LOGIN_AND_START_DATA_PREPARING
     *    5)              LOGIN              *-* > END_LOGIN + if firstProtocolWasPrepared == true => addMask
     *
     *
     *                                       *-* > END_LOGIN
     *    6) LOGIN_AND_START_DATA_PREPARING  -*- > LOGIN_AND_FINISH_DATA_PREPARING
     *
     *
     *                                       --- > LOGIN
     *    7) LOGIN_AND_FINISH_DATA_PREPARING *-* > END_LOGIN
     *
     *
     *                                       --- > START_DATA_PREPARING (updateAfter)
     *    8)            END_LOGIN            --- > END_LOGIN (updateBefore) + if firstProtocolWasSend == true => addMask
     *                                       -*- > FINISH_DATA_PREPARING
     *
     * <pre>
     */
    private enum LoginState {
        NEW(false),
        LOGIN(true),
        END_LOGIN(false),
        LOGIN_AND_START_DATA_PREPARING(true),
        LOGIN_AND_FINISH_DATA_PREPARING(true),
        START_DATA_PREPARING(false),
        FINISH_DATA_PREPARING(false),
        WAITING_OTHER_SIDE(false),
        COMPLETED(false);

        private final boolean loginProcess;

        LoginState(boolean loginProcess) {
            this.loginProcess = loginProcess;
        }
    }

    private enum Action {
        ADD_MASK(false),
        LOGIN(true),
        NOTHING(false);

        private final boolean needLogin;

        Action(boolean needLogin) {
            this.needLogin = needLogin;
        }
    }

    private final MessageAdapter messageAdapter;
    private final String endpointName;
    private final QDLoginHandler handler;
    private AuthToken accessToken;
    private AuthToken lastSendAccessToken;
    private LoginState state;
    private boolean firstProtocolWasPrepared;
    private boolean firstProtocolWasSend;
    private final List<Promise<AuthToken>> promises = new CopyOnWriteArrayList<>();

    LoginManager(QDLoginHandler handler, MessageAdapter messageAdapter, String endpointName) {
        this.handler = handler;
        this.messageAdapter = messageAdapter;
        this.endpointName = endpointName;
        this.accessToken = handler.getAuthToken();
        this.state = LoginState.NEW;
    }

    void login(String reason) {
        Action action = loginSync(reason);
        if (action.needLogin) {
            Promise<AuthToken> authToken = handler.login(reason);
            authToken.whenDone(this);
            promises.add(authToken);
            return;
        }
        if (action == Action.ADD_MASK)
            messageAdapter.addMask(MessageAdapter.getMessageMask(MessageType.DESCRIBE_PROTOCOL));
    }

    //returns true if accessToken == null
    private synchronized Action loginSync(String reason) {
        accessToken =  handler.getAuthToken();
        boolean loginRequired = reason.startsWith(MessageAdapter.AUTHENTICATION_LOGIN_REQUIRED);
        if (accessToken != null && (!accessToken.equals(lastSendAccessToken) || loginRequired)) {
            LoginState current = state;
            if (current == LoginState.WAITING_OTHER_SIDE && loginRequired)
                return Action.NOTHING;
            state = LoginState.END_LOGIN;
            return current == LoginState.WAITING_OTHER_SIDE ? Action.ADD_MASK : Action.NOTHING;
        }
        switch (state) {
        case NEW:
        case WAITING_OTHER_SIDE:
            state = LoginState.LOGIN;
            break;
        case START_DATA_PREPARING:
            state = LoginState.LOGIN_AND_START_DATA_PREPARING;
            break;
        case FINISH_DATA_PREPARING:
            state = LoginState.LOGIN_AND_FINISH_DATA_PREPARING;
            break;
        default:
            throw new AssertionError(endpointName + ": state = " + state);
        }
        return Action.LOGIN;
    }

    synchronized void prepareProtocolDescriptor(ProtocolDescriptor desc) {
        firstProtocolWasPrepared = true;
        if (state == LoginState.LOGIN_AND_START_DATA_PREPARING)
            state = LoginState.LOGIN_AND_FINISH_DATA_PREPARING;
        else
            state = LoginState.FINISH_DATA_PREPARING;
        if (accessToken != null) {
            desc.setProperty(ProtocolDescriptor.AUTHORIZATION_PROPERTY, accessToken.toString());
            lastSendAccessToken = accessToken;
        }
    }

    synchronized void completeLogin() {
        state = LoginState.COMPLETED;
    }


    void updateState(boolean beforePreparing) {
        if (updateStateSync(beforePreparing))
            messageAdapter.addMask(MessageAdapter.getMessageMask(MessageType.DESCRIBE_PROTOCOL));
    }

    private synchronized boolean updateStateSync(boolean beforePreparing) {
        return beforePreparing ? updateBefore() : updateAfter();
    }

    void close() {
        promises.forEach(Promise::cancel);
    }

    //return false, always!
    private boolean updateBefore() {
        switch (state) {
        case NEW:
            state = LoginState.START_DATA_PREPARING;
            return false;
        case LOGIN:
            state = LoginState.LOGIN_AND_START_DATA_PREPARING;
            return false;
        case END_LOGIN:
            state = LoginState.START_DATA_PREPARING;
            return false;
        default:
            throw new AssertionError();
        }
    }

    //returns true if state == LoginState.END_LOGIN
    private boolean updateAfter() {
        boolean result;
        switch (state) {
        case FINISH_DATA_PREPARING:
            state = LoginState.WAITING_OTHER_SIDE;
            result = false;
            break;
        case LOGIN_AND_FINISH_DATA_PREPARING:
            state = LoginState.LOGIN;
            result = false;
            break;
        case END_LOGIN:
            if (!firstProtocolWasSend)
                state = LoginState.WAITING_OTHER_SIDE;
            result = firstProtocolWasSend;
            break;
        default:
            throw new AssertionError();
        }
        firstProtocolWasSend = true;
        return result;
    }

    @Override
    public void promiseDone(Promise<? extends AuthToken> promise) {
        if (promise.hasResult()) {
            if (updateAccessTokenSync(promise.getResult()))
                messageAdapter.addMask(MessageAdapter.getMessageMask(MessageType.DESCRIBE_PROTOCOL));
        }
        promises.remove(promise);
    }

    private synchronized boolean updateAccessTokenSync(AuthToken token) {
        if (!state.loginProcess)
            return false;
        this.accessToken = token;
        boolean addMask = (state == LoginState.LOGIN && firstProtocolWasPrepared);
        state = LoginState.END_LOGIN;
        return addMask;
    }
}
