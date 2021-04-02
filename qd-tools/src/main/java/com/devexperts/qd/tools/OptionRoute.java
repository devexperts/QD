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
package com.devexperts.qd.tools;

import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIServer;
import com.devexperts.rmi.impl.RMIConnectorInitializer;
import com.devexperts.rmi.impl.RMIEndpointImpl;

import java.io.Closeable;

public class OptionRoute extends Option implements Closeable {
    private RMIEndpointImpl client;

    public OptionRoute() {
        super('R', "route", "Route RMI requests coming from its downlink (agent)");
    }

    public void applyRouting(RMIServer server, QDEndpoint endpoint, MessageAdapter.AbstractFactory distributorFactory,
        String address)
    {
        client = new RMIEndpointImpl(RMIEndpoint.Side.CLIENT, endpoint, distributorFactory, null);
        server.export(client.getClient().getService("*"));
        QDEndpoint.ConnectorInitializer initializer = new RMIConnectorInitializer(client);
        initializer.createAndAddConnector(endpoint, address);
    }

    public void close() {
        client.close();
    }
}
