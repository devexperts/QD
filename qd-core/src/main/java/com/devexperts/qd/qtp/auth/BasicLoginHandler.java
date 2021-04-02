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
 * Basic implementation for {@link QDAuthRealm}. This class parses the login string as "user:password".
 */
public class BasicLoginHandler implements QDLoginHandler {

    private final AuthToken authToken;

    public BasicLoginHandler(String user, String password) {
        this.authToken = AuthToken.createBasicToken(user, password);
    }

    @Override
    public Promise<AuthToken> login(String reason) {
        return Promise.completed(authToken);
    }

    @Override
    public AuthToken getAuthToken() {
        return authToken;
    }
}
