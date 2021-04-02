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
package com.dxfeed.sample.console;

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.event.market.MarketEventSymbols;
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Summary;
import com.dxfeed.event.market.Trade;
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.Promises;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * This sample demonstrates a way to subscribe to the big world of symbols with dxFeed API, so that the events are
 * updated and cached in memory of this process, and then take snapshots of those events from memory whenever
 * they are needed. This example repeatedly reads symbol name from the console and prints a snapshot of its last
 * quote, trade, summary, and profile events.
 */
public class LastEventsConsole {
    public static void main(String[] args) throws IOException {
        /*
         * Permanent subscription to the world is performed with a special property named "dxfeed.qd.subscribe.ticker".
         * Its value consists of a comma-separated list of records, followed by a space, followed by a comma-separated
         * list of symbols. Record names for composite (NBBO) events are the same as the corresponding event classes
         * in API. The string below defines subscription for quote, trade, summary, and profile composite events:
         */
        String records = "Quote,Trade,Summary,Profile";

        /*
         * Records for regional-exchange events are derived by appending "&" (ampersand) and the a single-digit
         * exchange code. Regexp-like syntax can be used instead of listing them all. For example, the commented
         * line below and to the mix a subscription on regional quotes from all potential exchange codes A-Z
         */
        // String records = "Quote,Trade,Summary,Profile,Quote&[A-Z]";
        /*
         * There is an important trade-off between a resource consumption and speed of access to the last events.
         * The whole world of stocks and options from all the exchanges is very large and will consume gigabytes
         * of memory to cache. Moreover, this cache has to be constantly kept up-to-date which consumes a lot of
         * network and CPU.
         *
         * A particular application's uses cases has to be studied to figure out what is option for this particular
         * application. If some events are known be rarely needed and a small delay while access them can be
         * tolerated, then it is not worth configuring a permanent subscription for them. The code in this
         * sample works using DXFeed.getLastEventPromise method that will request event from upstream data provider
         * if it is not present in local in-memory cache.
         */

        /*
         * There are multiple ways to specify a list of symbols. It is typically taken from IPF file and its
         * specification consists of an URL to the file which has to contain ".ipf" in order to be recognized.
         * The string below defines subscription for all symbols that are available on the demo feed.
         */
        String symbols = "http://dxfeed.s3.amazonaws.com/masterdata/ipf/demo/mux-demo.ipf.zip";

        /*
         * Permanent subscription property "dxfeed.qd.subscribe.ticker" can be directly placed into the
         * "dxfeed.properties" file and no custom DXEndpoint instance will be needed. Here it is explicitly
         * specified using a DXFeedEndpoint.Builder class. Note, that we don't use "connect" method on DXEndpoint.
         * It is assumed by this sample that "dxfeed.address" property is specified in "dxfeed.properties" and
         * connection is automatically established to that address. To run this sample application without
         * "dxfeed.properties" file, specify connection address using JVM option. For example, use
         * "-Ddxfeed.address=demo.dxfeed.com:7300" to connect to the demo feed.
         */
        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withProperty("dxfeed.qd.subscribe.ticker", records + " " + symbols)
            .build();

        /*
         * The actual client code does not need a reference to DXEndpoint, which only contains lifecycle
         * methods like "connect" and "close". The client code needs a reference to DXFeed.
         */
        DXFeed feed = endpoint.getFeed();

        /*
         * Print a short help.
         */
        System.out.println("Type symbols to get their quote, trade, summary, and profile event snapshots");

        /*
         * The main loop of this sample loops forever reading symbols from console and printing events.
         */
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            /*
             * User of this sample application can type symbols on the console. Symbol like "IBM" corresponds
             * to the stock. Symbol like "IBM&N" corresponds to the information from a specific exchange.
             * See the dxFeed Symbol guide at http://www.dxfeed.com/downloads/documentation/dxFeed_Symbol_Guide.pdf
             */
            String symbol = in.readLine();

            /*
             * The first step is to extract promises for all events that we are interested in. This way we
             * can get an event even if we have not previously subscribed for it.
             */
            Promise<Quote> quotePromise = feed.getLastEventPromise(Quote.class, symbol);
            Promise<Trade> tradePromise = feed.getLastEventPromise(Trade.class, symbol);
            Promise<Summary> summaryPromise = feed.getLastEventPromise(Summary.class, symbol);

            /*
             * All promises are put into a list for convenience.
             */
            List<Promise<?>> promises = new ArrayList<>();
            promises.add(quotePromise);
            promises.add(tradePromise);
            promises.add(summaryPromise);

            /*
             * Profile events are composite-only. They are not available for regional symbols like
             * "IBM&N" and the attempt to retrieve never completes (will timeout), so we don't event try.
             */
            if (!MarketEventSymbols.hasExchangeCode(symbol)) {
                Promise<Profile> profilePromise = feed.getLastEventPromise(Profile.class, symbol);
                promises.add(profilePromise);
            }

            /*
             * If the events are available in the in-memory cache, then the promises will be completed immediately.
             * Otherwise, a request to the upstream data provider is sent. Below we combine promises using
             * Promises utility class from DXFeed API in order to wait for at most 1 second for all of the
             * promises to complete. The last event promise never completes exceptionally and we don't
             * have to specially process a case of timeout, so "awaitWithoutException" is used to continue
             * normal execution even on timeout. This sample prints a special message in the case of timeout.
             */
            if (!Promises.allOf(promises).awaitWithoutException(1, TimeUnit.SECONDS))
                System.out.println("Request timed out");

            /*
             * The combination above is used only to ensure a common wait of 1 second. Promises to individual events
             * are completed independently and the corresponding events can be accessed even if some events were not
             * available for any reason and the wait above had timed out. This sample just prints all results.
             * "null" is printed when the event is not available.
             */
            for (Promise<?> promise : promises)
                System.out.println(promise.getResult());
        }
    }
}
