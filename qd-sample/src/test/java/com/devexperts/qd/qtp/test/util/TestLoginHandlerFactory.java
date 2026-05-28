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
import com.devexperts.qd.qtp.auth.QDLoginHandler;
import com.devexperts.qd.qtp.auth.QDLoginHandlerFactory;
import com.devexperts.services.ServiceProvider;
import com.devexperts.util.InvalidFormatException;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Test-only {@link QDLoginHandlerFactory}. Activated by address attribute {@code login=test:<id>};
 * returns the handler the test {@link #register registered} under that id.
 */
@ServiceProvider
public class TestLoginHandlerFactory implements QDLoginHandlerFactory {

    public static final String PREFIX = "test";

    private static final ConcurrentHashMap<String, QDLoginHandler> REGISTRY = new ConcurrentHashMap<>();

    public static void register(String testId, QDLoginHandler handler) {
        REGISTRY.put(testId, handler);
    }

    public static void unregister(String testId) {
        REGISTRY.remove(testId);
    }

    @Override
    public QDLoginHandler createLoginHandler(String login, MessageAdapterConnectionFactory factory)
        throws InvalidFormatException
    {
        if (login == null || !login.startsWith(PREFIX + ":"))
            return null;
        String testId = login.substring(PREFIX.length() + 1);
        QDLoginHandler handler = REGISTRY.get(testId);
        if (handler == null)
            throw new InvalidFormatException("No handler registered for testId: " + testId);
        return handler;
    }
}
