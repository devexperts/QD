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
package com.devexperts.qd.qtp.test;

import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.socket.ServerSocketConnector;
import com.devexperts.qd.spi.QDFilterFactory;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.test.TestDataScheme;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests the read-only {@code DisplayFilter}, {@code DisplayChannels} and {@code Role} JMX attributes of
 * {@link com.devexperts.qd.qtp.MessageConnectorMBean}.
 */
public class MessageConnectorDisplayAttributesTest {

    // ========== DisplayFilter ==========

    @Test
    public void testFilterFromAddressStringSpec() {
        assertEquals("TEST", distributor("TEST@:1234").getDisplayFilter());
    }

    @Test
    public void testFilterFromAddressStringProperty() {
        assertEquals("TEST", distributor(":1234[filter=TEST]").getDisplayFilter());
    }

    @Test
    public void testFilterIsAnythingWhenNotConfigured() {
        assertEquals("*", distributor(":1234").getDisplayFilter());
    }

    /** The attribute reports the effective filter, so a filter passed to the factory combines into it. */
    @Test
    public void testFilterCombinesFactoryFilterWithAddressFilter() {
        QDFilter factoryFilter = CompositeFilters.valueOf("B*", SCHEME);
        MessageConnector connector = single(MessageConnectors.createMessageConnectors(
            new DistributorAdapter.Factory(ticker, null, null, factoryFilter), "TEST@:1234", QDStats.VOID));

        // "B*" and "TEST" share no symbol, so their conjunction is QDFilter.NOTHING
        assertEquals("!*", connector.getDisplayFilter());
    }

    @Test
    public void testFilterCredentialsAreMasked() {
        assertEquals(IPF_SPEC_MASKED, distributor(":1234[filter=" + IPF_SPEC + "]").getDisplayFilter());
    }

    // ========== DisplayChannels ==========

    @Test
    public void testChannelsAsConfigured() {
        assertEquals("(A*&ticker)(stream)", agent(":1234[channels=(A*&ticker)(stream)]").getDisplayChannels());
    }

    @Test
    public void testChannelsIsEmptyWhenNotConfigured() {
        assertEquals("", agent(":1234").getDisplayChannels());
    }

    @Test
    public void testChannelsIsNullOnDistributorConnector() {
        assertNull(distributor("TEST@:1234").getDisplayChannels());
    }

    @Test
    public void testChannelsCredentialsAreMasked() {
        assertEquals("(" + IPF_SPEC_MASKED + "&ticker)",
            agent(":1234[channels=(" + IPF_SPEC + "&ticker)]").getDisplayChannels());
    }

    // ========== Role ==========

    @Test
    public void testRoleIsUplinkOnDistributorConnector() {
        assertEquals("Uplink", distributor(":1234").getRole());
    }

    @Test
    public void testRoleIsDownlinkOnAgentConnector() {
        assertEquals("Downlink", agent(":1234").getRole());
    }

    @Test
    public void testRoleIsNullOnFactoryOfAnotherKind() {
        MessageConnector connector = single(MessageConnectors.createMessageConnectors(
            new OtherKindFactory(ticker), "TEST@:1234", QDStats.VOID));

        assertNull(connector.getRole());
        assertEquals("TEST", connector.getDisplayFilter());
        assertNull(connector.getDisplayChannels());
    }

    // ========== other connector shapes ==========

    /** A connector behind a codec layer wraps its factory, so reaching the adapter factory unwraps it. */
    @Test
    public void testAttributesAreReportedBehindCodec() {
        MessageConnector connector = single(MessageConnectors.createMessageConnectors(
            new DistributorAdapter.Factory(ticker), "TEST@tls+:1234", QDStats.VOID));

        assertNotEquals(MessageAdapter.ConfigurableFactory.class, connector.getFactory().getClass());
        assertEquals("TEST", connector.getDisplayFilter());
        assertEquals("Uplink", connector.getRole());
    }

    /** A connector whose application connection factory is not a message adapter one reports {@code null}. */
    @Test
    public void testAttributesAreNullWithoutMessageAdapterFactory() {
        MessageConnector connector = new ServerSocketConnector(new ApplicationConnectionFactory() {
            @Override
            public ApplicationConnection<?> createConnection(TransportConnection transportConnection) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String toString() {
                return "Test";
            }
        }, 1234);

        assertNull(connector.getDisplayFilter());
        assertNull(connector.getDisplayChannels());
        assertNull(connector.getRole());
    }

    // ========== fixture ==========

    /** An IPF filter specification carrying the credentials of the IPF web service in its address. */
    private static final String IPF_SPEC = "ipf[http://ipf.host/ipf.zip?user=alice]";

    /** The same specification after obfuscation. */
    private static final String IPF_SPEC_MASKED = "ipf[http://ipf.host/ipf.zip?user=****]";

    /** Scheme with a custom filter factory, so that a filter can carry credentials without a network. */
    private static final DataScheme SCHEME = new TestDataScheme(20260805) {
        @SuppressWarnings("unchecked")
        @Override
        public <T> T getService(Class<T> serviceClass) {
            if (serviceClass == QDFilterFactory.class) {
                return (T) new QDFilterFactory(this) {
                    @Override
                    public QDFilter createFilter(String spec) {
                        return spec.startsWith("ipf") ? new IpfLikeFilter(SCHEME, spec) : null;
                    }
                };
            }
            return super.getService(serviceClass);
        }
    };

    /**
     * A filter that renders as an IPF specification, the way an {@code IPFSymbolFilter} does, so that a
     * credential-bearing filter can be built without reaching an IPF web service.
     */
    private static class IpfLikeFilter extends QDFilter {
        private final String spec;

        IpfLikeFilter(DataScheme scheme, String spec) {
            super(scheme);
            this.spec = spec;
        }

        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            return true;
        }

        @Override
        public String getDefaultName() {
            return spec;
        }
    }

    /** An adapter factory that is neither an agent-side nor a distributor-side one. */
    private static class OtherKindFactory extends MessageAdapter.AbstractFactory {
        OtherKindFactory(QDTicker ticker) {
            super(ticker, null, null, null);
        }

        @Override
        public MessageAdapter createAdapter(QDStats stats) {
            throw new UnsupportedOperationException();
        }
    }

    private final QDTicker ticker = QDFactory.getDefaultFactory().createTicker(SCHEME);
    private final QDStream stream = QDFactory.getDefaultFactory().createStream(SCHEME);

    private MessageConnector distributor(String address) {
        return single(MessageConnectors.createMessageConnectors(
            new DistributorAdapter.Factory(ticker, stream, null, null), address, QDStats.VOID));
    }

    private MessageConnector agent(String address) {
        return single(MessageConnectors.createMessageConnectors(
            new AgentAdapter.Factory(ticker, stream, null, null), address, QDStats.VOID));
    }

    private static MessageConnector single(List<MessageConnector> connectors) {
        assertEquals(1, connectors.size());
        return connectors.get(0);
    }
}
