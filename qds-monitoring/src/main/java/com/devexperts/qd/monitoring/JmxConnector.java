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
package com.devexperts.qd.monitoring;

import com.devexperts.management.Management;

import java.io.IOException;

abstract class JmxConnector {
    private final int port;
    private final String name;
    private Management.Registration registration;

    JmxConnector(int port, String name) {
        this.port = port;
        this.name = name;
    }

    public int getPort() {
        return port;
    }

    public String getName() {
        return name;
    }

    public void setRegistration(Management.Registration registration) {
        this.registration = registration;
    }

    public void stop() throws IOException {
        if (registration != null)
            registration.unregister();
        JmxConnectors.removeConnector(this);
    }

    @Override
    public String toString() {
        return "port=" + port + ", name=" + name;
    }
}
