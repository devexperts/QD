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
package com.devexperts.qd.qtp;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.util.InvalidFormatException;

/**
 * Factory class for {@link MessageConnector}.
 */
public interface MessageConnectorFactory {
    /**
     * Creates message connector with a given address.
     *
     * @param applicationConnectionFactory ProtocolFactory to use.
     * @param address         address to connect to.
     * @return created MessageConnector or <code>null</code> if this
     *         factory does not support the specified address.
     * @throws InvalidFormatException if connector supports the specified address, but there
     *                                are some errors in its specification.
     */
    public MessageConnector createMessageConnector(ApplicationConnectionFactory applicationConnectionFactory, String address)
        throws InvalidFormatException;

    /**
     * Returns the exact class of produced MessageConnectors.
     * It can be used to retrieve annotations and java-bean properties.
     *
     * @return the exact class of produced MessageConnectors.
     */
    public Class<? extends MessageConnector> getResultingClass();
}
