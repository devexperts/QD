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
package com.dxfeed.sample.api;

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedFilter;
import com.dxfeed.api.DXFeedFilterListener;
import com.dxfeed.api.DXFeedFilterTracker;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Trade;

/**
 * Demonstrates the DXFeed Filter API for subscription classification and pre-validation.
 *
 * <p>This sample shows how to:
 * <ul>
 *     <li>Create a {@link DXFeedFilter} to accept/reject subscriptions by symbol pattern</li>
 *     <li>Use {@link DXFeedFilter} as a classifier to categorize subscriptions into named groups</li>
 *     <li>Inspect the {@link DXFeedFilterTracker} surface that backs dynamic filter updates</li>
 * </ul>
 */
public class DXFeedFilterSample {

    public static void main(String[] args) {
        DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.LOCAL_HUB);

        try {
            demonstrateFilter(endpoint);
            demonstrateClassifier(endpoint);
            demonstrateTracker(endpoint);
        } finally {
            endpoint.close();
        }
    }

    private static void demonstrateFilter(DXEndpoint endpoint) {
        System.out.println("=== DXFeedFilter Demo ===");

        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("a-symbols", "A*")
            .build();

        // Test subscription acceptance
        System.out.println("AAPL accepted: " + filter.accept(Trade.class, "AAPL"));   // true
        System.out.println("AMZN accepted: " + filter.accept(Quote.class, "AMZN"));   // true
        System.out.println("GOOG accepted: " + filter.accept(Trade.class, "GOOG"));   // false

        // Test event acceptance
        System.out.println("Trade(AAPL) accepted: " + filter.acceptEvent(new Trade("AAPL")));     // true
        System.out.println("Trade(GOOG) accepted: " + filter.acceptEvent(new Trade("GOOG")));     // false

        // Classify with named category
        System.out.println("AAPL classify: " + filter.getCategory(Trade.class, "AAPL")); // "a-symbols"
        System.out.println("GOOG classify: " + filter.getCategory(Trade.class, "GOOG")); // null
        System.out.println();
    }

    private static void demonstrateClassifier(DXEndpoint endpoint) {
        System.out.println("=== DXFeedFilter Classifier Demo ===");

        DXFeedFilter classifier = DXFeedFilter.newBuilder(endpoint)
            .withCategory("tech-A", "A*")
            .withCategory("tech-G", "G*")
            .withCategory("other", "*")
            .build();

        // Classify subscriptions
        System.out.println("AAPL -> " + classifier.getCategory(Quote.class, "AAPL"));  // tech-A
        System.out.println("GOOG -> " + classifier.getCategory(Quote.class, "GOOG"));  // tech-G
        System.out.println("MSFT -> " + classifier.getCategory(Quote.class, "MSFT"));  // other

        // Classify events
        System.out.println("Trade(AMZN) -> " + classifier.getEventCategory(new Trade("AMZN")));    // tech-A
        System.out.println("Trade(IBM) -> " + classifier.getEventCategory(new Trade("IBM")));       // other

        // Accept checks against any category
        System.out.println("AAPL accepted: " + classifier.accept(Quote.class, "AAPL")); // true
        System.out.println();
    }

    private static void demonstrateTracker(DXEndpoint endpoint) {
        System.out.println("=== DXFeedFilter Tracker Demo ===");

        DXFeedFilter filter = DXFeedFilter.newBuilder(endpoint)
            .withCategory("a-symbols", "A*")
            .build();

        // The tracker is a stable reference: same instance on repeated calls.
        DXFeedFilterTracker tracker = filter.getTracker();
        System.out.println("getTracker() identity-stable: " + (tracker == filter.getTracker())); // true

        // Initially the tracker exposes the same filter snapshot we just built.
        System.out.println("getCurrentFilter() == filter: " + (tracker.getCurrentFilter() == filter)); // true

        // For a static filter the listener never fires — see DXFeedLiveIpfSample for dynamic filters.
        // NOTE: Always consult with getCurrentFilter after adding a listener
        // (an update that happened before adding the listener can be missed).
        // Updating a current filter voluntarily and from a listener should always be
        // synchronized to avoid racy updates.
        DXFeedFilterListener listener = t -> System.out.println("  filter updated");
        tracker.addListener(listener);
        tracker.removeListener(listener);
        System.out.println("listener add/remove completed on static filter");
        System.out.println();
    }
}
