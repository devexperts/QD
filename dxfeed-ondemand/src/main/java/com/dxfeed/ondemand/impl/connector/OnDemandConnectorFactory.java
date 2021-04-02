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
package com.dxfeed.ondemand.impl.connector;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorFactory;
import com.devexperts.util.InvalidFormatException;

public class OnDemandConnectorFactory implements MessageConnectorFactory {
    public static final String ON_DEMAND_PREFIX = "ondemand:";

    @Override
    public MessageConnector createMessageConnector(ApplicationConnectionFactory factory, String address)
        throws InvalidFormatException
    {
        if (!address.startsWith(ON_DEMAND_PREFIX))
            return null;
        return new OnDemandConnector(factory, address.substring(ON_DEMAND_PREFIX.length()));
    }

    @Override
    public Class<? extends MessageConnector> getResultingClass() {
        return OnDemandConnector.class;
    }
}
