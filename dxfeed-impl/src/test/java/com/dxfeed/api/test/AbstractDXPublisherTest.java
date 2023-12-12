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

import com.devexperts.logging.Logging;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunnerWithParametersFactory;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXFeedTimeSeriesSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.EventType;
import com.dxfeed.event.LastingEvent;
import com.dxfeed.event.TimeSeriesEvent;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(TraceRunnerWithParametersFactory.class)
public abstract class AbstractDXPublisherTest {
    private static final Logging log = Logging.getLogging(AbstractDXPublisherTest.class);

    protected final DXEndpoint.Role role;
    protected ExecutorService executorService;
    protected Executor executor;
    protected AtomicInteger executionCount;
    protected DXEndpoint endpoint;
    protected DXFeed feed;
    protected DXPublisher publisher;

    protected AbstractDXPublisherTest(DXEndpoint.Role role) {
        this.role = role;
    }

    @Parameterized.Parameters(name="{0}")
    public static Iterable<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
            {DXEndpoint.Role.LOCAL_HUB},
            {DXEndpoint.Role.STREAM_FEED}
        });
    }

    public void setUp(String description) {
        ThreadCleanCheck.before(description);
        executorService = Executors.newSingleThreadExecutor();
        executor = command -> { executionCount.incrementAndGet(); executorService.execute(command); };
        executionCount = new AtomicInteger();
        endpoint = endpointBuilder().build().executor(executor);
        feed = endpoint.getFeed();
        publisher = endpoint.getPublisher();
    }

    public void tearDown() throws InterruptedException {
        endpoint.close();
        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.SECONDS);
        ThreadCleanCheck.after();
    }

    protected DXEndpoint.Builder endpointBuilder() {
        return DXEndpoint.newBuilder()
            .withRole(role)
            .withProperty("dxfeed.wildcard.enable", "true")
            .withProperty(DXEndpoint.DXENDPOINT_EVENT_TIME_PROPERTY, "true");
    }

    protected <T extends TimeSeriesEvent<?>> void testTimeSeriesEventPublishing(Class<T> clazz,
        Object symbol, EventCreator<T> eventCreator, EventChecker<T> eventChecker) throws InterruptedException
    {
        setUp("testTimeSeriesEventPublishing for " + clazz.getSimpleName() + ", symbol=" + symbol);
        DXFeedTimeSeriesSubscription<T> sub = feed.createTimeSeriesSubscription(clazz);
        sub.setFromTime(0);
        testEventPublishing(clazz, symbol, eventCreator, eventChecker, sub);
        tearDown();
    }

    protected <T extends TimeSeriesEvent<?>> void testTimeSeriesEventPublishing(Class<T> clazz,
        Object symbol, EventCreator<T> eventCreator) throws InterruptedException
    {
        setUp("testTimeSeriesEventPublishing for " + clazz.getSimpleName() + ", symbol=" + symbol);
        DXFeedTimeSeriesSubscription<T> sub = feed.createTimeSeriesSubscription(clazz);
        sub.setFromTime(0);
        testEventPublishing(clazz, symbol, eventCreator, new DefaultEventChecker<>(), sub);
        tearDown();
    }

    protected <T extends EventType<?>> void testEventPublishing(Class<T> clazz,
        Object symbol, EventCreator<T> eventCreator, EventChecker<T> eventChecker) throws InterruptedException
    {
        setUp("testEventPublishing for " + clazz.getSimpleName() + ", symbol=" + symbol);
        testEventPublishing(clazz, symbol, eventCreator, eventChecker, feed.createSubscription(clazz));
        tearDown();
    }

    protected <T extends EventType<?>> void testEventPublishing(Class<T> clazz,
        Object symbol, EventCreator<T> eventCreator) throws InterruptedException
    {
        setUp("testEventPublishing for " + clazz.getSimpleName() + ", symbol=" + symbol);
        testEventPublishing(clazz, symbol, eventCreator, new DefaultEventChecker<>(), feed.createSubscription(clazz));
        tearDown();
    }

    @SuppressWarnings("unchecked")
    private <T extends EventType<?>> void testEventPublishing(Class<T> clazz,
        Object symbol, EventCreator<T> eventCreator, EventChecker<T> eventChecker, DXFeedSubscription<T> sub)
    {
        final BlockingQueue<Object> subAddQueue = new ArrayBlockingQueue<>(100);
        final BlockingQueue<Object> subRemoveQueue = new ArrayBlockingQueue<>(100);
        final BlockingQueue<T> queue = new ArrayBlockingQueue<>(1);
        sub.addEventListener(events -> {
            log.trace("eventsReceived " + events);
            assertEquals(1, events.size());
            queue.addAll(events);
        });
        ObservableSubscriptionChangeListener observableSubChangeListener = new ObservableSubscriptionChangeListener() {
            @Override
            public void symbolsAdded(Set<?> symbols) {
                log.trace("symbolsAdded " + symbols);
                assertTrue(!symbols.isEmpty());
                for (Object symbol : symbols) {
                    subAddQueue.add(getEventSymbol(symbol));
                }
            }

            @Override
            public void symbolsRemoved(Set<?> symbols) {
                log.trace("symbolsRemoved " + symbols);
                assertTrue(!symbols.isEmpty());
                for (Object symbol : symbols) {
                    subRemoveQueue.add(getEventSymbol(symbol));
                }
            }

            private Object getEventSymbol(Object symbol) {
                if (symbol instanceof TimeSeriesSubscriptionSymbol<?>)
                    return ((TimeSeriesSubscriptionSymbol<?>) symbol).getEventSymbol();
                return symbol;
            }
        };
        publisher.getSubscription(clazz).addChangeListener(observableSubChangeListener);
        log.trace("Adding symbol " + symbol);
        sub.addSymbols(symbol);
        checkpoint();
        assertEquals(symbol, subAddQueue.poll());
        assertEquals(0, subAddQueue.size());
        assertEquals(0, subRemoveQueue.size());
        for (int i = 0; i < 100; i++) {
            T pubEvent = eventCreator.createEvent(i);
            log.trace("Publishing " + pubEvent);
            publisher.publishEvents(Collections.singletonList(pubEvent));
            checkpoint();
            // Test subscription
            T subEvent = queue.poll();
            assertTrue(subEvent != pubEvent);
            eventChecker.check(pubEvent, subEvent);
        }
        sub.close();
        checkpoint();
        assertEquals(symbol, subRemoveQueue.poll());
        assertEquals(0, subAddQueue.size());
        assertEquals(0, subRemoveQueue.size());
        publisher.getSubscription(clazz).removeChangeListener(observableSubChangeListener);
        checkpoint();
    }

    @SuppressWarnings("unchecked")
    protected <S, T extends EventType<S> & LastingEvent<S>, L extends T, P extends T> void testGetLastEvent(
        Class<T> clazz, P pubEvent, L lastEvent, EventChecker<T> eventChecker) throws InterruptedException
    {
        if (role != DXEndpoint.Role.LOCAL_HUB)
            return;
        setUp("testGetLastEvent for " + clazz.getSimpleName() + ", pubEvent=" + pubEvent);
        DXFeedSubscription<T> sub = feed.createSubscription(clazz);
        sub.addSymbols(pubEvent.getEventSymbol());
        publisher.publishEvents(Collections.singletonList(pubEvent));
        checkpoint();
        lastEvent.setEventSymbol(pubEvent.getEventSymbol());
        lastEvent = feed.getLastEvent(lastEvent);
        assertTrue(lastEvent != pubEvent);
        eventChecker.check(pubEvent, lastEvent);
        tearDown();
    }

    protected void checkpoint() {
        try {
            for (int i = 1;; i++) {
                int loop = i;
                int count = executionCount.get();
                executorService.submit(() -> log.trace("Executing checkpoint #" + loop + " for " + count)).get();
                if (executionCount.get() == count)
                    break;
            }
            log.trace("Checkpoint done");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.toString());
        }
    }

    private static class DefaultEventChecker<T extends EventType<?>> implements EventChecker<T> {

        /**
         * Checks that all getXXX methods from the 1st object return same values in the 2nd object.
         * Ignores {@code Object#getClass getClass} method.
         */
        @Override
        public void check(T publishedEvent, T receivedEvent) {
            assertNotNull(publishedEvent);
            assertNotNull(receivedEvent);
            Method[] methods = publishedEvent.getClass().getMethods();
            for (Method method : methods) {
                if (!method.getName().equals("getClass") &&
                    method.getName().matches("(get|is).*") &&
                    method.getParameterTypes().length == 0)
                {
                    try {
                        Object expectedRes = method.invoke(publishedEvent);
                        Object actualRes = method.invoke(receivedEvent);
                        assertEquals(method.getName(), expectedRes, actualRes);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
    }

    protected interface EventCreator<T extends EventType<?>> {
        T createEvent(int i);
    }

    protected interface EventChecker<T extends EventType<?>> {
        void check(T publishedEvent, T receivedEvent);
    }
}
