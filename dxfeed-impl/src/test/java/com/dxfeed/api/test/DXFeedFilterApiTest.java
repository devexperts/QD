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
package com.dxfeed.api.test;

import com.devexperts.qd.kit.FilterSyntaxException;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedFilter;
import com.dxfeed.api.DXFeedFilterListener;
import com.dxfeed.api.DXFeedFilterTracker;
import com.dxfeed.api.osub.IndexedEventSubscriptionSymbol;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderSource;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.event.market.Trade;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DXFeedFilterApiTest {

    private static final String SYMBOL_A = "AAPL";
    private static final String SYMBOL_B = "BIDU";
    private static final String SYMBOL_C = "CSCO";

    private DXEndpoint endpoint;

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
        endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);
    }

    @After
    public void tearDown() throws Exception {
        endpoint.close();
        ThreadCleanCheck.after();
    }

    // ---- accept tests ----

    @Test
    public void testFilterAcceptsMatchingSubscription() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        assertTrue(filter.accept(Trade.class, SYMBOL_A));
        assertTrue(filter.accept(Quote.class, SYMBOL_A));
    }

    @Test
    public void testFilterRejectsNonMatchingSubscription() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        assertFalse(filter.accept(Trade.class, SYMBOL_B));
        assertFalse(filter.accept(Quote.class, SYMBOL_C));
    }

    @Test
    public void testFilterNegation() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", "!" + SYMBOL_A)
            .build();
        assertFalse(filter.accept(Trade.class, SYMBOL_A));
        assertTrue(filter.accept(Trade.class, SYMBOL_B));
    }

    @Test
    public void testFilterAcceptEvent() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        assertTrue(filter.acceptEvent(new Trade(SYMBOL_A)));
        assertFalse(filter.acceptEvent(new Trade(SYMBOL_B)));
    }

    @Test
    public void testFilterAcceptEventQuote() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        assertTrue(filter.acceptEvent(new Quote(SYMBOL_A)));
        assertFalse(filter.acceptEvent(new Quote(SYMBOL_B)));
    }

    @Test
    public void testFilterWildcard() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A + "*")
            .build();
        assertTrue(filter.accept(Trade.class, SYMBOL_A));
        assertTrue(filter.accept(Trade.class, SYMBOL_A + "X"));
        assertFalse(filter.accept(Trade.class, SYMBOL_B));
    }

    // ---- classify tests (single category) ----

    @Test
    public void testFilterClassifyReturnsCategory() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        assertEquals("filter", filter.getCategory(Trade.class, SYMBOL_A));
        assertNull(filter.getCategory(Trade.class, SYMBOL_B));
    }

    @Test
    public void testFilterClassifyEvent() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        assertEquals("filter", filter.getEventCategory(new Trade(SYMBOL_A)));
        assertNull(filter.getEventCategory(new Trade(SYMBOL_B)));
    }

    // ---- classify tests (multiple categories) ----

    @Test
    public void testGetCategoryMultipleCategories() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("groupA", SYMBOL_A)
            .withCategory("groupB", SYMBOL_B)
            .withCategory("groupC", SYMBOL_C)
            .build();

        assertEquals("groupA", filter.getCategory(Trade.class, SYMBOL_A));
        assertEquals("groupB", filter.getCategory(Trade.class, SYMBOL_B));
        assertEquals("groupC", filter.getCategory(Trade.class, SYMBOL_C));
    }

    @Test
    public void testGetCategoryReturnsNullForNoMatch() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("groupA", SYMBOL_A)
            .build();
        assertNull(filter.getCategory(Trade.class, "UNKNOWN"));
    }

    @Test
    public void testGetCategoryFirstMatchWins() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("first", SYMBOL_A)
            .withCategory("second", SYMBOL_A)
            .build();
        assertEquals("first", filter.getCategory(Trade.class, SYMBOL_A));
    }

    @Test
    public void testGetCategoryWithWildcardCategories() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("A-group", "A*")
            .withCategory("B-group", "B*")
            .withCategory("other", "*")
            .build();

        assertEquals("A-group", filter.getCategory(Quote.class, SYMBOL_A));
        assertEquals("B-group", filter.getCategory(Quote.class, SYMBOL_B));
        assertEquals("other", filter.getCategory(Quote.class, SYMBOL_C));
    }

    @Test
    public void testGetEventCategory() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("groupA", SYMBOL_A)
            .withCategory("groupB", SYMBOL_B)
            .build();

        assertEquals("groupA", filter.getEventCategory(new Trade(SYMBOL_A)));
        assertEquals("groupB", filter.getEventCategory(new Trade(SYMBOL_B)));
        assertNull(filter.getEventCategory(new Trade("UNKNOWN")));
    }

    @Test
    public void testGetCategoryAcceptMatchesAnyCategory() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("groupA", SYMBOL_A)
            .withCategory("groupB", SYMBOL_B)
            .build();

        assertTrue(filter.accept(Trade.class, SYMBOL_A));
        assertTrue(filter.accept(Trade.class, SYMBOL_B));
        assertFalse(filter.accept(Trade.class, SYMBOL_C));
    }

    @Test
    public void testGetCategoryWithCategoriesMap() {
        Map<String, String> categories = new LinkedHashMap<>();
        categories.put("A-group", "A*");
        categories.put("B-group", "B*");
        categories.put("other", "*");

        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategories(categories)
            .build();

        assertEquals("A-group", filter.getCategory(Quote.class, SYMBOL_A));
        assertEquals("B-group", filter.getCategory(Quote.class, SYMBOL_B));
        assertEquals("other", filter.getCategory(Quote.class, SYMBOL_C));
    }

    // ---- Regional symbols ----

    @Test
    public void testFilterWithRegionalSymbol() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A + "*")
            .build();
        assertTrue(filter.accept(Quote.class, SYMBOL_A + "&Q"));
        assertFalse(filter.accept(Quote.class, SYMBOL_B + "&Q"));
    }

    // ---- Order with COMPOSITE / REGIONAL source ----
    // Order with COMPOSITE or REGIONAL source is subscribed via IndexedEventSubscriptionSymbol.
    // Internally these sources are synthetic — generated from Quote records — so the filter must
    // resolve the corresponding QD delegates for the given source id.

    @Test
    public void testFilterAcceptsOrderWithCompositeSource() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        IndexedEventSubscriptionSymbol<String> iesA =
            new IndexedEventSubscriptionSymbol<>(SYMBOL_A, OrderSource.COMPOSITE);
        IndexedEventSubscriptionSymbol<String> iesB =
            new IndexedEventSubscriptionSymbol<>(SYMBOL_B, OrderSource.COMPOSITE);
        assertTrue(filter.accept(Order.class, iesA));
        assertFalse(filter.accept(Order.class, iesB));
    }

    @Test
    public void testFilterAcceptsOrderWithRegionalSource() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        IndexedEventSubscriptionSymbol<String> iesA =
            new IndexedEventSubscriptionSymbol<>(SYMBOL_A, OrderSource.REGIONAL);
        IndexedEventSubscriptionSymbol<String> iesB =
            new IndexedEventSubscriptionSymbol<>(SYMBOL_B, OrderSource.REGIONAL);
        assertTrue(filter.accept(Order.class, iesA));
        assertFalse(filter.accept(Order.class, iesB));
    }

    @Test
    public void testFilterClassifiesOrderWithCompositeSource() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("groupA", SYMBOL_A)
            .withCategory("groupB", SYMBOL_B)
            .build();

        IndexedEventSubscriptionSymbol<String> iesA =
            new IndexedEventSubscriptionSymbol<>(SYMBOL_A, OrderSource.COMPOSITE);
        IndexedEventSubscriptionSymbol<String> iesB =
            new IndexedEventSubscriptionSymbol<>(SYMBOL_B, OrderSource.COMPOSITE);

        assertEquals("groupA", filter.getCategory(Order.class, iesA));
        assertEquals("groupB", filter.getCategory(Order.class, iesB));

        assertNull(filter.getCategory(Order.class,
            new IndexedEventSubscriptionSymbol<>(SYMBOL_C, OrderSource.COMPOSITE)));
    }

    @Test
    public void testFilterClassifiesOrderWithRegionalSource() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("groupA", SYMBOL_A)
            .withCategory("groupB", SYMBOL_B)
            .build();
        IndexedEventSubscriptionSymbol<String> iesA =
            new IndexedEventSubscriptionSymbol<>(SYMBOL_A, OrderSource.REGIONAL);
        IndexedEventSubscriptionSymbol<String> iesB =
            new IndexedEventSubscriptionSymbol<>(SYMBOL_B, OrderSource.REGIONAL);
        assertEquals("groupA", filter.getCategory(Order.class, iesA));
        assertEquals("groupB", filter.getCategory(Order.class, iesB));
        assertNull(filter.getCategory(Order.class,
            new IndexedEventSubscriptionSymbol<>(SYMBOL_C, OrderSource.REGIONAL)));
    }

    // ---- TimeSeriesSubscriptionSymbol ----
    // TimeSeriesSubscriptionSymbol extends IndexedEventSubscriptionSymbol, so it is handled by the
    // existing IES branch in firstMatchingSubscription: the inner event symbol is unwrapped via
    // ies.getEventSymbol() before convertSymbol(), and source id is IndexedEventSource.DEFAULT (0).

    @Test
    public void testFilterAcceptsTimeSeriesSubscriptionSymbol() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        TimeSeriesSubscriptionSymbol<String> tssA = new TimeSeriesSubscriptionSymbol<>(SYMBOL_A, 0);
        TimeSeriesSubscriptionSymbol<String> tssB = new TimeSeriesSubscriptionSymbol<>(SYMBOL_B, 0);
        assertTrue(filter.accept(TimeAndSale.class, tssA));
        assertFalse(filter.accept(TimeAndSale.class, tssB));
    }

    @Test
    public void testFilterAcceptsCandleTimeSeriesSubscriptionSymbol() {
        // Realistic time-series Candle subscription: CandleSymbol like "AAPL{=d}" wrapped in a
        // TimeSeriesSubscriptionSymbol. CandleEventDelegateSet.convertSymbol throws for any class
        // other than CandleSymbol/String/WildcardSymbol, so the filter must unwrap the TSS before
        // calling convertSymbol — which it does via the IES branch.
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A + "*")
            .build();
        TimeSeriesSubscriptionSymbol<CandleSymbol> tssA =
            new TimeSeriesSubscriptionSymbol<>(CandleSymbol.valueOf(SYMBOL_A + "{=d}"), 0);
        TimeSeriesSubscriptionSymbol<CandleSymbol> tssB =
            new TimeSeriesSubscriptionSymbol<>(CandleSymbol.valueOf(SYMBOL_B + "{=d}"), 0);
        assertTrue(filter.accept(Candle.class, tssA));
        assertFalse(filter.accept(Candle.class, tssB));
    }

    // ---- Builder validation ----

    @Test
    public void testBuilderRequiresEndpoint() {
        assertThrows(NullPointerException.class, () -> DXFeedFilter.newBuilder(null));
    }

    @Test
    public void testBuilderRequiresCategories() {
        DXFeedFilter.Builder builder = DXFeedFilter.newBuilder(endpoint);
        assertThrows(IllegalArgumentException.class, () -> builder.build());
    }

    @Test
    public void testBuilderRejectsDuplicateCategory() {
        DXFeedFilter.Builder builder = DXFeedFilter.newBuilder(endpoint).withCategory("dup", SYMBOL_A);
        assertThrows(IllegalArgumentException.class, () -> builder.withCategory("dup", SYMBOL_B));
    }

    @Test
    public void testBuilderRejectsNullCategoryName() {
        DXFeedFilter.Builder builder = DXFeedFilter.newBuilder(endpoint);
        assertThrows(NullPointerException.class, () -> builder.withCategory(null, SYMBOL_A));
    }

    @Test
    public void testBuilderRejectsNullFilterExpression() {
        DXFeedFilter.Builder builder = DXFeedFilter.newBuilder(endpoint);
        assertThrows(NullPointerException.class, () -> builder.withCategory("name", null));
    }

    @Test
    public void testBuilderRejectsEmptyCategoryName() {
        DXFeedFilter.Builder builder = DXFeedFilter.newBuilder(endpoint);
        assertThrows(IllegalArgumentException.class, () -> builder.withCategory("", SYMBOL_A));
    }

    @Test
    public void testBuilderRejectsEmptyFilterExpression() {
        DXFeedFilter.Builder builder = DXFeedFilter.newBuilder(endpoint);
        assertThrows(IllegalArgumentException.class, () -> builder.withCategory("name", ""));
    }

    @Test
    public void testBuilderRejectsBadFilterExpression() {
        DXFeedFilter.Builder builder = DXFeedFilter.newBuilder(endpoint).withCategory("bad", "((unbalanced");
        assertThrows(FilterSyntaxException.class, () -> builder.build());
    }

    @Test
    public void testWithCategoriesPartialFailureLeavesBuilderUsable() {
        DXFeedFilter.Builder builder = DXFeedFilter.newBuilder(endpoint)
            .withCategory("good", SYMBOL_A);

        Map<String, String> moreCategories = new LinkedHashMap<>();
        moreCategories.put("extra", SYMBOL_B);
        moreCategories.put("good", SYMBOL_C); // duplicate of already-added

        try {
            builder.withCategories(moreCategories);
            fail("expected IllegalArgumentException for duplicate category name");
        } catch (IllegalArgumentException expected) {
            // ok
        }

        // "extra" was added before the collision; the builder remains usable.
        DXFeedFilter filter = builder.build();
        assertEquals("good", filter.getCategory(Trade.class, SYMBOL_A));
        assertEquals("extra", filter.getCategory(Trade.class, SYMBOL_B));
        assertNull(filter.getCategory(Trade.class, SYMBOL_C));
    }

    // ---- Combination of multiple categories ----

    @Test
    public void testMultipleCategoriesAcceptsEither() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .withCategory("stocks", SYMBOL_B)
            .build();

        assertTrue(filter.accept(Trade.class, SYMBOL_A));
        assertTrue(filter.accept(Trade.class, SYMBOL_B));
        assertFalse(filter.accept(Trade.class, SYMBOL_C));
    }

    @Test
    public void testMultipleCategoriesClassifies() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .withCategory("stocks", SYMBOL_B)
            .build();

        assertEquals("filter", filter.getCategory(Trade.class, SYMBOL_A));
        assertEquals("stocks", filter.getCategory(Trade.class, SYMBOL_B));
        assertNull(filter.getCategory(Trade.class, SYMBOL_C));
    }

    @Test
    public void testMultipleCategoriesClassifiesInOrder() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("stocks", SYMBOL_A)
            .withCategory("filter", SYMBOL_B)
            .build();

        assertEquals("stocks", filter.getCategory(Trade.class, SYMBOL_A));
        assertEquals("filter", filter.getCategory(Trade.class, SYMBOL_B));
    }

    // ---- Tracker tests ----

    @Test
    public void testGetTrackerNotNull() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        assertNotNull(filter.getTracker());
    }

    @Test
    public void testTrackerReturnsSameInstance() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        assertSame(filter.getTracker(), filter.getTracker());
    }

    @Test
    public void testTrackerCurrentFilterIsInitialFilter() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        DXFeedFilterTracker tracker = filter.getTracker();
        assertSame(filter, tracker.getCurrentFilter());
    }

    @Test
    public void testTrackerSharedAcrossVersions() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        DXFeedFilterTracker tracker = filter.getTracker();
        DXFeedFilter current = tracker.getCurrentFilter();
        assertSame(tracker, current.getTracker());
    }

    @Test
    public void testStaticFilterIsNotDynamic() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        assertFalse(filter.isDynamic());
    }

    @Test
    public void testStaticFilterListenerNeverFires() {
        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("filter", SYMBOL_A)
            .build();
        DXFeedFilterTracker tracker = filter.getTracker();

        // Adding/removing a listener on a non-dynamic filter should not throw
        DXFeedFilterListener listener = t -> {
            throw new AssertionError("Listener should never fire for static filters");
        };
        tracker.addListener(listener);
        tracker.removeListener(listener);

        // Filter should still be the same
        assertSame(filter, tracker.getCurrentFilter());
    }
}
