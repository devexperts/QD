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


import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.RMIServer;
import com.devexperts.rmi.impl.RMIConnectorInitializer;
import com.devexperts.rmi.impl.RMIEndpointImpl;

import java.io.Closeable;

public class OptionForward extends Option implements Closeable {

    private String services;
    private String address;
    private RMIEndpointImpl client;

    private static final String ARGUMENTS_NAME = "<service-names> <address>";


    public OptionForward() {
        super('F', "forward", "Route RMI requests coming from its downlink (agent)");
    }

    public String getServices() {
        return services;
    }

    @Override
    public int parse(int i, String[] args) throws OptionParseException {
        if (i >= args.length - 1)
            throw new OptionParseException(this + " must be followed by a string argument.");
        services = args[++i];
        if (!services.startsWith("(") && !services.endsWith(")"))
            services = "(" + services + ")";
        address = args[++i];
        return super.parse(i, args);
    }

    public void applyForwards(RMIServer server, QDEndpoint endpoint) {
        client = new RMIEndpointImpl(RMIEndpoint.Side.CLIENT, endpoint, null, null);
        server.export(client.getClient().getService(services));
        new RMIConnectorInitializer(client).createAndAddConnector(endpoint, address);
    }

    public void close() {
        client.close();
    }

    @Override
    public String toString() {
        return super.toString() + " " + ARGUMENTS_NAME;
    }
}
