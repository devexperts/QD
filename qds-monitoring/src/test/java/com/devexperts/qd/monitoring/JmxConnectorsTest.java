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
package com.devexperts.qd.monitoring;

import junit.framework.TestCase;
import org.junit.Assert;

public class JmxConnectorsTest extends TestCase {

    public void testJmxConnectorProperlyRemoved() {
        int port = 1;
        JmxConnector connector = new JmxConnector(port, "name-1") {};
        Assert.assertTrue(JmxConnectors.isPortAvailable(port));
        Assert.assertTrue(JmxConnectors.addConnector(connector));
        Assert.assertFalse(JmxConnectors.isPortAvailable(port));
        JmxConnectors.removeConnector(connector);
        Assert.assertTrue(JmxConnectors.isPortAvailable(port));
        Assert.assertTrue(JmxConnectors.addConnector(connector));
        JmxConnectors.removeConnector(connector);
        Assert.assertTrue(JmxConnectors.isPortAvailable(port));
    }

    public void testJmxConnectorProperlyStopped() {
        int port1 = 1;
        JmxConnector connector1 = new JmxConnector(port1, "name-1") {};
        Assert.assertTrue(JmxConnectors.addConnector(connector1));
        int port2 = 2;
        JmxConnector connector2 = new JmxConnector(port2, "name-2") {};
        Assert.assertTrue(JmxConnectors.addConnector(connector2));
        Assert.assertFalse(JmxConnectors.isPortAvailable(port1));
        Assert.assertFalse(JmxConnectors.isPortAvailable(port2));
        JmxConnectors.stopConnectors();
        Assert.assertTrue(JmxConnectors.isPortAvailable(port1));
        Assert.assertTrue(JmxConnectors.isPortAvailable(port2));
    }
}
