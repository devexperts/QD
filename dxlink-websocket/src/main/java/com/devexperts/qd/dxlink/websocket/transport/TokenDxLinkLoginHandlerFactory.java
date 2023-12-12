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
package com.devexperts.qd.dxlink.websocket.transport;

import com.devexperts.auth.AuthToken;
import com.devexperts.logging.Logging;
import com.devexperts.qd.dxlink.websocket.application.DxLinkWebSocketApplicationConnectionFactory;
import com.devexperts.qd.qtp.auth.QDLoginHandler;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.InvalidFormatException;
import com.dxfeed.promise.Promise;

@ServiceProvider
public class TokenDxLinkLoginHandlerFactory implements DxLinkLoginHandlerFactory {
    public static final TokenDxLinkLoginHandlerFactory INSTANCE = new TokenDxLinkLoginHandlerFactory();
    public static final String DXLINK_AUTHORIZATION_SCHEME = "dxlink";

    private static final Logging log = Logging.getLogging(TokenDxLinkLoginHandlerFactory.class);

    @Override
    public QDLoginHandler createLoginHandler(String login, DxLinkWebSocketApplicationConnectionFactory factory)
        throws InvalidFormatException
    {
        if (!login.startsWith(DXLINK_AUTHORIZATION_SCHEME + ":"))
            return null;

        boolean hasToken = login.length() > (DXLINK_AUTHORIZATION_SCHEME + ":").length();
        if (!hasToken) {
            log.error("Auth scheme '" + DXLINK_AUTHORIZATION_SCHEME + "' login must be specified as: " +
                DXLINK_AUTHORIZATION_SCHEME + "[:<token>]");
            return null;
        }

        String token = login.substring(DXLINK_AUTHORIZATION_SCHEME.length() + 1);
        log.info("Using auth scheme '" + DXLINK_AUTHORIZATION_SCHEME + "' with token: " + token);
        return new AutherLoginHandler(token);
    }

    protected static class AutherLoginHandler implements QDLoginHandler {
        private final AuthToken token;

        public AutherLoginHandler(String token) {
            this.token = AuthToken.createCustomToken(DXLINK_AUTHORIZATION_SCHEME, token);
        }

        @Override
        public Promise<AuthToken> login(String reason) {
            if ("UNAUTHORIZED".equals(reason)) {
                return Promise.completed(token);
            } else {
                log.error("Server rejected token: " + reason);
                return Promise.failed(new SecurityException(reason));
            }
        }

        @Override
        public AuthToken getAuthToken() {
            return null; // We must first set up the channel and only then decide whether to send the token.
        }
    }
}
