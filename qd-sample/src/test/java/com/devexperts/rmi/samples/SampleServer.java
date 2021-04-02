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
package com.devexperts.rmi.samples;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.sample.Sample;
import com.devexperts.qd.sample.SampleGeneratorThread;
import com.devexperts.qd.sample.SampleScheme;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.impl.RMIEndpointImpl;

import java.util.EnumSet;

public class SampleServer {

    public static void main(String[] args) {
        initServer(args.length <= 0 ? ":5555" : args[0], 1235);
    }

    /**
     * Creates server at specified address.
     * @param address address to start the server at.
     * @param jmxHtmlPort HTML JMX port.
     */
    private static void initServer(String address, int jmxHtmlPort) {
        DataScheme scheme = SampleScheme.getInstance();
        QDEndpoint endpoint = QDEndpoint.newBuilder()
            .withName("server")
            .withScheme(scheme)
            .withContracts(EnumSet.of(QDContract.TICKER, QDContract.STREAM, QDContract.HISTORY))
            .withProperties(Sample.getMonitoringProps(jmxHtmlPort))
            .build();
        endpoint.getStream().setEnableWildcards(true);
        AgentAdapter.Factory factory = new AgentAdapter.Factory(endpoint, null);
        new RMIEndpointImpl(RMIEndpoint.Side.SERVER, endpoint, factory, null);
        endpoint.initializeConnectorsForAddress(address);
        endpoint.startConnectors();
        Thread generator = new SampleGeneratorThread(endpoint);
        generator.start();
    }
}
