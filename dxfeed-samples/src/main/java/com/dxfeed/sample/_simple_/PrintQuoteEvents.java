/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.sample._simple_;

import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.Quote;

/**
 * Subscribes to Quote events for a specified symbol and prints them until terminated.
 */
public class PrintQuoteEvents {
    public static void main(String[] args) throws InterruptedException {
        String symbol = args[0];
        // Use default DXFeed instance for that data feed address is defined by dxfeed.properties file
        DXFeedSubscription<Quote> sub = DXFeed.getInstance().createSubscription(Quote.class);
        sub.addEventListener(events -> {
            for (Quote quote : events)
                System.out.println(quote);
        });
        sub.addSymbols(symbol);
        Thread.sleep(Long.MAX_VALUE);
    }
}
