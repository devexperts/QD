/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.test.util;

import com.devexperts.qd.qtp.MessageAdapterConnectionFactory;
import com.devexperts.qd.qtp.auth.QDAuthRealm;
import com.devexperts.qd.qtp.auth.QDAuthRealmFactory;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.InvalidFormatException;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only {@link QDAuthRealmFactory}. Activated by address attribute {@code auth=test:<id>};
 * returns the realm the test {@link #register registered} under that id.
 */
@ServiceProvider
public class TestAuthRealmFactory implements QDAuthRealmFactory {

    public static final String PREFIX = "test";

    private static final ConcurrentHashMap<String, QDAuthRealm> REGISTRY = new ConcurrentHashMap<>();

    public static void register(String testId, QDAuthRealm realm) {
        REGISTRY.put(testId, realm);
    }

    public static void unregister(String testId) {
        REGISTRY.remove(testId);
    }

    @Override
    public QDAuthRealm createAuthRealm(String auth, MessageAdapterConnectionFactory factory)
        throws InvalidFormatException
    {
        if (auth == null || !auth.startsWith(PREFIX + ":"))
            return null;
        String testId = auth.substring(PREFIX.length() + 1);
        QDAuthRealm realm = REGISTRY.get(testId);
        if (realm == null)
            throw new InvalidFormatException("No realm registered for testId: " + testId);
        return realm;
    }
}
