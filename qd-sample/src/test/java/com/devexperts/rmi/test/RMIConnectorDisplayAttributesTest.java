/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.rmi.test;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.rmi.RMIEndpoint;
import com.devexperts.rmi.impl.RMIEndpointImpl;
import org.junit.After;
import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests that the {@code DisplayFilter}, {@code DisplayChannels} and {@code Role} JMX attributes of
 * {@link com.devexperts.qd.qtp.MessageConnectorMBean} report the attached adapter factory on a connector
 * built by {@code RMIConnectorInitializer}, whose own adapter factory only delegates to that one.
 */
public class RMIConnectorDisplayAttributesTest {

    private static final DataScheme SCHEME = new DefaultScheme(new PentaCodec());

    private QDEndpoint qdEndpoint;
    private RMIEndpointImpl rmiEndpoint;

    @After
    public void tearDown() {
        if (rmiEndpoint != null)
            rmiEndpoint.close();
        if (qdEndpoint != null)
            qdEndpoint.close();
    }

    @Test
    public void testDistributorAttributesAreReportedThroughRmiWrapper() {
        MessageConnector connector =
            rmiConnector(new DistributorAdapter.Factory(qdEndpoint(), null), "TEST@127.0.0.1:1234");
        assertEquals("TEST", connector.getDisplayFilter());
        assertEquals("Uplink", connector.getRole());
        assertNull(connector.getDisplayChannels());
    }

    @Test
    public void testAgentAttributesAreReportedThroughRmiWrapper() {
        MessageConnector connector = rmiConnector(new AgentAdapter.Factory(qdEndpoint(), null),
            "TEST@127.0.0.1:1234[channels=(A*&ticker)]");
        assertEquals("TEST", connector.getDisplayFilter());
        assertEquals("Downlink", connector.getRole());
        assertEquals("(A*&ticker)", connector.getDisplayChannels());
    }

    /** An RMI-only connector carries no QD message adapter, so all three attributes are {@code null}. */
    @Test
    public void testAttributesAreAbsentWithoutAttachedFactory() {
        MessageConnector connector = rmiConnector(null, "127.0.0.1:1234");
        assertNull(connector.getDisplayFilter());
        assertNull(connector.getDisplayChannels());
        assertNull(connector.getRole());
    }

    /**
     * An endpoint built by {@link RMIEndpoint.Builder} with no role attaches no adapter factory and
     * builds a {@code QDEndpoint} with no collectors; the attributes answer {@code null} without throwing.
     */
    @Test
    public void testAttributesAreAbsentOnEndpointBuiltWithoutRole() {
        rmiEndpoint = (RMIEndpointImpl) RMIEndpoint.newBuilder()
            .withName("test")
            .withSide(RMIEndpoint.Side.CLIENT)
            .build();
        rmiEndpoint.getQdEndpoint().initializeConnectorsForAddress("127.0.0.1:1234");
        MessageConnector connector = rmiEndpoint.getQdEndpoint().getConnectors().get(0);
        assertNull(connector.getDisplayFilter());
        assertNull(connector.getDisplayChannels());
        assertNull(connector.getRole());
    }

    private MessageConnector rmiConnector(MessageAdapter.AbstractFactory attachedFactory, String address) {
        rmiEndpoint = new RMIEndpointImpl(RMIEndpoint.Side.CLIENT, qdEndpoint(), attachedFactory, null);
        qdEndpoint.initializeConnectorsForAddress(address);
        return qdEndpoint.getConnectors().get(0);
    }

    private QDEndpoint qdEndpoint() {
        if (qdEndpoint == null) {
            qdEndpoint = QDEndpoint.newBuilder()
                .withName("test")
                .withScheme(SCHEME)
                .withCollectors(EnumSet.of(QDContract.TICKER, QDContract.STREAM, QDContract.HISTORY))
                .build();
        }
        return qdEndpoint;
    }
}
