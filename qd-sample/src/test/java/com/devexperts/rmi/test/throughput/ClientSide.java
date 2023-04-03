/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.test.throughput;

import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.test.NTU;

class ClientSide {
    private final String address;
    private final LoggingThread logger;
    private final RMIEndpoint[] endpoints;
    private final RequestingThread[] threads;

    ClientSide(String address, int endpointsNumber, final int loggingInterval, int parameterSize, final boolean oneWay) {
        this.address = address;
        this.endpoints = new RMIEndpoint[endpointsNumber];
        this.threads = new RequestingThread[endpointsNumber];
        final ClientSideStats stats = new ClientSideStats();
        for (int i = 0; i < endpoints.length; i++) {
            endpoints[i] = RMIEndpoint.createEndpoint();
            threads[i] = new RequestingThread(endpoints[i], parameterSize, oneWay, stats);
        }
        ITestingService testingService = endpoints[0].getProxy(ITestingService.class);
        if (loggingInterval > 0) {
            this.logger = new LoggingThread(testingService, loggingInterval,
                new AdditionalInfoProvider() {
                    private int totalSentRequestsCount = 0;
                    private int totalSucceededRequestsCount = 0;
                    private int totalFailedRequestsCount = 0;

                    @Override
                    public String getAdditionalInfo() {
                        StringBuilder sb = new StringBuilder();
                        int sendingRequestNumber = 0;
                        for (RMIEndpoint endpoint : endpoints) {
                            sendingRequestNumber += endpoint.getSendingRequestsQueueLength();
                        }

                        int sentCount;
                        int succeededCount;
                        int failedCount;
                        int executionTime = 0;
                        synchronized (stats) {
                            sentCount = stats.getSentCount();
                            succeededCount = stats.getSucceededCount();
                            failedCount = stats.getFailedCount();
                            if (succeededCount + failedCount > 0)
                                executionTime = stats.getTotalExecutionTime() / (succeededCount + failedCount);
                            stats.reset();
                        }
                        totalSentRequestsCount += sentCount;
                        totalSucceededRequestsCount += succeededCount;
                        totalFailedRequestsCount += failedCount;
                        sb.append("Number of requests pending for sending: ").append(sendingRequestNumber)
                            .append("\nClient sends ").append(sentCount / loggingInterval)
                            .append(" requests per second\n")
                            .append("Total number of sent requests: ").append(totalSentRequestsCount).append('\n');
                        if (!oneWay) {
                            sb.append("Total number of succeeded requests: ").append(totalSucceededRequestsCount)
                                .append("\nTotal number of failed requests: ").append(totalFailedRequestsCount)
                                .append("\nAverage request execution time: ").append(executionTime).append(" ms\n");
                        }
                        return sb.toString();
                    }
                }
            );
        } else {
            this.logger = null;
        }
    }

    void start() {
        int n = endpoints.length;
        for (int i = 0; i < n; i++) {
            NTU.connect(endpoints[i], address);
            threads[i].start();
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
