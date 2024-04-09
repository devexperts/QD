/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.impl;

import com.devexperts.rmi.RMIExceptionType;
import com.devexperts.rmi.task.RMIServiceDescriptor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.PriorityQueue;
import javax.annotation.concurrent.ThreadSafe;

/**
 * Outgoing requests.
 * This class is thread-safe.
 */
@ThreadSafe
class OutgoingRequests {

    private final ServiceFilter filter;
    private final PriorityQueue<RMIRequestImpl<?>> outgoingRequests =
        new PriorityQueue<>(11, RMIRequestImpl.REQUEST_COMPARATOR_BY_SENDING_TIME);

    private volatile boolean closed;

    OutgoingRequests(ServiceFilter filter) {
        this.filter = filter;
    }

    synchronized boolean add(RMIRequestImpl<?> request) {
        if (closed)
            return false;
        return outgoingRequests.add(request);
    }

    // Use descriptors == null to get everything
    synchronized List<RMIRequestImpl<?>> getByDescriptorsAndRemove(List<RMIServiceDescriptor> descriptors) {
        if (descriptors == null) {
            List<RMIRequestImpl<?>> result = new ArrayList<>(outgoingRequests);
            outgoingRequests.clear();
            return result;
        }
        List<RMIRequestImpl<?>> result = new ArrayList<>();
        for (Iterator<RMIRequestImpl<?>> it = outgoingRequests.iterator(); it.hasNext(); ) {
            RMIRequestImpl<?> request = it.next();
            boolean needRemove = false;
            for (RMIServiceDescriptor descriptor : descriptors) {
                if (!filter.accept(descriptor.getServiceName()))
                    continue;
                if (request.getOperation().getServiceName().equals(descriptor.getServiceName())) {
                    needRemove = true;
                    result.add(request);
                }
            }
            if (needRemove)
                it.remove();
        }
        return result;
    }

    synchronized RMIRequestImpl<?> poll() {
        return outgoingRequests.poll();
    }

    synchronized boolean remove(RMIRequestImpl<?> request) {
        return outgoingRequests.remove(request);
    }

    synchronized int size() {
        return outgoingRequests.size();
    }

    synchronized boolean isEmpty() {
        return outgoingRequests.isEmpty();
    }

    synchronized RMIRequestImpl<?>[] getRequests(RMIRequestImpl<?>[] requests) {
        return outgoingRequests.toArray(requests);
    }

    void close() {
        closed = true;
        // Limit synchronized range to honor lock hierarchy with requestLock
        RMIRequestImpl<?>[] requests = getRequests(new RMIRequestImpl[0]);
        for (RMIRequestImpl<?> request : requests) {
            request.setFailedState(RMIExceptionType.DISCONNECTION, null);
        }
    }
}
