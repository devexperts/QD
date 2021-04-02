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
package com.devexperts.qd.qtp.nio;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorFactory;
import com.devexperts.util.InvalidFormatException;

public class NioConnectorFactory implements MessageConnectorFactory {
    private static final String NIO_PREFIX = "nio::";

    public NioServerConnector createMessageConnector(ApplicationConnectionFactory applicationConnectionFactory, String address)
        throws InvalidFormatException
    {
        if (!address.startsWith(NIO_PREFIX))
            return null;
        int port;
        try {
            port = Integer.parseInt(address.substring(NIO_PREFIX.length()));
        } catch (NumberFormatException e) {
            throw new InvalidFormatException("Couldn't parse port number while creating NIO server connector");
        }
        return new NioServerConnector(applicationConnectionFactory, port);
    }

    public Class<? extends MessageConnector> getResultingClass() {
        return NioServerConnector.class;
    }
}
