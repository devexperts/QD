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
package com.dxfeed.api.test;

import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.misc.Message;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MessageTest {

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() throws Exception {
        ThreadCleanCheck.after();
    }

    @Test
    public void testMessageConflation() {
        // install custom task queue to delay processing
        final ConcurrentLinkedQueue<Runnable> taskQueue = new ConcurrentLinkedQueue<>();
        DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
        endpoint.executor(taskQueue::add);
        DXFeed feed = endpoint.getFeed();
        String topic = "test";

        // subscribe to messages
        DXFeedSubscription<Message> sub = feed.createSubscription(Message.class);
        final ConcurrentLinkedQueue<Message> receiveQueue = new ConcurrentLinkedQueue<>();
        sub.addEventListener(receiveQueue::addAll);
        sub.addSymbols(topic);

        // publish a couple of messages
        DXPublisher publisher = endpoint.getPublisher();
        publisher.publishEvents(Arrays.asList(
            new Message(topic, "one"),
            new Message(topic, "two"),
            new Message(topic, "three")
        ));

        // ensure they are not received yet
        assertEquals(0, receiveQueue.size());

        // process all pending tasks
        Runnable task;
        while ((task = taskQueue.poll()) != null)
            task.run();

        // make sure that all events are received
        assertEquals(3, receiveQueue.size());
        assertEquals("one", receiveQueue.poll().getAttachment());
        assertEquals("two", receiveQueue.poll().getAttachment());
        assertEquals("three", receiveQueue.poll().getAttachment());
        assertNull(receiveQueue.poll());
    }
}
