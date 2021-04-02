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

import com.devexperts.rmi.task.RMIServiceDescriptor;
import com.devexperts.rmi.task.RMIServiceId;
import com.devexperts.util.IndexedSet;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.concurrent.GuardedBy;

public class ClientDescriptorsManager {

    @GuardedBy("RMIClientImpl.services")
    private final IndexedSet<RMIServiceId, RMIServiceDescriptor> serviceDescriptors =
        IndexedSet.create(RMIServiceDescriptor.INDEXER_BY_SERVICE_ID);

    @GuardedBy("RMIClientImpl.services")
    void updateDescriptors(List<RMIServiceDescriptor> descriptors) {
        for (RMIServiceDescriptor descriptor : descriptors) {
            if (descriptor.isAvailable())
                serviceDescriptors.add(descriptor);
            else
                serviceDescriptors.removeValue(descriptor);
        }
    }

    @GuardedBy("RMIClientImpl.services")
    List<RMIServiceDescriptor> clearDescriptors() {
        List<RMIServiceDescriptor> result = new ArrayList<>(serviceDescriptors);
        serviceDescriptors.clear();
        return result;
    }
}
