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
package com.devexperts.qd.qtp.test;

import com.devexperts.connector.codec.CodecConnectionFactory;
import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.connector.proto.ConfigurationException;
import com.devexperts.connector.proto.ConfigurationKey;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.qd.qtp.AddressSyntaxException;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.nio.NioServerConnector;
import com.devexperts.qd.qtp.socket.ClientSocketConnector;
import com.devexperts.qd.qtp.socket.ServerSocketConnector;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class AddressFormatTest {

    private final ApplicationConnectionFactory ACF = new ApplicationConnectionFactory() {
        @Override
        public ApplicationConnection<?> createConnection(TransportConnection transportConnection) {
            return null;
        }
        @Override
        public <T> boolean setConfiguration(ConfigurationKey<T> key, T value) throws ConfigurationException {
            if (key.equals(MessageConnectors.FILTER_CONFIGURATION_KEY)) {
                filters.add((String) value);
                return true;
            } else
                return super.setConfiguration(key, value);
        }

        public String toString() {
            return null;
        }
    };

    List<String> filters = new ArrayList<>();

    @Test
    public void testAddressFormats() throws Exception {
        filters.clear();
        validate1(MessageConnectors.createMessageConnectors(ACF,
            "(haba@xor(secret=secret)+tls[isServer=true]+1.2.3.4:5678)(ssl[isServer]+:1111(bindAddr=7.7.7.7))"));
        filters.clear();
        validate1(MessageConnectors.createMessageConnectors(ACF,
            "(haba@xor+tls[isServer=true]+1.2.3.4:5678)(ssl(isServer)+:1111[bindAddr=7.7.7.7])"));
        filters.clear();
        validate1(MessageConnectors.createMessageConnectors(ACF,
            "haba@xor[secret=secret]+tls[isServer=true]+1.2.3.4:5678/ssl[isServer]+:1111(bindAddr=7.7.7.7)"));

        try {
            MessageConnectors.createMessageConnectors(ACF, "xor+[secret=12345]http://foo.com:1234");
            fail();
        } catch (AddressSyntaxException ignored) {}
        try {
            MessageConnectors.createMessageConnectors(ACF, "zlib+http://foo.com:1234[compression=5]");
            fail();
        } catch (AddressSyntaxException ignored) {}
    }

    @Test
    public void testNioAmbiguity() {
        try {
            MessageConnectors.createMessageConnectors(ACF, "nio:7777");
            fail();
        } catch (AddressSyntaxException ignored) {}

        List<MessageConnector> connectors = MessageConnectors.createMessageConnectors(ACF, "nio::7777");
        assertEquals(1, connectors.size());
        MessageConnector nioConnector = connectors.get(0);
        assertEquals(NioServerConnector.class, nioConnector.getClass());

        connectors = MessageConnectors.createMessageConnectors(ACF, "socket:nio:7777");
        assertEquals(1, connectors.size());
        MessageConnector clientConnector = connectors.get(0);
        assertEquals(ClientSocketConnector.class, clientConnector.getClass());
        assertEquals(clientConnector.getAddress(), "nio:7777");
        assertEquals(((ClientSocketConnector) clientConnector).getHost(), "nio");
    }

    private final Field delegateField;
    {
        try {
            delegateField = CodecConnectionFactory.class.getDeclaredField("delegate");
        } catch (NoSuchFieldException e) {
            throw new AssertionError(e.getMessage());
        }
    }

    private void validate1(List<MessageConnector> connectors) throws Exception {
        assertEquals(2, connectors.size());
        assertEquals(Arrays.asList("haba", ""), filters);

        ClientSocketConnector clientSocketConnector = (ClientSocketConnector) connectors.get(0);
        assertEquals("1.2.3.4:5678", clientSocketConnector.getAddress());
        assertEquals(5678, clientSocketConnector.getPort());
        assertFalse(clientSocketConnector.getTls());
        ApplicationConnectionFactory xorFactory = clientSocketConnector.getFactory();
        assertEquals("secret", xorFactory.getConfiguration(ConfigurationKey.create("secret", String.class)));
        assertEquals(xorFactory.getClass().getSimpleName(), "XorConnectionFactory");
        ApplicationConnectionFactory sslFactory = ((CodecConnectionFactory) xorFactory).getDelegate();
        assertEquals(sslFactory.getClass().getSimpleName(), "SSLConnectionFactory");


        delegateField.setAccessible(true);
        assertEquals(ACF.getClass(), delegateField.get(sslFactory).getClass());

        ServerSocketConnector serverSocketConnector = (ServerSocketConnector) connectors.get(1);
        assertEquals("7.7.7.7:1111", serverSocketConnector.getAddress());
        assertEquals(1111, serverSocketConnector.getLocalPort());
        assertEquals("7.7.7.7", serverSocketConnector.getBindAddr());
        assertFalse(serverSocketConnector.getTls());
        sslFactory = serverSocketConnector.getFactory();
        assertEquals(sslFactory.getClass().getSimpleName(), "SSLConnectionFactory");
        delegateField.setAccessible(true);
        assertEquals(ACF.getClass(), delegateField.get(sslFactory).getClass());
    }
}
