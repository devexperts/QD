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
package com.devexperts.rmi.test.auth;

import com.devexperts.auth.AuthToken;
import com.devexperts.logging.Logging;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.rmi.test.NTU;
import com.devexperts.util.Base64;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

class SimpleAuthServer {
    private static final Logging log = Logging.getLogging(SimpleAuthServer.class);
    private final AuthServiceImpl serviceImpl;
    private final int port;

    static interface AuthService {

        AuthToken getAccessToken(AuthToken token);

        byte[] getSessionToken(AuthToken token);
    }

    class AuthServiceImpl implements SimpleAuthServer.AuthService {

        private static final int TOKEN_LENGTH = 16;
        private static final int UID_SIZE = 16;

        private final AuthToken badClient;
        private final Map<AuthToken, AuthToken> accessTokens = Collections.synchronizedMap(new HashMap<>());
        private final Map<AuthToken, byte[]> sessionTokens = Collections.synchronizedMap(new HashMap<>());
        private final Random rnd = new Random(256);
        private final Object subject;

        AuthServiceImpl(Object subject, AuthToken badClient, AuthToken... clients) {
            this.subject = subject;
            this.badClient = badClient;
            for (AuthToken client : clients)
                accessTokens.put(client, null);
        }

        @Override
        public AuthToken getAccessToken(AuthToken userPassword) {
            if (!accessTokens.containsKey(userPassword) && !userPassword.equals(badClient))
                throw new SecurityException("user or password is wrong");
            if (userPassword.equals(badClient))
                return generatedToken();
            AuthToken token = generatedToken();
            accessTokens.putIfAbsent(userPassword, token);
            byte[] session = generatedSessionByte(token);
            sessionTokens.putIfAbsent(token, session);
            return token;
        }

        @Override
        public byte[] getSessionToken(AuthToken token) {
            if (!RMITask.current().getSubject().getObject().equals(subject))
                throw new SecurityException();
            byte[] session = sessionTokens.get(token);
            if (session == null) {
                errorLoginCount++;
                throw new SecurityException("Access token is wrong");
            }
            return session;
        }

        private byte[] generatedSessionByte(AuthToken token) {
            byte[] session;
            session = new byte[UID_SIZE];
            ByteBuffer buf = ByteBuffer.wrap(session);
            UUID uuid = UUID.nameUUIDFromBytes(Base64.DEFAULT.decode(token.getValue()));
            buf.putLong(uuid.getMostSignificantBits());
            buf.putLong(uuid.getLeastSignificantBits());
            log.info("generate session token:" + Arrays.toString(session));
            return session;
        }

        private AuthToken generatedToken() {
            byte[] bytes = new byte[TOKEN_LENGTH];
            rnd.nextBytes(bytes);
            AuthToken token = AuthToken.createBearerToken(Base64.DEFAULT.encode(bytes));
            return token;
        }

        private void close() {
            accessTokens.clear();
            sessionTokens.clear();
        }
    }

    static final RMIOperation<AuthToken> GET_ACCESS_TOKEN =
        RMIOperation.valueOf(AuthService.class, AuthToken.class, "getAccessToken", AuthToken.class);

    static final RMIOperation<byte[]> GET_SESSION_TOKEN =
        RMIOperation.valueOf(AuthService.class, byte[].class, "getSessionToken", AuthToken.class);

    private final RMIEndpoint serverAuth = RMIEndpoint.newBuilder()
        .withName("SimpleAuthServer ")
        .withSide(RMIEndpoint.Side.SERVER)
        .build();
    private int errorLoginCount;

    SimpleAuthServer(Object subject, AuthToken badClient, AuthToken... clients) {
        serviceImpl = new AuthServiceImpl(subject, badClient, clients);
        serverAuth.getServer().export(serviceImpl, AuthService.class);
        port = NTU.connectServer(serverAuth);
    }

    void close() {
        serverAuth.close();
        serviceImpl.close();
    }

    public int getErrorLoginCount() {
        return errorLoginCount;
    }

    public int getPort() {
        return port;
    }

    public String getAddress() {
        return NTU.localHost(getPort());
    }
}
