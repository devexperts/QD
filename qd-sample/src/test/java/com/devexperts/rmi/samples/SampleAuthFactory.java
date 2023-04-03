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
package com.devexperts.rmi.samples;

import com.devexperts.auth.AuthSession;
import com.devexperts.auth.AuthToken;
import com.devexperts.qd.qtp.MessageAdapterConnectionFactory;
import com.devexperts.qd.qtp.auth.BasicChannelShaperFactory;
import com.devexperts.qd.qtp.auth.QDAuthRealm;
import com.devexperts.qd.qtp.auth.QDAuthRealmFactory;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.RMIRequestState;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.TypedMap;
import com.dxfeed.promise.Promise;

import java.util.HashMap;
import java.util.Map;

@ServiceProvider
public class SampleAuthFactory implements QDAuthRealmFactory {

    static final String PREFIX = "Sample";

    public SampleAuthFactory() {}


    @Override
    public QDAuthRealm createAuthRealm(String auth, MessageAdapterConnectionFactory factory) {
        if (!auth.startsWith(PREFIX))
            return null;
        String address = auth.substring(auth.indexOf(':') + 1, auth.indexOf(';'));
        String subject = auth.substring(auth.indexOf(';') + 1);
        return new SampleAuthPlugin(address, subject);
    }

    private static class SampleAuthPlugin implements QDAuthRealm {

        private static final String AUTH_INFO = "You must login";

        private final String subject;
        private Map<AuthToken, AuthSession> sessionMap = new HashMap<>();
        private RMIEndpoint endpoint = RMIEndpoint.newBuilder()
            .withName("RealmClient ")
            .withSide(RMIEndpoint.Side.CLIENT)
            .build();

        SampleAuthPlugin(String address, String subject) {
            this.subject = subject;
            endpoint.connect(address);
        }

        @Override
        public Promise<AuthSession> authenticate(AuthToken authToken, TypedMap connectionVariables) {
            AuthSession session = getSession(authToken);
            if (session != null)
                return Promise.completed(session);

            RMIRequest<byte[]> request = endpoint.getClient()
                .getPort(subject)
                .createRequest(AuthService.GET_SESSION_TOKEN, authToken);
            Promise<AuthSession> sessionPromise = new Promise<>();
            sessionPromise.whenDone(promise -> {
                if (promise.isCancelled())
                    request.cancelOrAbort();
            });
            request.setListener(req -> requestComplete(authToken, sessionPromise, req));
            request.send();
            return sessionPromise;
        }

        @Override
        public String getAuthenticationInfo() {
            return AUTH_INFO;
        }

        private synchronized void requestComplete(
            AuthToken authToken, Promise<AuthSession> sessionPromise, RMIRequest<?> req)
        {
            System.out.println("req = " + req);
            if (req.getState() == RMIRequestState.SUCCEEDED) {
                AuthSession createdSession = new AuthSession(req.getNonBlocking());
                createdSession.variables().set(BasicChannelShaperFactory.CHANNEL_CONFIGURATION_KEY,
                    BasicChannelShaperFactory.ALL_DATA);
                sessionMap.put(authToken, createdSession);

                sessionPromise.complete(createdSession);
            } else {
                sessionPromise.completeExceptionally(req.getException());
            }
        }

        private synchronized AuthSession getSession(AuthToken accessToken) throws SecurityException {
            return sessionMap.get(accessToken);
        }
    }
}
