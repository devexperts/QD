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

import com.devexperts.connector.proto.AbstractTransportConnection;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.socket.ClientSocketConnector;
import com.dxfeed.event.misc.Message;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ClientSocketConnectorStripingTest extends AbstractConnectorTest<Message> {

    private static final String SYMBOL = "ABCD";

    private String prevStripeProperty;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        prevStripeProperty = System.clearProperty("com.devexperts.qd.impl.stripe");
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (prevStripeProperty != null) {
            System.setProperty("com.devexperts.qd.impl.stripe", prevStripeProperty);
        } else {
            System.clearProperty("com.devexperts.qd.impl.stripe");
        }
    }

    @Override
    public Message createEvent(String symbol) {
        return new Message(symbol);
    }

    @Test
    public void testStriping() throws Exception {
        String striperConfig = "byhash4";
        BlockingQueue<Message> messages = new ArrayBlockingQueue<>(4);

        startPublisher(Message.class, SYMBOL);
        startFeed(SYMBOL, messages::addAll, Message.class);
        feedEndpoint.connect("127.0.0.1:" + port + "[stripe=" + striperConfig + "]");

        ClientSocketConnector connector = getClientSocketConnector(feedEndpoint);
        waitForConnectionCount(connector, 4);
        assertEquals(4, connector.getCurrentAddresses().length);

        QDFilter[] filters = getStripeFilters(connector);
        assertEquals(4, filters.length);
        assertEquals("hash0of4", filters[0].toString());
        assertEquals("hash1of4", filters[1].toString());
        assertEquals("hash2of4", filters[2].toString());
        assertEquals("hash3of4", filters[3].toString());

        Message message = messages.poll(WAIT_TIMEOUT, TimeUnit.SECONDS);
        assertNotNull("Missed message from publisher", message);
        assertEquals(SYMBOL, message.getEventSymbol());

        // Should receive only one message
        assertNull(messages.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testNoAutoStriping() throws Exception {
        BlockingQueue<Message> messages = new ArrayBlockingQueue<>(4);

        startPublisher(Message.class, SYMBOL);
        startFeed(SYMBOL, messages::addAll, Message.class);
        feedEndpoint.connect("127.0.0.1:" + port + "[stripe=auto]");

        ClientSocketConnector connector = getClientSocketConnector(feedEndpoint);
        waitForConnectionCount(connector, 1);
        assertEquals(1, connector.getCurrentAddresses().length);

        QDFilter[] filters = getStripeFilters(connector);
        assertEquals(1, filters.length);
        assertNull(filters[0]);

        Message message = messages.poll(WAIT_TIMEOUT, TimeUnit.SECONDS);
        assertNotNull("Missed message from publisher", message);
        assertEquals(SYMBOL, message.getEventSymbol());

        // Should receive only one message
        assertNull(messages.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testAutoStriping() throws Exception {
        System.setProperty("com.devexperts.qd.impl.stripe", "2");

        BlockingQueue<Message> messages = new ArrayBlockingQueue<>(4);

        startPublisher(Message.class, SYMBOL);
        startFeed(SYMBOL, messages::addAll, Message.class);
        feedEndpoint.connect("127.0.0.1:" + port + "[stripe=auto]");

        ClientSocketConnector connector = getClientSocketConnector(feedEndpoint);
        waitForConnectionCount(connector, 2);
        assertEquals(2, connector.getCurrentAddresses().length);

        QDFilter[] filters = getStripeFilters(connector);
        assertEquals(2, filters.length);
        assertEquals("hash0of2", filters[0].toString());
        assertEquals("hash1of2", filters[1].toString());

        Message message = messages.poll(WAIT_TIMEOUT, TimeUnit.SECONDS);
        assertNotNull("Missed message from publisher", message);
        assertEquals(SYMBOL, message.getEventSymbol());

        // Should receive only one message
        assertNull(messages.poll(200, TimeUnit.MILLISECONDS));
    }

    private QDFilter[] getStripeFilters(ClientSocketConnector connector) throws Exception {
        return Arrays.stream(getConnections(connector))
            .map(c -> c.variables().get(MessageConnectors.LOCAL_STRIPE_KEY))
            .toArray(QDFilter[]::new);
    }

    //FIXME Create an API to navigate from Connector to Connection
    private AbstractTransportConnection[] getConnections(ClientSocketConnector connector) throws Exception {
        return (AbstractTransportConnection[]) getPrivateField(connector, ClientSocketConnector.class, "handlers");
    }

    private static Object getPrivateField(Object object, Class<?> clazz, String fieldName) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(object);
    }
}
