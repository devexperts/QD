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
 * Factory for {@link QDLoginHandler}. It must be used for login-side authorization.
 */
@Service
public interface QDLoginHandlerFactory {
    /**
     * Creates login handler. This method is invoked when configuration string is parsed.
     * For example, for an address {@code <host>:<port>[login=<params>]} this method
     * will be invoked with login parameters of {@code <params>}. Params shall include
     * some scheme prefix that is recognized by this login handler. For example,
     * "MDDLoginHandlerFactory" will only create login handler for param string
     * "mdd" or param strings starting with "mdd:". This way, multiple login handler
     * factories can peacefully coexist and be used in a single JVM.
     *
     * <p>Note, that this method can read other config params from the {@param factory}.
     * For example, the implementation may understand an address like
     * {@code <host>:<port>[login=mdd,user=xxx,password=yyy]}, reading user and password
     * from the config, instead of asking it on the command line.
     *
     * @param login Additional parameters for configuration of this login handler.
     * @param factory Parent message adapter factory.
     * @return Login handler or {@code null} if the specified login parameters are not supported.
     * @throws InvalidFormatException if {@code login} parameters start with an appropriate prefix
     *         but have invalid syntax.
     */
    public QDLoginHandler createLoginHandler(String login, MessageAdapterConnectionFactory factory)
        throws InvalidFormatException;
}
