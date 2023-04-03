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

import com.devexperts.io.IOUtil;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedEventListener;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.TimeAndSale;
import org.junit.Test;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DXFeedSubscriptionTest {
    private static int serialRecv;
    private static int nonSerialRecv;

    abstract static class ProcessEventsAccess extends DXFeed {
        public static <T> void processEvents(DXFeedSubscription<T> subscription, List<T> events) {
            DXFeed.processEvents(subscription, events);
        }
    }

    @SuppressWarnings({"unchecked"})
    @Test
    public void testSerialization() throws Exception {
        // allow direct access to package-private "processEvents" for testing purposes

        serialRecv = 0;
        nonSerialRecv = 0;
        DXFeedSubscription<Quote> sub = new DXFeedSubscription<>(Quote.class);
        sub.addEventListener(new SerializableEventListener());
        sub.addEventListener(new NonSerializableEventListener());
        List<String> symbols = Arrays.asList("IBM", "MSFT");
        sub.setSymbols(symbols);
        ProcessEventsAccess.processEvents(sub, Arrays.asList(new Quote()));
        assertEquals(1, serialRecv);
        assertEquals(1, nonSerialRecv);

        sub = (DXFeedSubscription<Quote>) IOUtil.bytesToObject(IOUtil.objectToBytes(sub));
        ProcessEventsAccess.processEvents(sub, Arrays.asList(new Quote()));
        assertEquals(new HashSet<>(symbols), sub.getSymbols());
        assertEquals(2, serialRecv);
        assertEquals(1, nonSerialRecv);
    }

    @Test
    public void testRemoveListeners() throws Exception {
        // allow direct access to package-private "processEvents" for testing purposes

        serialRecv = 0;
        nonSerialRecv = 0;
        DXFeedSubscription<Quote> sub = new DXFeedSubscription<>(Quote.class);
        SerializableEventListener serialListener = new SerializableEventListener();
        NonSerializableEventListener nonSerialListener = new NonSerializableEventListener();
        List<String> symbols = Arrays.asList("IBM", "MSFT");
        sub.setSymbols(symbols);

        sub.addEventListener(serialListener);
        ProcessEventsAccess.processEvents(sub, Arrays.asList(new Quote()));
        assertEquals(1, serialRecv);
        assertEquals(0, nonSerialRecv);

        // add again the same one
        sub.addEventListener(serialListener);
        ProcessEventsAccess.processEvents(sub, Arrays.asList(new Quote()));
        assertEquals(3, serialRecv);
        assertEquals(0, nonSerialRecv);

        // remove extra copy
        sub.removeEventListener(serialListener);
        ProcessEventsAccess.processEvents(sub, Arrays.asList(new Quote()));
        assertEquals(4, serialRecv);
        assertEquals(0, nonSerialRecv);

        sub.addEventListener(nonSerialListener);
        ProcessEventsAccess.processEvents(sub, Arrays.asList(new Quote()));
        assertEquals(5, serialRecv);
        assertEquals(1, nonSerialRecv);

        sub.removeEventListener(serialListener);
        ProcessEventsAccess.processEvents(sub, Arrays.asList(new Quote()));
        assertEquals(5, serialRecv);
        assertEquals(2, nonSerialRecv);

        sub.removeEventListener(nonSerialListener);
        ProcessEventsAccess.processEvents(sub, Arrays.asList(new Quote()));
        assertEquals(5, serialRecv);
        assertEquals(2, nonSerialRecv);
    }

    private static class SerializableEventListener implements DXFeedEventListener<Quote>, Serializable {
        private static final long serialVersionUID = 0;

        SerializableEventListener() {}

        @Override
        public void eventsReceived(List<Quote> events) {
            serialRecv++;
        }
    }

    private static class NonSerializableEventListener implements DXFeedEventListener<Quote> {
        NonSerializableEventListener() {}

        @Override
        public void eventsReceived(List<Quote> events) {
            nonSerialRecv++;
        }
    }

    @Test
    public void testSymbolSet() {
        DXFeedSubscription<TimeAndSale> sub = new DXFeedSubscription<>(TimeAndSale.class);
        MySubscriptionChangeListener cl = new MySubscriptionChangeListener(sub);
        sub.addChangeListener(cl);

        String s1 = "IBM";
        sub.addSymbols(s1);
        cl.assertAdded(s1);
        cl.assertRemoved();
        cl.assertSub(s1);

        String s2 = "MSFT";
        sub.addSymbols(s2);
        cl.assertAdded(s2);
        cl.assertRemoved();
        cl.assertSub(s1, s2);

        sub.setSymbols(s1, s2);
        cl.assertAdded();
        cl.assertRemoved();
        cl.assertSub(s1, s2);

        String s1New = new String(s1); // new instance of the same string! --> no notification
        sub.addSymbols(s1New);
        cl.assertAdded();
        cl.assertRemoved();
        cl.assertSub(s1New, s2); // but replaced in sub set
        s1 = s1New;

        s1New = new String(s1); // new instance of the same string! --> no notification
        sub.setSymbols(s1New, s2);
        cl.assertAdded();
        cl.assertRemoved();
        cl.assertSub(s1New, s2); // but replaced in sub set
        s1 = s1New;

        sub.addSymbols(s1, s2);
        cl.assertAdded();
        cl.assertRemoved();
        cl.assertSub(s1, s2);

        TimeSeriesSubscriptionSymbol<String> s3 = new TimeSeriesSubscriptionSymbol<>("IBM", 1234);
        sub.removeSymbols(s3);
        cl.assertAdded();
        cl.assertRemoved();
        cl.assertSub(s1, s2);

        sub.addSymbols(s3);
        cl.assertAdded(s3);
        cl.assertRemoved();
        cl.assertSub(s1, s2, s3);

        TimeSeriesSubscriptionSymbol<String> s4 = new TimeSeriesSubscriptionSymbol<>("IBM", 8888);
        sub.addSymbols(s4);
        cl.assertAdded(s4);
        cl.assertRemoved();
        cl.assertSub(s1, s2, s4);

        TimeSeriesSubscriptionSymbol<String> s5 = new TimeSeriesSubscriptionSymbol<>("IBM", 0);
        sub.removeSymbols(s5);
        cl.assertAdded();
        cl.assertRemoved(s4);
        cl.assertSub(s1, s2);

        sub.close();
        cl.assertClosed();
    }

    private static class MySubscriptionChangeListener implements ObservableSubscriptionChangeListener {
        Set<Object> added;
        Set<Object> removed = new HashSet<>();
        boolean closed;
        final DXFeedSubscription<?> sub;

        MySubscriptionChangeListener(DXFeedSubscription<?> sub) {
            this.sub = sub;
            added = new HashSet<>();
        }

        @Override
        public void symbolsAdded(Set<?> symbols) {
            added.addAll(symbols);
        }

        @Override
        public void symbolsRemoved(Set<?> symbols) {
            assertTrue("Remove should be first", added.isEmpty());
            removed.addAll(symbols);
        }

        @Override
        public void subscriptionClosed() {
            closed = true;
        }

        private void assertSame(Object[] symbols, Set<?> set) {
            HashSet<Object> expectedSet = new HashSet<>(Arrays.asList(symbols));
            assertEquals(expectedSet, set);
            // now also check that they are "identity equals"
            IdentityHashMap<Object, Object> expectedIdentifies = new IdentityHashMap<>();
            for (Object symbol : symbols) {
                expectedIdentifies.put(symbol, symbol);
            }
            for (Object o : set) {
                assertTrue(expectedIdentifies.containsKey(o));
            }
        }

        public void assertAdded(Object... symbols) {
            assertFalse(closed);
            assertSame(symbols, added);
            added.clear();
        }

        public void assertRemoved(Object... symbols) {
            assertFalse(closed);
            assertSame(symbols, removed);
            removed.clear();
        }

        public void assertSub(Object... symbols) {
            assertFalse(closed);
            assertSame(symbols, sub.getSymbols());
            removed.clear();
        }

        public void assertClosed() {
            assertTrue(closed);
            assertEquals(0, added.size());
            assertEquals(0, removed.size());
        }
    }
}
