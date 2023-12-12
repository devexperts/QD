/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.monitoring;

import com.devexperts.logging.Logging;
import com.devexperts.util.SynchronizedIndexedSet;

class JmxConnectors {
    private static final Logging log = Logging.getLogging(JmxConnectors.class);

    private static final SynchronizedIndexedSet<Integer, JmxConnector> JMX_CONNECTORS =
        SynchronizedIndexedSet.createInt(JmxConnector::getPort);

    private JmxConnectors() {}

    static boolean isPortAvailable(Integer port) {
        return port != null && !JMX_CONNECTORS.containsKey(port);
    }

    static boolean addConnector(JmxConnector connector) {
        return JMX_CONNECTORS.putIfAbsentAndGet(connector) == connector;
    }

    static void removeConnector(JmxConnector connector) {
        JMX_CONNECTORS.remove(connector);
    }

    static void stopConnectors() {
        JmxConnector[] values = JMX_CONNECTORS.toArray(new JmxConnector[JMX_CONNECTORS.size()]);
        // take snapshot to stop them all
        for (JmxConnector connector : values) {
            try {
                connector.stop();
            } catch (Exception e) {
                log.error("Failed to stop JMX connector " + connector, e);
            }
        }
    }
}
