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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JmxConnectorsTest {

    @Test
    public void testJmxConnectorProperlyRemoved() {
        int port = 1;
        JmxConnector connector = new JmxConnector(port, "name-1") {};
        assertTrue(JmxConnectors.isPortAvailable(port));
        assertTrue(JmxConnectors.addConnector(connector));
        assertFalse(JmxConnectors.isPortAvailable(port));
        JmxConnectors.removeConnector(connector);
        assertTrue(JmxConnectors.isPortAvailable(port));
        assertTrue(JmxConnectors.addConnector(connector));
        JmxConnectors.removeConnector(connector);
        assertTrue(JmxConnectors.isPortAvailable(port));
    }

    @Test
    public void testJmxConnectorProperlyStopped() {
        int port1 = 1;
        JmxConnector connector1 = new JmxConnector(port1, "name-1") {};
        assertTrue(JmxConnectors.addConnector(connector1));
        int port2 = 2;
        JmxConnector connector2 = new JmxConnector(port2, "name-2") {};
        assertTrue(JmxConnectors.addConnector(connector2));
        assertFalse(JmxConnectors.isPortAvailable(port1));
        assertFalse(JmxConnectors.isPortAvailable(port2));
        JmxConnectors.stopConnectors();
        assertTrue(JmxConnectors.isPortAvailable(port1));
        assertTrue(JmxConnectors.isPortAvailable(port2));
    }
}
