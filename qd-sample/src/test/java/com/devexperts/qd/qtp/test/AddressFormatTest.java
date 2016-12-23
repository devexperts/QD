/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.qtp.test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

import com.devexperts.connector.codec.CodecConnectionFactory;
import com.devexperts.connector.proto.*;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.qtp.nio.NioServerConnector;
import com.devexperts.qd.qtp.socket.ClientSocketConnector;
import com.devexperts.qd.qtp.socket.ServerSocketConnector;
import junit.framework.TestCase;

public class AddressFormatTest extends TestCase {

	private final ApplicationConnectionFactory ACF = new ApplicationConnectionFactory() {
		@Override
		public ApplicationConnection<?> createConnection(TransportConnection transportConnection) throws IOException {
			return null;
		}
		@Override
		public <T> boolean setConfiguration(ConfigurationKey<T> key, T value) throws ConfigurationException {
			if (key.equals(MessageConnectors.FILTER_CONFIGURATION_KEY)) {
				filters.add((String)value);
				return true;
			} else
				return super.setConfiguration(key, value);
		}

		public String toString() {
			return null;
		}
	};

	List<String> filters = new ArrayList<>();

	public void testAddressFormats() throws Exception {
		filters.clear();
		validate1(MessageConnectors.createMessageConnectors(ACF, "(haba@tls+xor(secret=secret)+1.2.3.4:5678)(ssl[isServer]+:1111(bindAddr=7.7.7.7))"));
		filters.clear();
		validate1(MessageConnectors.createMessageConnectors(ACF, "(haba@tls+xor+1.2.3.4:5678)(ssl(isServer)+:1111[bindAddr=7.7.7.7])"));
		filters.clear();
		validate1(MessageConnectors.createMessageConnectors(ACF, "haba@tls+xor[secret=secret]+1.2.3.4:5678/ssl[isServer]+:1111(bindAddr=7.7.7.7)"));

		try {
			MessageConnectors.createMessageConnectors(ACF, "xor+tls+:1234");
			fail();
		} catch (AddressSyntaxException ignored) {}
		try {
			MessageConnectors.createMessageConnectors(ACF, "xor+[secret=12345]http://foo.com:1234");
			fail();
		} catch (AddressSyntaxException ignored) {}
		try {
			MessageConnectors.createMessageConnectors(ACF, "zlib+http://foo.com:1234[compression=5]");
			fail();
		} catch (AddressSyntaxException ignored) {}
	}

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
		assertEquals(((ClientSocketConnector)clientConnector).getHost(), "nio");
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

		ClientSocketConnector clientSocketConnector = (ClientSocketConnector)connectors.get(0);
		assertEquals("1.2.3.4:5678", clientSocketConnector.getAddress());
		assertEquals(5678, clientSocketConnector.getPort());
		assertTrue(clientSocketConnector.getTls());
		ApplicationConnectionFactory xorFactory = clientSocketConnector.getFactory();
		assertEquals("secret", xorFactory.getConfiguration(ConfigurationKey.create("secret", String.class)));
		assertEquals(xorFactory.getClass().getSimpleName(), "XorConnectionFactory");

		delegateField.setAccessible(true);
		assertEquals(ACF.getClass(), delegateField.get(xorFactory).getClass());

		ServerSocketConnector serverSocketConnector = (ServerSocketConnector)connectors.get(1);
		assertEquals("7.7.7.7:1111", serverSocketConnector.getAddress());
		assertEquals(1111, serverSocketConnector.getLocalPort());
		assertEquals("7.7.7.7", serverSocketConnector.getBindAddr());
		assertFalse(serverSocketConnector.getTls());
		ApplicationConnectionFactory sslFactory = serverSocketConnector.getFactory();
		assertEquals(sslFactory.getClass().getSimpleName(), "SSLConnectionFactory");
		delegateField.setAccessible(true);
		assertEquals(ACF.getClass(), delegateField.get(sslFactory).getClass());
	}


}
