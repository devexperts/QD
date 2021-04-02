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

import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.RMIRequestState;

class ClientSideStats {
    private int executionTime;
    private int succeededCount;
    private int failedCount;
    private int sentCount;

    synchronized void requestCompleted(RMIRequest<?> request) {
        executionTime += request.getCompletionTime() - request.getSendTime();
        if (request.getState() == RMIRequestState.SUCCEEDED) {
            succeededCount++;
        } else {
            failedCount++;
        }
    }

    synchronized void requestSent() {
        sentCount++;
    }

    public int getSucceededCount() {
        return succeededCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public int getSentCount() {
        return sentCount;
    }

    int getTotalExecutionTime() {
        return executionTime;
    }

    public void reset() {
        executionTime = 0;
        sentCount = 0;
        succeededCount = 0;
        failedCount = 0;
    }
}
