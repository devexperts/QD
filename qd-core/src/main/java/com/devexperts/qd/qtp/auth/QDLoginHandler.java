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
package com.devexperts.qd.qtp.auth;

import com.devexperts.auth.AuthToken;
import com.dxfeed.promise.Promise;


/**
 * Framework for the authorization, which must receive and update accessToken for the client.
 */
public interface QDLoginHandler {

    /**
     * Starts or restarts login procedure by this login handler. This method shall not block and can be called asynchronously
     * and concurrently from multiple threads, e.g. it must include all the synchronization it needs.
     * This method is invoked if server reports that connection needs authorization.
     * Returns promise that will either return {@link AuthToken} or throw something exception.
     * If handler already has {@link AuthToken}, then this method returns the completed promise.
     * This promise is cancelled if the connection is closed for any reason.
     *
     * @param reason The text prompt to login. If current auth token was rejected by server for some reason,
     * then it will contain the reason.
     * @return promise that will either return {@link AuthToken} or throw something exception.
     */
    public Promise<AuthToken> login(String reason);

    /**
     * Returns current token or {@code null} if not authenticated yet. It is Ok if login handler already has a token even before its
     * login method was invoked.
     *
     * @return current token or {@code null} if not authenticated yet.
     */
    public AuthToken getAuthToken();
}
