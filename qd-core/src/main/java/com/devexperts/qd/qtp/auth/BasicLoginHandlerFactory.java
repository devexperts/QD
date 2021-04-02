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
import com.devexperts.util.InvalidFormatException;


/**
 * Basic implementation for {@link QDLoginHandlerFactory}. This class used when all other implementations
 * {@link QDLoginHandlerFactory} returned {@code null} for the login string or if attribute login is missing, but
 * connection have login and password.
 *
 * This implementation created {@link QDLoginHandler}.
 */
public class BasicLoginHandlerFactory implements QDLoginHandlerFactory {
    public static final BasicLoginHandlerFactory INSTANCE = new BasicLoginHandlerFactory();

    @Override
    public QDLoginHandler createLoginHandler(String login, MessageAdapterConnectionFactory factory) {
        int i = login.indexOf(':');
        if (i < 0)
            throw new InvalidFormatException("Login must be set to <user>:<password> string or refer to the name of other login factory");
        if (!factory.getUser().isEmpty() || !factory.getPassword().isEmpty())
            throw new InvalidFormatException("Either set login=<user>:<password> string or use 'user' and 'password' configuration keys separately");
        return new BasicLoginHandler(login.substring(0, i), login.substring(i + 1));
    }
}
