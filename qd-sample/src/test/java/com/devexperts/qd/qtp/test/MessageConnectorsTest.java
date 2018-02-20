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
package com.devexperts.qd.qtp.test;

import java.util.List;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.*;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.qtp.socket.ServerSocketConnector;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.test.TestDataScheme;
import junit.framework.TestCase;

public class MessageConnectorsTest extends TestCase {
    DataScheme scheme = new TestDataScheme();
    QDTicker ticker = QDFactory.getDefaultFactory().createTicker(scheme);

    public void testDistributorServerConnectorWithLegacyFilter() {
        ConfigurableMessageAdapterFactory originalFactory = new DistributorAdapter.Factory(ticker);
        List<MessageConnector> connectors = MessageConnectors.createMessageConnectors(originalFactory,
            "TEST@:1234", QDStats.VOID);

        assertEquals(1, connectors.size());

        ServerSocketConnector connector = (ServerSocketConnector) connectors.get(0);
        ApplicationConnectionFactory cf = connector.getFactory();
        MessageAdapter.Factory maf = MessageConnectors.retrieveMessageAdapterFactory(cf);

        assertEquals(1234, connector.getLocalPort());
        assertEquals("*:1234", connector.getAddress());
        assertEquals("Distributor[TEST]", cf.toString());
        assertEquals("Distributor[TEST]", maf.toString());
        assertEquals("TEST", cf.getConfiguration(MessageConnectors.FILTER_CONFIGURATION_KEY));
        assertEquals("ServerSocket-Distributor[TEST]", connector.getName());
    }

    public void testDistributorServerConnectorWithPropertyFilter() {
        ConfigurableMessageAdapterFactory originalFactory = new DistributorAdapter.Factory(ticker);
        List<MessageConnector> connectors = MessageConnectors.createMessageConnectors(originalFactory,
            ":1234[filter=TEST]", QDStats.VOID);

        assertEquals(1, connectors.size());

        ServerSocketConnector connector = (ServerSocketConnector) connectors.get(0);
        ApplicationConnectionFactory cf = connector.getFactory();
        MessageAdapter.Factory maf = MessageConnectors.retrieveMessageAdapterFactory(cf);

        assertEquals(1234, connector.getLocalPort());
        assertEquals("*:1234", connector.getAddress());
        assertEquals("Distributor[TEST]", cf.toString());
        assertEquals("Distributor[TEST]", maf.toString());
        assertEquals("TEST", cf.getConfiguration(MessageConnectors.FILTER_CONFIGURATION_KEY));
        assertEquals("ServerSocket-Distributor[TEST]", connector.getName());
    }

    public void testUniqueNames() {
        ConfigurableMessageAdapterFactory originalFactory = new DistributorAdapter.Factory(ticker);
        List<MessageConnector> connectors = MessageConnectors.createMessageConnectors(originalFactory,
            "(:1234[filter=TEST])(:1234)(demo:1234)(demo:1234)", QDStats.VOID);

        assertEquals(4, connectors.size());

        // addresses
        assertEquals("*:1234", connectors.get(0).getAddress());
        assertEquals("*:1234", connectors.get(1).getAddress());
        assertEquals("demo:1234", connectors.get(2).getAddress());
        assertEquals("demo:1234", connectors.get(3).getAddress());

        // names
        assertEquals("ServerSocket-Distributor[TEST]", connectors.get(0).getName());
        assertEquals("ServerSocket-Distributor", connectors.get(1).getName());
        assertEquals("ClientSocket-Distributor", connectors.get(2).getName());
        assertEquals("ClientSocket-Distributor-1", connectors.get(3).getName());
    }

    public void testSpecialCharactersInPassword() {
        ConfigurableMessageAdapterFactory originalFactory = new DistributorAdapter.Factory(ticker);
        List<MessageConnector> connectors = MessageConnectors.createMessageConnectors(originalFactory,
            "(ssl[trustStorePassword=!it+can@be-complex]+:1234[user=name,password=has@special+characters!])", QDStats.VOID);
        assertEquals(1, connectors.size());

        // check attributes
        assertEquals("*:1234", connectors.get(0).getAddress());
        assertEquals("name", connectors.get(0).getUser());
        assertEquals("has@special+characters!", connectors.get(0).getPassword());

        assertEquals("com.devexperts.connector.codec.ssl.SSLConnectionFactory",
            connectors.get(0).getFactory().getClass().getName());
    }
}
