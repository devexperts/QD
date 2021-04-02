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
package com.dxfeed.sample.ipf.option;

import com.dxfeed.api.DXFeed;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Trade;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;
import com.dxfeed.ipf.option.OptionChain;
import com.dxfeed.ipf.option.OptionChainsBuilder;
import com.dxfeed.ipf.option.OptionSeries;
import com.dxfeed.promise.Promise;
import com.dxfeed.promise.Promises;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class DXFeedOptionChain {
    public static void main(String[] args) throws IOException {
        if (args.length != 4) {
            System.err.println("usage: DXFeedOptionChain <ipf-file> <symbol> <n> <k>");
            System.err.println("       <ipf-file> is name of instrument profiles file");
            System.err.println("       <symbol>   is the product or underlying symbol");
            System.err.println("       <nStrikes> number of strikes to print for each series");
            System.err.println("       <nMonths>  number of months to print");
            return;
        }

        String argIpfFile = args[0];
        String argSymbol = args[1];
        int nStrikes = Integer.parseInt(args[2]);
        int nMonths = Integer.parseInt(args[3]);

        DXFeed feed = DXFeed.getInstance();

        // subscribe to trade to learn instrument last price
        System.out.printf("Waiting for price of %s ...%n", argSymbol);
        Trade trade = feed.getLastEventPromise(Trade.class, argSymbol).await(1, TimeUnit.SECONDS);

        double price = trade.getPrice();
        System.out.printf(Locale.US, "Price of %s is %f%n", argSymbol, price);

        System.out.printf("Reading instruments from %s ...%n", argIpfFile);
        List<InstrumentProfile> instruments = new InstrumentProfileReader().readFromFile(argIpfFile);

        System.out.printf("Building option chains ...%n");
        Map<String,OptionChain<InstrumentProfile>> chains = OptionChainsBuilder.build(instruments).getChains();
        OptionChain<InstrumentProfile> chain = chains.get(argSymbol);
        nMonths = Math.min(nMonths, chain.getSeries().size());
        List<OptionSeries<InstrumentProfile>> seriesList =
            new ArrayList<>(chain.getSeries()).subList(0, nMonths);

        System.out.printf("Requesting option quotes ...%n");
        Map<InstrumentProfile, Promise<Quote>> quotes = new HashMap<>();
        for (OptionSeries<InstrumentProfile> series : seriesList) {
            List<Double> strikes = series.getNStrikesAround(nStrikes, price);
            for (Double strike : strikes) {
                InstrumentProfile call = series.getCalls().get(strike);
                InstrumentProfile put = series.getPuts().get(strike);
                if (call != null)
                    quotes.put(call, feed.getLastEventPromise(Quote.class, call.getSymbol()));
                if (put != null)
                    quotes.put(put, feed.getLastEventPromise(Quote.class, put.getSymbol()));
            }
        }
        // ignore timeout and continue to print retrieved quotes even on timeout
        Promises.allOf(quotes.values()).awaitWithoutException(1, TimeUnit.SECONDS);

        System.out.printf("Printing option series ...%n");
        for (OptionSeries<InstrumentProfile> series : seriesList) {
            System.out.printf("Option series %s%n", series);
            List<Double> strikes = series.getNStrikesAround(nStrikes, price);
            System.out.printf("    %10s %10s %10s %10s %10s%n", "C.BID", "C.ASK", "STRIKE", "P.BID", "P.ASK");
            for (Double strike : strikes) {
                InstrumentProfile call = series.getCalls().get(strike);
                InstrumentProfile put = series.getPuts().get(strike);
                Quote callQuote = call == null ? null : quotes.get(call).getResult();
                Quote putQuote = put == null ? null : quotes.get(put).getResult();
                if (callQuote == null)
                    callQuote = new Quote();
                if (putQuote == null)
                    putQuote = new Quote();
                System.out.printf(Locale.US, "    %10.3f %10.3f %10.3f %10.3f %10.3f%n",
                    callQuote.getBidPrice(), callQuote.getAskPrice(), strike,
                    putQuote.getBidPrice(), putQuote.getAskPrice());
            }
        }

        System.exit(0); // shutdown JVM when done
    }
}
