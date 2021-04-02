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

import com.devexperts.logging.Logging;
import com.devexperts.rmi.RMIRequestState;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * A class that manages monitoring thread execution.
 */
class RMITimeoutRequestMonitoringThread implements Runnable {
    private static final Thread.UncaughtExceptionHandler UNCAUGHT_EXCEPTION_HANDLER =
        (t, e) -> Logging.getLogging(RMITimeoutRequestMonitoringThread.class).error("Uncaught exception", e);

    private final WeakReference<RMIEndpointImpl> endpointReference;
    private volatile Thread thread;

    RMITimeoutRequestMonitoringThread(RMIEndpointImpl endpoint) {
        endpointReference = new WeakReference<>(endpoint);
    }

    void startIfNotAlive() {
        Thread t = thread;
        if (t != null && t.isAlive())
            return;
        // do a proper double-check if thread is alive
        synchronized (this) {
            t = thread;
            if (t != null && t.isAlive())
                return;
            RMIEndpointImpl endpoint = endpointReference.get();
            if (endpoint == null)
                return; // just in case check... endpoint was somehow lost - don't start
            thread = new Thread(this, endpoint.getName() + "-" + RMITimeoutRequestMonitoringThread.class.getSimpleName());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler(UNCAUGHT_EXCEPTION_HANDLER);
            thread.start();
        }
    }

    synchronized void stop() {
        Thread t = thread;
        thread = null;
        LockSupport.unpark(t);
    }

    void wakeUp() {
        LockSupport.unpark(thread); // thread==null means "no operation" for unpark
    }

    @Override
    public void run() {
        RMIRequestImpl<?>[] requests = new RMIRequestImpl<?>[0];
        while (true) {
            RMIEndpointImpl endpoint = endpointReference.get();
            if (endpoint == null) // endpoint was lost without proper shutdown - stop ourselves
                break;
            if (endpoint.isClosed())
                break; // endpoint was closed -- stop
            if (thread != null && thread != Thread.currentThread()) // new thread was started - stop ourselves
                break;
            long requestSendingTimeout = endpoint.getClient().getRequestSendingTimeout();
            long requestRunningTimeout = endpoint.getClient().getRequestRunningTimeout();
            AtomicBoolean hasActiveRequests = new AtomicBoolean();
            long currentTime = System.currentTimeMillis();

            for (Iterator<RMIConnection> it = endpoint.concurrentConnectionsIterator(); it.hasNext();) {
                RMIConnection connection = it.next();
                requests = connection.requestsManager.getSentRequests(requests);
                for (int j = 0; j < requests.length; j++) {
                    RMIRequestImpl<?> request = requests[j];
                    if (request == null || request.isNestedRequest())
                        break;
                    requests[j] = null;
                    if (currentTime - request.getRunningStartTime() > requestRunningTimeout)
                        request.abortOnTimeout(RMIRequestState.SENT);
                    else
                        hasActiveRequests.set(true);
                }
                requests = connection.requestsManager.getOutgoingRequests(requests);
                for (int j = 0; j < requests.length; j++) {
                    RMIRequestImpl<?> request = requests[j];
                    if (request == null || request.isNestedRequest())
                        break;
                    requests[j] = null;
                    if (currentTime - request.getSendTime() > requestSendingTimeout) {
                        request.abortOnTimeout(RMIRequestState.WAITING_TO_SEND);
                        connection.requestsManager.removeOutgoingRequest(request);
                    } else
                        hasActiveRequests.set(true);
                }
            }
            endpoint.getClient().forEachPendingRequest(request -> {
                if (currentTime - request.getSendTime() > requestSendingTimeout) {
                    if (request.removeFromSendingQueues())
                        request.abortOnTimeout(RMIRequestState.WAITING_TO_SEND);
                    // if request.cancelAndWait() is called here, then we'll overwrite its CANCELLING status with failure
                } else {
                    hasActiveRequests.set(true);
                }

            });
            if (thread == null && !hasActiveRequests.get()) // thread was stopped and no more active requests - stop ourselves
                break;
            long sleepTime = Math.max(1000, Math.min(requestSendingTimeout, requestRunningTimeout) / 2);
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(sleepTime));
        }
    }
}
