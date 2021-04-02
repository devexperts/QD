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
package com.devexperts.rmi.task;

/**
 * Defines objects that can monitor the changes {@link RMIServiceDescriptor}.
 */
public interface RMIObservableServiceDescriptors {
    /**
     * Adds the specified {@link RMIServiceDescriptorsListener listener} to this object.
     * @param listener newly adding {@link RMIServiceDescriptorsListener}.
     */
    void addServiceDescriptorsListener(RMIServiceDescriptorsListener listener);

    /**
     * Removes the specified {@link RMIServiceDescriptorsListener listener} from this object
     * @param listener removing {@link RMIServiceDescriptorsListener}.
     */
    void removeServiceDescriptorsListener(RMIServiceDescriptorsListener listener);

    /**
     * Returns true if the implementation of service is available from current endpoint.
     * @return true if the implementation of service is available from current endpoint
     */
    public boolean isAvailable();
}
