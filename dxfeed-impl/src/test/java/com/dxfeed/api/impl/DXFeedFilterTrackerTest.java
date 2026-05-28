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
package com.dxfeed.api.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedFilter;
import com.dxfeed.api.DXFeedFilterTracker;
import com.dxfeed.event.market.Trade;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link DXFeedFilterTracker} listener notifications and filter updates.
 * Placed in {@code com.dxfeed.api.impl} for package-private access to {@link DXFeedFilterImpl}.
 */
public class DXFeedFilterTrackerTest {

    private static final String SYMBOL = "AAPL";

    private DXEndpointImpl endpoint;

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = (DXEndpointImpl) DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    // ---- Dynamic filter detection ----

    @Test
    public void testStaticFilterIsNotDynamic() {
        DXFeedFilter filter = createFilter(new TestStaticFilter(SYMBOL));
        assertFalse(filter.isDynamic());
    }

    @Test
    public void testDynamicFilterIsDynamic() {
        DXFeedFilter filter = createFilter(new TestDynamicFilter(SYMBOL));
        assertTrue(filter.isDynamic());
    }

    // ---- Listener notification ----

    @Test
    public void testListenerNotifiedOnUpdate() {
        TestDynamicFilter qdFilter = new TestDynamicFilter(SYMBOL);
        DXFeedFilter filter = createFilter(qdFilter);
        DXFeedFilterTracker tracker = filter.getTracker();

        AtomicReference<DXFeedFilterTracker> notifiedTracker = new AtomicReference<>();
        tracker.addListener(notifiedTracker::set);

        qdFilter.addSymbol("GOOG");

        assertSame(tracker, notifiedTracker.get());
    }

    @Test
    public void testListenerReceivesUpdatedSnapshot() {
        TestDynamicFilter qdFilter = new TestDynamicFilter();
        DXFeedFilter filter = createFilter(qdFilter);
        DXFeedFilterTracker tracker = filter.getTracker();

        // Initially rejects SYMBOL
        assertFalse(filter.accept(Trade.class, SYMBOL));

        AtomicReference<DXFeedFilter> updatedRef = new AtomicReference<>();
        tracker.addListener(t -> updatedRef.set(t.getCurrentFilter()));

        qdFilter.addSymbol(SYMBOL);

        DXFeedFilter updated = updatedRef.get();
        assertNotSame(filter, updated);
        assertTrue(updated.accept(Trade.class, SYMBOL));
    }

    @Test
    public void testGetCurrentFilterReturnsLatestSnapshot() {
        TestDynamicFilter qdFilter = new TestDynamicFilter();
        DXFeedFilter filter = createFilter(qdFilter);
        DXFeedFilterTracker tracker = filter.getTracker();

        // Need a listener for tracker to subscribe to QDFilter updates
        tracker.addListener(t -> {});

        DXFeedFilter beforeUpdate = tracker.getCurrentFilter();
        assertFalse(beforeUpdate.accept(Trade.class, SYMBOL));

        qdFilter.addSymbol(SYMBOL);

        DXFeedFilter afterUpdate = tracker.getCurrentFilter();
        assertNotSame(beforeUpdate, afterUpdate);
        assertTrue(afterUpdate.accept(Trade.class, SYMBOL));
    }

    @Test
    public void testMultipleUpdatesProduceDistinctSnapshots() {
        TestDynamicFilter qdFilter = new TestDynamicFilter();
        DXFeedFilter filter = createFilter(qdFilter);
        DXFeedFilterTracker tracker = filter.getTracker();

        tracker.addListener(t -> {});

        qdFilter.addSymbol(SYMBOL);
        DXFeedFilter filter1 = tracker.getCurrentFilter();

        qdFilter.addSymbol("GOOG");
        DXFeedFilter filter2 = tracker.getCurrentFilter();

        assertNotSame(filter, filter1);
        assertNotSame(filter1, filter2);
    }

    @Test
    public void testListenerCalledOnEachUpdate() {
        TestDynamicFilter qdFilter = new TestDynamicFilter();
        DXFeedFilter filter = createFilter(qdFilter);
        DXFeedFilterTracker tracker = filter.getTracker();

        AtomicInteger count = new AtomicInteger();
        tracker.addListener(t -> count.incrementAndGet());

        qdFilter.addSymbol(SYMBOL);
        qdFilter.addSymbol("GOOG");
        qdFilter.addSymbol("MSFT");

        assertEquals(3, count.get());
    }

    // ---- Listener removal ----

    @Test
    public void testRemovedListenerNotNotified() {
        TestDynamicFilter qdFilter = new TestDynamicFilter(SYMBOL);
        DXFeedFilter filter = createFilter(qdFilter);
        DXFeedFilterTracker tracker = filter.getTracker();

        AtomicInteger count = new AtomicInteger();
        com.dxfeed.api.DXFeedFilterListener listener = t -> count.incrementAndGet();
        tracker.addListener(listener);
        tracker.removeListener(listener);

        qdFilter.addSymbol("GOOG");

        assertEquals(0, count.get());
    }

    @Test
    public void testStaticFilterListenerNeverNotified() {
        DXFeedFilter filter = createFilter(new TestStaticFilter(SYMBOL));
        DXFeedFilterTracker tracker = filter.getTracker();

        AtomicInteger count = new AtomicInteger();
        tracker.addListener(t -> count.incrementAndGet());

        // No way to trigger updates on a static filter — just verify listener was silently ignored
        assertEquals(0, count.get());
    }

    // ---- Tracker identity ----

    @Test
    public void testTrackerSharedAcrossSnapshots() {
        TestDynamicFilter qdFilter = new TestDynamicFilter();
        DXFeedFilter filter = createFilter(qdFilter);
        DXFeedFilterTracker tracker = filter.getTracker();

        tracker.addListener(t -> {});
        qdFilter.addSymbol(SYMBOL);

        DXFeedFilter updated = tracker.getCurrentFilter();
        assertNotSame(filter, updated);
        assertSame(tracker, updated.getTracker());
    }

    // ---- Multiple categories with dynamic filter ----

    @Test
    public void testDynamicFilterWithMultipleCategories() {
        TestDynamicFilter dynamicFilter = new TestDynamicFilter();
        QDFilter staticQdFilter = new TestStaticFilter("GOOG");

        DXFeedFilterImpl filter = new DXFeedFilterImpl(
            endpoint,
            new String[]{"dynamic", "static"},
            new QDFilter[]{dynamicFilter, staticQdFilter}
        );
        DXFeedFilterTracker tracker = filter.getTracker();

        assertTrue(filter.isDynamic());
        // Static category matches GOOG
        assertEquals("static", filter.getCategory(Trade.class, "GOOG"));
        // Dynamic category does not match anything yet
        assertFalse(filter.accept(Trade.class, SYMBOL));

        tracker.addListener(t -> {});
        dynamicFilter.addSymbol(SYMBOL);

        DXFeedFilter updated = tracker.getCurrentFilter();
        assertEquals("dynamic", updated.getCategory(Trade.class, SYMBOL));
        assertEquals("static", updated.getCategory(Trade.class, "GOOG"));
    }

    // ---- Helpers ----

    private DXFeedFilterImpl createFilter(QDFilter qdFilter) {
        return new DXFeedFilterImpl(
            endpoint,
            new String[]{"category"},
            new QDFilter[]{qdFilter}
        );
    }

    private static class TestStaticFilter extends QDFilter {
        private final Set<String> symbols = new HashSet<>();

        TestStaticFilter(String... symbols) {
            super(DXFeedScheme.getInstance());
            setName("static");
            for (String s : symbols) {
                this.symbols.add(s);
            }
        }

        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            return symbols.contains(record.getScheme().getCodec().decode(cipher, symbol));
        }
    }

    private static class TestDynamicFilter extends QDFilter {
        private Set<String> symbols = new HashSet<>();

        TestDynamicFilter(String... initialSymbols) {
            super(DXFeedScheme.getInstance());
            setName("dynamic");
            for (String s : initialSymbols) {
                symbols.add(s);
            }
        }

        private TestDynamicFilter(TestDynamicFilter source) {
            super(DXFeedScheme.getInstance(), source);
            setName("dynamic");
            this.symbols = new HashSet<>(source.symbols);
        }

        @Override
        public boolean accept(QDContract contract, DataRecord record, int cipher, String symbol) {
            return symbols.contains(record.getScheme().getCodec().decode(cipher, symbol));
        }

        @Override
        public boolean isDynamic() {
            return true;
        }

        /**
         * Adds a symbol and fires the update on the current filter in the chain.
         * Must be called on the original instance — it resolves the current filter via
         * {@code getUpdated()} so that chained updates work correctly
         * ({@link QDFilter#fireFilterUpdated} is a no-op once a filter has already fired).
         */
        void addSymbol(String symbol) {
            TestDynamicFilter current = (TestDynamicFilter) getUpdated().getFilter();
            TestDynamicFilter next = new TestDynamicFilter(current);
            next.symbols.add(symbol);
            current.fireFilterUpdated(next);
        }

        void removeSymbol(String symbol) {
            TestDynamicFilter current = (TestDynamicFilter) getUpdated().getFilter();
            TestDynamicFilter next = new TestDynamicFilter(current);
            next.symbols.remove(symbol);
            current.fireFilterUpdated(next);
        }
    }
}
