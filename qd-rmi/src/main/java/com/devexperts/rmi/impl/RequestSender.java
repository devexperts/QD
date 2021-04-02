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
package com.devexperts.rmi.impl;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;

abstract class RequestSender {
    private final AtomicLong numerator = new AtomicLong(1);

    long createRequestId() {
        return numerator.getAndIncrement();
    }

    void startTimeoutRequestMonitoringThread() {}

    abstract RMIEndpointImpl getEndpoint();

    abstract Executor getExecutor();

    abstract void addOutgoingRequest(RMIRequestImpl<?> request);

    // returns true if removed from any queue
    abstract boolean dropPendingRequest(RMIRequestImpl<?> request);
}
