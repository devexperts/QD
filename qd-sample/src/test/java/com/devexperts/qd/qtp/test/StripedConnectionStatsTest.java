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

import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.qtp.socket.ClientSocketConnector;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.stats.QDStats.SType;
import com.devexperts.rmi.test.NTU;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.impl.DXEndpointImpl;
import com.dxfeed.event.misc.Message;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import static com.devexperts.qd.stats.QDStats.SValue.IO_DATA_READ_RECORDS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StripedConnectionStatsTest extends AbstractConnectorTest<Message> {

    private static final String SYMBOL0 = "AB0"; // Stripe 0 in byhash4
    private static final String SYMBOL1 = "ABA1"; // Stripe 1 in byhash4
    private static final String SYMBOL2 = "AB2"; // Stripe 2 in byhash4

    @Override
    public Message createEvent(String symbol) {
        return new Message(symbol);
    }
    
    @Test
    public void testNoStriping() throws Exception {
        startPublisher(Message.class, null);

        BlockingQueue<Message> messages = new ArrayBlockingQueue<>(10);

        feedEndpoint = DXEndpoint.create(DXEndpoint.Role.FEED);
        feedEndpoint.connect("127.0.0.1:" + port);

        DXFeed feed = feedEndpoint.getFeed();
        DXFeedSubscription<Message> subscription = feed.createSubscription(Message.class);
        subscription.addEventListener(messages::addAll);
        subscription.addSymbols(SYMBOL0, SYMBOL1, SYMBOL2);

        ClientSocketConnector connector = getClientSocketConnector(feedEndpoint);
        waitForConnectionCount(connector, 1);
        waitForSubscribedCount(3);

        QDStats stats = connector.getStats();
        QDStats connectionsStats = stats.get(SType.CONNECTIONS);
        assertNotNull(connectionsStats);

        // Events per stripe: Stripe0 = 3, Stripe1 = 2, Stripe2 = 1, Stripe3 = 0
        pubEndpoint.getPublisher().publishEvents(Arrays.asList(
            createEvent(SYMBOL0), createEvent(SYMBOL0), createEvent(SYMBOL0),
            createEvent(SYMBOL1), createEvent(SYMBOL1),
            createEvent(SYMBOL2)
        ));

        assertTrue(NTU.waitCondition(WAIT_TIMEOUT * 1_000L, 100, () -> (messages.size() == 6)));

        assertEquals(1, connectionsStats.getAll(SType.CONNECTION).size());
        QDStats conStats = connectionsStats.get(SType.CONNECTION);

        assertEquals(6, conStats.getValue(IO_DATA_READ_RECORDS));
        assertEquals(6, connectionsStats.getValue(IO_DATA_READ_RECORDS));
        assertEquals(6, connector.getStats().getValue(IO_DATA_READ_RECORDS));

        log.debug("close connector");
        connector.stopAndWait();
        QDEndpoint qdEndpoint = ((DXEndpointImpl) feedEndpoint).getQDEndpoint();
        qdEndpoint.cleanupConnectors();
        QDStats rootStats = qdEndpoint.getRootStats();

        assertEquals(6, rootStats.get(SType.CONNECTIONS).getValue(IO_DATA_READ_RECORDS));
        assertEquals(6, rootStats.get(SType.CONNECTIONS).getValue(IO_DATA_READ_RECORDS, true));
    }

    @Test
    public void testStriping() throws Exception {
        startPublisher(Message.class, null);

        BlockingQueue<Message> messages = new ArrayBlockingQueue<>(10);

        feedEndpoint = DXEndpoint.create(DXEndpoint.Role.FEED);
        feedEndpoint.connect("127.0.0.1:" + port + "[stripe=byhash4]");
        QDEndpoint qdEndpoint = ((DXEndpointImpl) feedEndpoint).getQDEndpoint();
        QDStats rootStats = qdEndpoint.getRootStats();

        DXFeed feed = feedEndpoint.getFeed();
        DXFeedSubscription<Message> subscription = feed.createSubscription(Message.class);
        subscription.addEventListener(messages::addAll);
        subscription.addSymbols(SYMBOL0, SYMBOL1, SYMBOL2);

        log.debug("wait connected");
        ClientSocketConnector connector = getClientSocketConnector(feedEndpoint);
        waitForConnectionCount(connector, 4);
        assertEquals(4, connector.getCurrentAddresses().length);

        log.debug("wait subscribed");
        waitForSubscribedCount(3);

        QDStats stats = connector.getStats();
        QDStats connectionsStats = stats.get(SType.CONNECTIONS);
        assertNotNull(connectionsStats);

        // Events per stripe: Stripe0 = 3, Stripe1 = 2, Stripe2 = 1, Stripe3 = 0
        pubEndpoint.getPublisher().publishEvents(Arrays.asList(
            createEvent(SYMBOL0), createEvent(SYMBOL0), createEvent(SYMBOL0),
            createEvent(SYMBOL1), createEvent(SYMBOL1),
            createEvent(SYMBOL2)
        ));

        log.debug("wait events received");
        assertTrue(NTU.waitCondition(WAIT_TIMEOUT * 1_000L, 100, () -> (messages.size() == 6)));
        messages.clear();

        List<QDStats> stripedStats = connectionsStats.getAll(SType.CONNECTION);
        assertEquals(4, stripedStats.size());

        QDStats qdStats0 = connectionsStats.get(SType.CONNECTION, "stripe=hash0of4");
        assertNotNull(qdStats0);
        QDStats qdStats1 = connectionsStats.get(SType.CONNECTION, "stripe=hash1of4");
        assertNotNull(qdStats1);
        QDStats qdStats2 = connectionsStats.get(SType.CONNECTION, "stripe=hash2of4");
        assertNotNull(qdStats2);
        QDStats qdStats3 = connectionsStats.get(SType.CONNECTION, "stripe=hash3of4");
        assertNotNull(qdStats3);

        assertEquals(0, qdStats0.getValue(IO_DATA_READ_RECORDS, true));
        assertEquals(3, qdStats0.getValue(IO_DATA_READ_RECORDS));
        assertEquals(3, qdStats0.get(SType.CONNECTION).getValue(IO_DATA_READ_RECORDS, true));
        assertEquals(3, qdStats0.get(SType.CONNECTION).getValue(IO_DATA_READ_RECORDS));

        assertEquals(0, qdStats1.getValue(IO_DATA_READ_RECORDS, true));
        assertEquals(2, qdStats1.getValue(IO_DATA_READ_RECORDS));
        assertEquals(2, qdStats1.get(SType.CONNECTION).getValue(IO_DATA_READ_RECORDS, true));
        assertEquals(2, qdStats1.get(SType.CONNECTION).getValue(IO_DATA_READ_RECORDS));

        assertEquals(0, qdStats2.getValue(IO_DATA_READ_RECORDS, true));
        assertEquals(1, qdStats2.getValue(IO_DATA_READ_RECORDS));
        assertEquals(1, qdStats2.get(SType.CONNECTION).getValue(IO_DATA_READ_RECORDS, true));
        assertEquals(1, qdStats2.get(SType.CONNECTION).getValue(IO_DATA_READ_RECORDS));

        assertEquals(0, qdStats3.getValue(IO_DATA_READ_RECORDS));

        assertEquals(0, connectionsStats.getValue(IO_DATA_READ_RECORDS, true));
        assertEquals(6, connectionsStats.getValue(IO_DATA_READ_RECORDS));
        assertEquals(0, connector.getStats().getValue(IO_DATA_READ_RECORDS, true));
        assertEquals(6, connector.getStats().getValue(IO_DATA_READ_RECORDS));

        log.debug("emulate reconnect");
        NTU.disconnectClientAbruptly(qdEndpoint, true);

        log.debug("wait connected");
        waitForConnectionCount(connector, 4);
        log.debug("wait subscribed");
        waitForSubscribedCount(3 + 3);

        pubEndpoint.getPublisher().publishEvents(Arrays.asList(
            createEvent(SYMBOL0),
            createEvent(SYMBOL1),
            createEvent(SYMBOL2)
        ));

        log.debug("wait events received");
        assertTrue(NTU.waitCondition(WAIT_TIMEOUT * 1_000L, 100, () -> (messages.size() == 3)));
        messages.clear();

        assertEquals(3, qdStats0.getValue(IO_DATA_READ_RECORDS, true));
        assertEquals(4, qdStats0.getValue(IO_DATA_READ_RECORDS));
        assertEquals(1, qdStats0.get(SType.CONNECTION).getValue(IO_DATA_READ_RECORDS, true));
        assertEquals(1, qdStats0.get(SType.CONNECTION).getValue(IO_DATA_READ_RECORDS));

        assertEquals(2, qdStats1.getValue(IO_DATA_READ_RECORDS, true));
        assertEquals(3, qdStats1.getValue(IO_DATA_READ_RECORDS));
        assertEquals(1, qdStats1.get(SType.CONNECTION).getValue(IO_DATA_READ_RECORDS, true));
        assertEquals(1, qdStats1.get(SType.CONNECTION).getValue(IO_DATA_READ_RECORDS));

        assertEquals(1, qdStats2.getValue(IO_DATA_READ_RECORDS, true));
        assertEquals(2, qdStats2.getValue(IO_DATA_READ_RECORDS));
        assertEquals(1, qdStats2.get(SType.CONNECTION).getValue(IO_DATA_READ_RECORDS, true));
        assertEquals(1, qdStats2.get(SType.CONNECTION).getValue(IO_DATA_READ_RECORDS));

        assertEquals(0, qdStats3.getValue(IO_DATA_READ_RECORDS));

        assertEquals(9, connectionsStats.getValue(IO_DATA_READ_RECORDS));
        assertEquals(9, connector.getStats().getValue(IO_DATA_READ_RECORDS));

        log.debug("close connector");
        connector.stopAndWait();
        qdEndpoint.cleanupConnectors();

        assertEquals(9, rootStats.get(SType.CONNECTIONS).getValue(IO_DATA_READ_RECORDS));
        assertEquals(9, rootStats.get(SType.CONNECTIONS).getValue(IO_DATA_READ_RECORDS, true));
    }
}
