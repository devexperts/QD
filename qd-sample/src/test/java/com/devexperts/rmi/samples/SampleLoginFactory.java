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
package com.devexperts.rmi.samples;

import com.devexperts.auth.AuthToken;
import com.devexperts.qd.qtp.MessageAdapterConnectionFactory;
import com.devexperts.qd.qtp.auth.QDLoginHandler;
import com.devexperts.qd.qtp.auth.QDLoginHandlerFactory;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIException;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.InvalidFormatException;
import com.dxfeed.promise.Promise;

import java.util.ArrayList;
import java.util.List;

@ServiceProvider
public class SampleLoginFactory implements QDLoginHandlerFactory {

    static final String PREFIX = "Sample";

    public SampleLoginFactory() {}

    @Override
    public QDLoginHandler createLoginHandler(String login, MessageAdapterConnectionFactory factory)
    throws InvalidFormatException
    {
        RMIEndpoint endpoint = factory.getEndpoint(RMIEndpoint.class);
        if (!login.startsWith(PREFIX) || endpoint == null)
            return null;
        String userPasswords = login.substring(login.indexOf(':') + 1);
        return new SampleLoginPlugin(userPasswords, endpoint);
    }

    private static class SampleLoginPlugin implements QDLoginHandler {

        private AuthToken token;
        private final RMIEndpoint client;
        private final AuthToken userPassword;
        private final List<Promise<AuthToken>> promises = new ArrayList<>();
        volatile boolean isLogin = false;

        SampleLoginPlugin(String userPassword, RMIEndpoint client) {
            this.client = client;
            this.userPassword = AuthToken.createBasicToken(userPassword);
        }

        @Override
        public Promise<AuthToken> login(String reason) {
            System.out.println("Login!");
            Promise<AuthToken> tokenPromise = new Promise<>();
            promises.add(tokenPromise);
            RMIRequest<AuthToken> request = client.getClient().getPort(null)
                .createRequest(AuthService.GET_ACCESS_TOKEN, userPassword);
            request.setListener(req -> {
                try {
                    token = (AuthToken) req.getBlocking();
                } catch (RMIException e) {
                    throw new SecurityException(e);
                }
                isLogin = true;
                for (Promise<AuthToken> promise : promises)
                    promise.complete(token);
            });
            tokenPromise.whenDone(promise ->  {
                request.cancelOrAbort();
                promises.remove(promise);
            });
            request.send();
            return tokenPromise;
        }

        @Override
        public AuthToken getAuthToken() {
            return token;
        }
    }
}
