/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.impl;


import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.devexperts.logging.Logging;
import com.devexperts.rmi.RMIClient;
import com.devexperts.rmi.RMIRequest;
import com.devexperts.rmi.message.RMIRequestType;
import com.devexperts.rmi.task.*;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.TypedKey;

class RMIClientService extends ForwardService {

    @SuppressWarnings("rawtypes")
    private static final TypedKey<RMIRequest> REQUEST = new TypedKey<>();

    private final IndexedSet<RMIServiceId, RMIServiceDescriptor> descriptors =
        IndexedSet.create(RMIServiceDescriptor.INDEXER_BY_SERVICE_ID);
    private final List<RMIServiceDescriptorsListener> listeners = new CopyOnWriteArrayList<>();
    private final ServiceFilter filter;
    private final RMIClient client;

    RMIClientService(String serviceName, RMIClientImpl client) {
        super(serviceName, client.getPort(null));
        this.filter = ServiceFilter.valueOf(serviceName);
        this.client = client;
    }

    @Override
    public synchronized void addServiceDescriptorsListener(RMIServiceDescriptorsListener listener) {
        super.addServiceDescriptorsListener(listener);
        listeners.add(listener);
    }

    @Override
    public synchronized void removeServiceDescriptorsListener(RMIServiceDescriptorsListener listener) {
        super.removeServiceDescriptorsListener(listener);
        listeners.remove(listener);
    }

    @Override
    public void openChannel(RMITask<Object> task) {
        RMIRequest<?> request = client.getPort(task.getSubject()).createRequest(task.getRequestMessage());
        request.getChannel().addChannelHandler(new ForwardService("*", task.getChannel()));
        task.getChannel().addChannelHandler(new ForwardService("*", request.getChannel()));
        task.getTaskVariables().set(REQUEST, request);
    }

    @Override
    RMIRequest<?> createRequest(RMITask<?> task) {
        if (task.getRequestMessage().getRequestType() == RMIRequestType.ONE_WAY)
            return client.getPort(task.getSubject()).createRequest(task.getRequestMessage());
        return task.getTaskVariables().get(REQUEST);
    }

    // Must only use descriptors accepted by this service filter
    synchronized void updateDescriptors(List<RMIServiceDescriptor> descriptors) {
        this.descriptors.addAll(descriptors);
        for (RMIServiceDescriptorsListener listener : listeners)
            try {
                listener.descriptorsUpdated(descriptors);
            } catch (Throwable t) {
                Logging.getLogging(RMIClientService.class).error("Failed to update service descriptors", t);
            }
    }

    @Override
    public List<RMIServiceDescriptor> getDescriptors() {
        // Note: we must use concurrent iterator to get a snapshot of descriptors (that are being modified),
        // so we use toArray that gives a concurrent snapshot of IndexedSet
        return Arrays.asList((RMIServiceDescriptor[]) descriptors.toArray(new RMIServiceDescriptor[descriptors.size()]));
    }

    @Override
    public boolean isAvailable() {
        return !descriptors.isEmpty();
    }

    ServiceFilter getFilter() {
        return filter;
    }
}
