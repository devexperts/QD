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

class LoggingThread extends Thread {
    private final ITestingService testingService;
    private final int loggingInterval;
    private final AdditionalInfoProvider additionalInfoProvider;
    private int operationsCount;

    LoggingThread(ITestingService testingService, int loggingInterval, AdditionalInfoProvider additionalInfoProvider) {
        this.testingService = testingService;
        this.loggingInterval = loggingInterval;
        this.additionalInfoProvider = additionalInfoProvider;
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(1000 * loggingInterval);
            } catch (InterruptedException e) {
                interrupt();
                break;
            }
            int newOperationCount = testingService.getOperationsCount();
            int speed = (newOperationCount - operationsCount) / loggingInterval;
            System.out.println("Server executes " + speed + " tasks per second");
            System.out.println("Total number of executed tasks: " + newOperationCount);
            if (additionalInfoProvider != null) {
                System.out.println(additionalInfoProvider.getAdditionalInfo());
            }
            operationsCount = newOperationCount;
        }
    }
}
