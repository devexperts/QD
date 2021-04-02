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

import com.devexperts.qd.qtp.MessageAdapterConnectionFactory;
import com.devexperts.services.Service;
import com.devexperts.util.InvalidFormatException;

/**
 * Factory for {@link QDAuthRealm}. It must be used for server-side authentication.
 */
@Service
public interface QDAuthRealmFactory {
    /**
     * Creates {@link QDAuthRealm} on this auth string.
     * If this auth string unknown to this factory, it must returns {@code null}.
     * @param auth auth string for attribute {@code auth=<params>}
     * @param factory factory for connection.
     * @return {@link QDAuthRealm} for this auth string or {@code null} if this auth string unknown to this factory.
     * @throws InvalidFormatException if {@code auth} parameters start with an appropriate prefix but have invalid syntax.
     */
    public QDAuthRealm createAuthRealm(String auth, MessageAdapterConnectionFactory factory)
        throws InvalidFormatException;
}
