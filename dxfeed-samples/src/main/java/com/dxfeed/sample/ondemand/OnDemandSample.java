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
package com.dxfeed.sample.ondemand;

import com.devexperts.util.TimeUtil;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.Quote;
import com.dxfeed.ondemand.OnDemandService;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class OnDemandSample {
    public static void main(String[] args) throws ParseException, InterruptedException {
        // get on-demand-only data feed
        OnDemandService onDemand = OnDemandService.getInstance();
        DXFeed feed = onDemand.getEndpoint().getFeed();

        // prepare time format
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS 'EST'");
        fmt.setTimeZone(TimeUtil.getTimeZone("America/New_York"));

        // subscribe to Accenture symbol ACN to print its quotes
        DXFeedSubscription<Quote> sub = feed.createSubscription(Quote.class);
        sub.addEventListener(events -> {
            for (Quote quote : events) {
                System.out.println(fmt.format(new Date(quote.getEventTime())) + " : " + quote.getEventSymbol() +
                    " bid " + quote.getBidPrice() + " /" +
                    " ask " + quote.getAskPrice());
            }
        });
        sub.addSymbols("ACN");

        // Watch Accenture drop under $1 on May 6, 2010 "Flashcrash" from 14:47:48 to 14:48:02 EST
        Date from = fmt.parse("2010-05-06 14:47:48.000 EST");
        Date to = fmt.parse("2010-05-06 14:48:02.000 EST");

        // switch into historical on-demand data replay mode
        onDemand.replay(from);

        // replaying events until end time reached
        while (onDemand.getTime().getTime() < to.getTime())              {
            System.out.println("Current state is " + onDemand.getEndpoint().getState() + "," +
                " on-demand time is " + fmt.format(onDemand.getTime()));
            Thread.sleep(1000);
        }

        // close endpoint completely to release resources and shutdown JVM
        onDemand.getEndpoint().closeAndAwaitTermination();
    }
}
