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
package com.devexperts.qd.qtp.test;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.ConfigurableMessageAdapterFactory;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorState;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.qtp.socket.ServerSocketConnector;
import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.test.TestDataScheme;
import com.dxfeed.promise.Promise;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MessageConnectorsTest {
    DataScheme scheme = new TestDataScheme();
    QDTicker ticker = QDFactory.getDefaultFactory().createTicker(scheme);

    @Test
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

    @Test
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

    @Test
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

    @Test
    public void testSpecialCharactersInPassword() {
        ConfigurableMessageAdapterFactory originalFactory = new DistributorAdapter.Factory(ticker);
        List<MessageConnector> connectors = MessageConnectors.createMessageConnectors(originalFactory,
            "(ssl[trustStorePassword=!it+can@be-complex]+:1234[user=name,password=has@special+characters!])",
            QDStats.VOID);
        assertEquals(1, connectors.size());

        // check attributes
        assertEquals("*:1234", connectors.get(0).getAddress());
        assertEquals("name", connectors.get(0).getUser());
        assertEquals("has@special+characters!", connectors.get(0).getPassword());

        assertEquals("com.devexperts.connector.codec.ssl.SSLConnectionFactory",
            connectors.get(0).getFactory().getClass().getName());
    }

    @Test
    public void testServerSocketMaxConnections() throws InterruptedException {
        checkMaxConnectionsForParticularServerAddress(":", 2, "maxConnections=" + 2);
    }

    @Test
    public void testServerSocketUnlimitedMaxConnectionsNoOpt() throws InterruptedException {
        checkMaxConnectionsForParticularServerAddress(":", 0, "");
    }

    @Test
    public void testServerSocketUnlimitedMaxConnections() throws InterruptedException {
        checkMaxConnectionsForParticularServerAddress(":", 0, "maxConnections=" + 0);
    }

    @Test
    public void testNioServerSocketMaxConnections() throws InterruptedException {
        checkMaxConnectionsForParticularServerAddress("nio::", 2, "maxConnections=" + 2);
    }

    @Test
    public void testNioServerSocketUnlimitedMaxConnectionsNoOpt() throws InterruptedException {
        checkMaxConnectionsForParticularServerAddress("nio::", 0, "");
    }

    @Test
    public void testNioServerSocketUnlimitedMaxConnections() throws InterruptedException {
        checkMaxConnectionsForParticularServerAddress("nio::", 0, "maxConnections=" + 0);
    }

    private void checkMaxConnectionsForParticularServerAddress(String prefix, int maxConnectionsVal,
        String maxConnectionsOpt) throws InterruptedException
    {
        String testID = UUID.randomUUID().toString();
        Promise<Integer> port = ServerSocketTestHelper.createPortPromise(testID);
        QDEndpoint outEndpoint = createEndpoint();
        outEndpoint.addConnectors(MessageConnectors.createMessageConnectors(
            new AgentAdapter.Factory(outEndpoint, null),
            prefix + "0[name=" + testID + (maxConnectionsOpt.isEmpty() ? "" : "," + maxConnectionsOpt) + "]",
            outEndpoint.getRootStats())
        );
        outEndpoint.startConnectors();

        assertEquals(1, outEndpoint.getConnectors().size());
        MessageConnector connector = outEndpoint.getConnectors().get(0);
        assertEquals(0, connector.getConnectionCount());

        int maxConnections = maxConnectionsVal == 0 ? 2 : maxConnectionsVal;

        List<QDEndpoint> inEndpoints = new ArrayList<>();
        long waitTime = 0;
        for (int i = 0; i < maxConnections; i++) {
            QDEndpoint inEndpoint = createAndConnect(port);
            inEndpoints.add(inEndpoint);
            MessageConnector inConnector = inEndpoint.getConnectors().get(0);
            //checks that server connection count was incremented
            waitTime = waitForConnection(waitTime, inConnector, i + 1);
        }

        if (maxConnectionsVal != 0) {
            //not allowed connection according to maxConnections property
            QDEndpoint inEndpoint = createAndConnect(port);
            inEndpoints.add(inEndpoint);
            MessageConnector inConnector = inEndpoint.getConnectors().get(0);
            //wait that at least one connection was closed
            waitTime = 0;
            while (inConnector.getClosedConnectionCount() == 0) {
                Thread.sleep(1);
                waitTime++;
                if (waitTime > 1000)
                    fail("Not allowed connection was not closed");
            }
            assertTrue(inConnector.getClosedConnectionCount() > 0);
        }

        for (QDEndpoint in : inEndpoints) {
            in.stopConnectorsAndWait();
            in.close();
        }
        outEndpoint.stopConnectorsAndWait();
        outEndpoint.close();
    }

    protected long waitForConnection(long waitTime, MessageConnector connector, int connectionCount)
        throws InterruptedException
    {
        while (connector.getState() != MessageConnectorState.CONNECTED &&
            connector.getConnectionCount() != connectionCount)
        {
            Thread.sleep(1);
            waitTime += 1;
            if (waitTime > 1000)
                fail("Test timeout, couldn't connect to endpoint");
        }
        return waitTime;
    }

    private QDEndpoint createAndConnect(Promise<Integer> port) {
        QDEndpoint inEndpoint = createEndpoint();
        inEndpoint.addConnectors(MessageConnectors.createMessageConnectors(
            new DistributorAdapter.Factory(inEndpoint, null),
            "localhost:" + port.await(10_000, TimeUnit.MILLISECONDS),
            inEndpoint.getRootStats())
        );
        inEndpoint.startConnectors();
        return inEndpoint;
    }

    private QDEndpoint createEndpoint() {
        return QDEndpoint.newBuilder()
            .withScheme(QDFactory.getDefaultScheme())
            .withCollectors(Collections.singletonList(QDContract.TICKER))
            .build();
    }
}
