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
package com.devexperts.qd.monitoring;

/**
 * Management interface for {@link MonitoringEndpoint}.
 *
 * @dgen.annotate method {}
 */
public interface MonitoringEndpointMXBean {

    /**
     * Flag indicating whether to use extended stats logging for striped connectors.
     * @return {@code true} if stats will be logged per stripe, {@code false} otherwise.
     */
    public boolean isLogStripedConnectors();

    public void setLogStripedConnectors(boolean flag);
}
