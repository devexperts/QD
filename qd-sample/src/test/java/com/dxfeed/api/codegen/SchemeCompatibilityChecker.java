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
package com.dxfeed.api.codegen;

import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.event.EventType;
import com.dxfeed.event.LastingEvent;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;

class SchemeCompatibilityChecker {
    private static final int PUBLISHED_EVENTS_COUNT = 100;
    private static final int WAIT_TIMEOUT = 60_000; // ms

    private final DXPublisher publisher;
    private final DXFeed feed;

    SchemeCompatibilityChecker(DXPublisher publisher, DXFeed feed) {
        this.publisher = publisher;
        this.feed = feed;
    }

    <S extends EventType<?>, P extends EventType<?>> void checkEventPublishing(Class<S> subClass, Class<P> pubClass,
        Object symbol, IntFunction<P> eventCreator, BiConsumer<P, S> eventChecker) throws Exception
    {
        BlockingQueue<Object> subAddQueue = new ArrayBlockingQueue<>(1);
        BlockingQueue<Object> subRemoveQueue = new ArrayBlockingQueue<>(1);
        BlockingQueue<S> queue = new ArrayBlockingQueue<>(1);
        DXFeedSubscription<S> sub = feed.createSubscription(subClass);
        sub.addEventListener(events -> {
            assertEquals(1, events.size());
            queue.addAll(events);
        });
        sub.addSymbols(symbol);
        ObservableSubscriptionChangeListener listener = new ObservableSubscriptionChangeListener() {
            @Override
            public void symbolsAdded(Set<?> symbols) {
                assertFalse(symbols.isEmpty());
                subAddQueue.addAll(symbols);
            }

            @Override
            public void symbolsRemoved(Set<?> symbols) {
                assertFalse(symbols.isEmpty());
                subRemoveQueue.addAll(symbols);
            }
        };
        publisher.getSubscription(pubClass).addChangeListener(listener);
        assertEquals(symbol, subAddQueue.poll(WAIT_TIMEOUT, TimeUnit.MILLISECONDS));
        assertEquals(0, subAddQueue.size());
        assertEquals(0, subRemoveQueue.size());
        for (int i = 0; i < PUBLISHED_EVENTS_COUNT; i++) {
            P pubEvent = eventCreator.apply(i);
            publisher.publishEvents(Collections.singletonList(pubEvent));
            // Test subscription
            S subEvent = queue.poll(WAIT_TIMEOUT, TimeUnit.MILLISECONDS);
            assertNotSame(pubEvent, subEvent);
            eventChecker.accept(pubEvent, subEvent);
            if (LastingEvent.class.isAssignableFrom(subClass)) {
                // Test getLastEvent
                S getEvent = subClass.newInstance();
                ((EventType) getEvent).setEventSymbol(symbol);
                feed.getLastEvent((LastingEvent<?>) getEvent);
                eventChecker.accept(pubEvent, getEvent);
            }
        }
        sub.close();
        assertEquals(symbol, subRemoveQueue.poll(WAIT_TIMEOUT, TimeUnit.MILLISECONDS));
        assertEquals(0, subAddQueue.size());
        assertEquals(0, subRemoveQueue.size());
        publisher.getSubscription(pubClass).removeChangeListener(listener);
    }
}
