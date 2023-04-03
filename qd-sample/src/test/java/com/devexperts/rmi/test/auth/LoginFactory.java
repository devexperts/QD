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
package com.devexperts.rmi.test.auth;

import com.devexperts.auth.AuthToken;
import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.MessageAdapterConnectionFactory;
import com.devexperts.qd.qtp.auth.QDLoginHandler;
import com.devexperts.qd.qtp.auth.QDLoginHandlerFactory;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.RMIRequestState;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.InvalidFormatException;
import com.dxfeed.promise.Promise;

import java.util.ArrayList;
import java.util.List;

@ServiceProvider
public class LoginFactory implements QDLoginHandlerFactory {
    private static final Logging log = Logging.getLogging(LoginFactory.class);

    static final String PREFIX = "Simple";

    public LoginFactory() {}

    @Override
    public QDLoginHandler createLoginHandler(String login, MessageAdapterConnectionFactory factory)
        throws InvalidFormatException
    {
        RMIEndpoint endpoint = factory.getEndpoint(RMIEndpoint.class);
        if (!login.startsWith(PREFIX) || endpoint == null)
            return null;
        String[] userPasswords = login.substring(login.indexOf(':') + 1).split(";");
        return new LoginPlugin(userPasswords[0], userPasswords[1], Integer.valueOf(userPasswords[2]), endpoint);
    }

    private static class LoginPlugin implements QDLoginHandler {

        private long requestCount = 0;
        private int errorLoginCount;
        private AuthToken token;
        private final RMIEndpoint client;
        private final AuthToken badUserPassword;
        private final AuthToken goodUserPassword;
        private final List<Promise<AuthToken>> promises = new ArrayList<>();
        volatile boolean isLogin = false;

        LoginPlugin(String badUserPassword, String goodUserPassword, int errorLoginCount, RMIEndpoint client) {
            this.client = client;
            this.badUserPassword = AuthToken.valueOf(badUserPassword);
            this.goodUserPassword = AuthToken.valueOf(goodUserPassword);
            this.errorLoginCount = errorLoginCount;
        }

        @Override
        public Promise<AuthToken> login(String reason) {
            log.info("START LOGIN");
            RMIRequest<AuthToken> request = client.getClient().getPort(null).createRequest(
                SimpleAuthServer.GET_ACCESS_TOKEN,
                requestCount++ < errorLoginCount ? badUserPassword : goodUserPassword);
            Promise<AuthToken> tokenPromise = new Promise<>();
            tokenPromise.whenDone(promise -> {
                if (promise.isCancelled()) {
                    log.info("PROMISE CLOSE");
                    request.cancelOrAbort();
                } else {
                    log.info("PROMISE DONE");
                }
            });
            request.setListener(req -> {
                if (request.getState() == RMIRequestState.SUCCEEDED)
                    log.info("REQUEST DONE");
                else
                    log.info("REQUEST CLOSE");
                try {
                    token = (AuthToken) req.getBlocking();
                    tokenPromise.complete((AuthToken) req.getBlocking());
                } catch (RMIException e) {
                    SecurityException exception = new SecurityException(e);
                    tokenPromise.completeExceptionally(exception);
                    throw exception;
                }
                isLogin = true;
                for (Promise<AuthToken> promise : promises) {
                    promise.complete(token);
                    promises.remove(promise);
                }
            });
            request.send();
            log.info("END HANDLER LOGIN");
            return tokenPromise;
        }

        @Override
        public AuthToken getAuthToken() {
            return token;
        }
    }
}
