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
package com.devexperts.qd.qtp.file;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorFactory;
import com.devexperts.util.InvalidFormatException;

public class TapeConnectorFactory implements MessageConnectorFactory {
    public static final String TAPE_PREFIX = "tape:";

    @Override
    public MessageConnector createMessageConnector(ApplicationConnectionFactory applicationConnectionFactory,
        String address) throws InvalidFormatException
    {
        if (address.startsWith(TAPE_PREFIX))
            address = address.substring(TAPE_PREFIX.length());
        return address.indexOf(':') < 0 ? new TapeConnector(applicationConnectionFactory, address) : null;
    }

    @Override
    public Class<? extends MessageConnector> getResultingClass() {
        return TapeConnector.class;
    }
}
