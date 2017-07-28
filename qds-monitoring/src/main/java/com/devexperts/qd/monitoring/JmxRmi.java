/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.monitoring;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.rmi.registry.LocateRegistry;
import java.util.Properties;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.rmi.RMIConnectorServer;
import javax.management.remote.rmi.RMIJRMPServerImpl;

import com.devexperts.management.Management;
import com.devexperts.qd.QDLog;

/**
 * Separate class so that </code>RMIConnectorServer</code> class is loaded only when needed.
 */
class JmxRmi {
    static JmxConnector init(Properties props) throws IOException {
        Integer port = Integer.decode(props.getProperty(JMXEndpoint.JMX_RMI_PORT_PROPERTY));

        if (!JmxConnectors.isPortAvailable(port))
            return null;

        String name = "com.devexperts.qd.monitoring:type=RmiServer,port=" + port;
        RMIJRMPServerImpl srvImpl = new RMIJRMPServerImpl(port, null, null, null);
        RMIConnectorServer rmiServer = new RMIConnectorServer(
            new JMXServiceURL("service:jmx:rmi:///jndi/rmi://localhost:" + port +  "/jmxrmi"),
            null, srvImpl, ManagementFactory.getPlatformMBeanServer());
        ConnectorImpl connector = new ConnectorImpl(name, rmiServer);
        if (!JmxConnectors.addConnector(port, connector))
            return null; // port is already taken

        LocateRegistry.createRegistry(port);
        connector.setRegistration(Management.registerMBean(rmiServer, null, name));
        rmiServer.start();
        QDLog.log.info("RMI management port is " + port);
        return connector;
    }

    private static class ConnectorImpl extends JmxConnector {
        private final RMIConnectorServer rmiServer;

        ConnectorImpl(String name, RMIConnectorServer rmiServer) {
            super(name);
            this.rmiServer = rmiServer;
        }

        @Override
        public void stop() throws IOException {
            super.stop();
            rmiServer.stop();
        }
    }
}
