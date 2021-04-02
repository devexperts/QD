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

public class TestingService implements ITestingService {
    private final Object lock = new Object();

    private final int delay;

    private int operationsCount;

    public TestingService(int delay) {
        this.delay = delay;
    }

    @Override
    public byte[] ping(byte[] data) {
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (lock) {
            operationsCount++;
        }
        return data;
    }

    @Override
    public int getOperationsCount() {
        synchronized (lock) {
            return operationsCount;
        }
    }
}
