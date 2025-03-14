/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
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
import com.dxfeed.event.misc.TextConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

@RunWith(Parameterized.class)
public class TextConfigurationTest {
    private static final String SYMBOL = "TEST";

    private final Queue<TextConfiguration> receivedEvents = new ArrayDeque<>();

    private DXEndpoint endpoint;
    private DXFeed feed;
    private DXPublisher publisher;
    private DXFeedSubscription<TextConfiguration> sub;

    @Parameterized.Parameter()
    public boolean isVersionFieldEnabled;

    @Parameterized.Parameters(name = "isVersionFieldEnabled:{0}")
    public static List<Boolean> params() {
        return Arrays.asList(true, false);
    }

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.LOCAL_HUB)
            .withProperty("dxscheme.enabled.Version", isVersionFieldEnabled ? "*" : "")
            .build();
        endpoint.executor(Runnable::run);
        feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
        sub = feed.createSubscription(TextConfiguration.class);
        sub.addEventListener(receivedEvents::addAll);
        sub.addSymbols(SYMBOL);
    }

    @After
    public void tearDown() {
        sub.close();
        endpoint.close();
        ThreadCleanCheck.after();
    }

    @Test
    public void testConflation() {
        TextConfiguration one = createTextConfiguration(100, 1, "one", 1);
        TextConfiguration two = createTextConfiguration(200, 2, "two", 2);
        TextConfiguration three = createTextConfiguration(300, 3, "three", 3);
        publishEvents(one, two, three);
        assertOnlyOneEventReceived(); // only one event is received after conflation
        assertReceivedEvent(three);
        assertLastEvent(three); // the same last event
    }

    @Test
    public void testConflationWithEnabledVersionField() {
        assumeTrue(isVersionFieldEnabled); // skip if version field disabled

        TextConfiguration two = createTextConfiguration(200, 2, "two", 2);
        TextConfiguration three = createTextConfiguration(300, 3, "three", 3);
        TextConfiguration one = createTextConfiguration(100, 1, "one", 1);
        publishEvents(two, three, one);
        assertOnlyOneEventReceived(); // only one event is received after conflation
        assertReceivedEvent(three); // the one with the highest version field (3)
        assertLastEvent(three); // the same last event

        publishEvents(one, two); // publish the event with version one and two again
        assertTrue(receivedEvents.isEmpty()); // no new events are received as a version is lower than the highest (3)

        three.setText("new three"); // modify the text field in the event with the highest version (3)
        publishEvents(three);
        assertReceivedEvent(three); // the updated event is received

        TextConfiguration four = createTextConfiguration(400, 4, "four", 4);
        publishEvents(four); // publish event with version four
        assertReceivedEvent(four); // event with version four received
        assertLastEvent(four); // the same last event
    }

    @Test
    public void testConflationWithDisabledVersionField() {
        assumeTrue(!isVersionFieldEnabled); // skip if version field enabled

        TextConfiguration two = createTextConfiguration(200, 2, "two", 2);
        TextConfiguration three = createTextConfiguration(300, 3, "three", 3);
        TextConfiguration one = createTextConfiguration(100, 1, "one", 1);
        publishEvents(two, three, one);
        assertOnlyOneEventReceived(); // only one event is received after conflation
        assertReceivedEvent(one); // this is the last published event, regardless of the version number
        assertLastEvent(one); // the same last event
    }

    @Test
    public void testEmptyTextConfiguration() {
        TextConfiguration conf = new TextConfiguration();
        assertNull(conf.getEventSymbol());
        assertEquals(0, conf.getTime());
        assertEquals(0, conf.getSequence());
        assertEquals(0, conf.getVersion());
        assertNull(conf.getText());
    }

    private void assertOnlyOneEventReceived() {
        assertEquals(1, receivedEvents.size());
    }

    private void assertReceivedEvent(TextConfiguration expected) {
        assertEvent(expected, receivedEvents.poll());
    }

    private void assertLastEvent(TextConfiguration expected) {
        assertEvent(expected, feed.getLastEvent(new TextConfiguration(SYMBOL)));
    }

    private void assertEvent(TextConfiguration expected, TextConfiguration actual) {
        assertNotNull(actual);
        assertEquals(expected.getEventSymbol(), actual.getEventSymbol());
        assertEquals(expected.getTime(), actual.getTime());
        assertEquals(expected.getSequence(), actual.getSequence());
        assertEquals(expected.getText(), actual.getText());
        assertEquals(isVersionFieldEnabled ? expected.getVersion() : 0, actual.getVersion());
    }

    private TextConfiguration createTextConfiguration(long time, int sequence, String text, int version) {
        TextConfiguration conf = new TextConfiguration(SYMBOL);
        conf.setTime(time);
        conf.setSequence(sequence);
        conf.setText(text);
        conf.setVersion(version);
        return conf;
    }

    private void publishEvents(TextConfiguration... conf) {
        publisher.publishEvents(Arrays.asList(conf));
    }
}
