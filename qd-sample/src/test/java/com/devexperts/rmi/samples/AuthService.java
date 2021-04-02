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
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIOperation;
import com.devexperts.rmi.task.RMITask;
import com.devexperts.util.Base64;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public interface AuthService {

    public static final RMIOperation<AuthToken> GET_ACCESS_TOKEN =
        RMIOperation.valueOf(AuthService.class, AuthToken.class, "getAccessToken", AuthToken.class);

    public static final RMIOperation<byte[]> GET_SESSION_TOKEN =
        RMIOperation.valueOf(AuthService.class, byte[].class, "getSessionToken", AuthToken.class);

    AuthToken getAccessToken(AuthToken token);

    byte[] getSessionToken(AuthToken token);

    public static class AuthServiceImpl implements AuthService {

        public static void main(String... args) throws InterruptedException {
            String port = args[0];
            String subject = args[1];
            AuthToken[] clients  = new AuthToken[args.length - 2];
            for (int i = 2; i < args.length; i++) {
                clients[i - 2] = AuthToken.createBasicToken(args[i]);
            }
            RMIEndpoint server = RMIEndpoint.newBuilder()
                .withName("Auth Server")
                .withSide(RMIEndpoint.Side.SERVER)
                .build();
            server.getServer().export(new AuthServiceImpl(subject, clients), AuthService.class);
            server.connect(port);

            Thread.sleep(1000 * 1000);
        }


        private static final int TOKEN_LENGTH = 16;
        private static final int UID_SIZE = 16;

        private final Map<AuthToken, AuthToken> accessTokens = Collections.synchronizedMap(new HashMap<>());
        private final Map<AuthToken, byte[]> sessionTokens = Collections.synchronizedMap(new HashMap<>());
        private final Random rnd = new Random(256);
        private final Object subject;

        AuthServiceImpl(Object subject, AuthToken... clients) {
            this.subject = subject;
            for (AuthToken client : clients)
                accessTokens.put(client, null);
        }

        @Override
        public AuthToken getAccessToken(AuthToken userPassword) {
            if (!accessTokens.containsKey(userPassword))
                throw new SecurityException("user or password is wrong");
            AuthToken token = generatedToken();
            accessTokens.putIfAbsent(userPassword, token);
            byte[] session = generatedSessionByte(token);
            sessionTokens.putIfAbsent(token, session);
            System.out.println("getAccessToken(" + userPassword + ")=" + token);
            return token;
        }

        @Override
        public byte[] getSessionToken(AuthToken token) {
            if (!RMITask.current().getSubject().getObject().equals(subject))
                throw new SecurityException();
            byte[] session = sessionTokens.get(token);
            System.out.println("getSessionToken(" + token + ")=" + Arrays.toString(session));
            if (session == null)
                throw new SecurityException("Access token is wrong");
            return session;
        }

        private byte[] generatedSessionByte(AuthToken token) {
            byte[] session;
            session = new byte[UID_SIZE];
            ByteBuffer buf = ByteBuffer.wrap(session);
            UUID uuid = UUID.nameUUIDFromBytes(Base64.DEFAULT.decode(token.getValue()));
            buf.putLong(uuid.getMostSignificantBits());
            buf.putLong(uuid.getLeastSignificantBits());
            System.out.println("generate session token:" + Arrays.toString(session));
            return session;
        }

        private AuthToken generatedToken() {
            byte[] bytes = new byte[TOKEN_LENGTH];
            rnd.nextBytes(bytes);
            AuthToken token = AuthToken.createBearerToken(Base64.DEFAULT.encode(bytes));
            return token;
        }
    }
}
