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

import com.devexperts.auth.AuthSession;
import com.devexperts.auth.AuthToken;
import com.devexperts.util.TypedMap;
import com.dxfeed.promise.Promise;


/**
 * This is server-side authentication provider that resolves {@link AuthSession}.
 */
public interface QDAuthRealm {

    /**
     * Starts or restarts authentication procedure by this QDAuthRealm.
     * This method shall not block and can be called asynchronously and concurrently from multiple threads,
     * e.g. it must include all the synchronization it needs.
     * Returns promise that will either return {@link AuthSession} for this client {@link AuthToken} or throw something exception.
     * This promise is cancelled if the connection is closed for any reason.
     * This method is invoked if client sent auth token.
     *
     * @param authToken token from client
     * @param connectionVariables the connection variables
     * @return promise that will either return {@link AuthSession} or throw something exception.
     */
    public Promise<AuthSession> authenticate(AuthToken authToken, TypedMap connectionVariables);

    /**
     * Returns introductory information for the client about the authentication procedure.
     * This message is sent to the other side of the connection.
     * @return introductory information for the client about the authentication procedure.
     */
    public String getAuthenticationInfo();
}
