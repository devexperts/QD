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
package com.devexperts.rmi.test.throughput;

import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.test.NTU;

class ServerSide {
    private final String address;
    private final LoggingThread logger;
    private final RMIEndpoint[] endpoints;
    private final ITestingService testingService;

    ServerSide(String address, int endpointsNumber, int loggingInterval, int delay) {
        this.address = address;
        this.endpoints = new RMIEndpoint[endpointsNumber];
        for (int i = 0; i < endpoints.length; i++) {
            endpoints[i] = RMIEndpoint.createEndpoint();
        }
        this.testingService = new TestingService(delay);
        if (loggingInterval > 0) {
            this.logger = new LoggingThread(testingService, loggingInterval, null);
        } else {
            this.logger = null;
        }
    }

    void start() {
        for (RMIEndpoint endpoint : endpoints) {
            NTU.connect(endpoint, address);
            endpoint.export(testingService, ITestingService.class);
        }
        if (logger != null) {
            logger.start();
        }
    }

    void close() {
        for (RMIEndpoint endpoint : endpoints) {
            endpoint.close();
        }
    }
}
