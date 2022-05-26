/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.impl;

import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.rmi.task.RMIServiceId;
import com.devexperts.util.IndexedSet;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
class ServerDescriptorsManager {

    private final RMIConnection connection;
    private volatile ServiceFilter services;
    private final IndexedSet<RMIServiceId, RMIServiceDescriptor> descriptors =
        IndexedSet.create(RMIServiceDescriptor.INDEXER_BY_SERVICE_ID);

    ServerDescriptorsManager(RMIConnection connection) {
        this.connection = connection;
        services = ServiceFilter.NOTHING;
    }

    void setServicesOnDescribeProtocolAndSendAllDescriptors(ServiceFilter services) {
        synchronized (this) {
            this.services = services;
        }
        if (connection.side.hasServer())
            connection.endpoint.getServer().sendAllDescriptorsToConnection(connection);
    }

    synchronized void addServiceDescriptors(List<RMIServiceDescriptor> descriptors) {
        if (!connection.adFilter.isSendAdvertisement()) {
            return;
        }
        boolean changed = false;
        for (RMIServiceDescriptor descriptor : descriptors) {
            if (!services.accept(descriptor.getServiceName()))
                continue;
            if (descriptor.getIntermediateNodes().contains(connection.getRemoteEndpointId())) {
                this.descriptors.add(RMIServiceDescriptor.createUnavailableDescriptor(descriptor.getServiceId(),
                    descriptor.getProperties()));
                changed = true;
                continue;
            }
            this.descriptors.add(descriptor);
            changed = true;
        }
        if (changed)
            connection.messageAdapter.rmiMessageAvailable(RMIQueueType.ADVERTISE);
    }

    synchronized List<RMIServiceDescriptor> pollServiceDescriptors() {
        if (descriptors.isEmpty())
            return null;
        List<RMIServiceDescriptor> result = new ArrayList<>(descriptors.size());
        result.addAll(descriptors);
        descriptors.clear();
        return result;
    }

    synchronized int descriptorsSize() {
        return descriptors.size();
    }

    synchronized boolean hasDescriptor() {
        return !descriptors.isEmpty();
    }
}
