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
package com.dxfeed.sample.ipf;

import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.MarketEvent;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;
import com.dxfeed.sample.api.DXFeedConnect;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DXFeedIpfConnect {

    public static void main(String[] args) throws InterruptedException, IOException {
        if (args.length != 2) {
            String eventTypeNames = DXFeedConnect.getEventTypeNames(MarketEvent.class);
            System.err.println("usage: DXFeedIpfConnect <type> <ipf-file>");
            System.err.println("where: <type>     is dxfeed event type (" + eventTypeNames + ")");
            System.err.println("       <ipf-file> is name of instrument profiles file");
            return;
        }
        String argType = args[0];
        String argIpfFile = args[1];

        Class<? extends MarketEvent> eventType = DXFeedConnect.findEventType(argType, MarketEvent.class);
        DXFeedSubscription<MarketEvent> sub = DXFeed.getInstance().createSubscription(eventType);
        sub.addEventListener(events -> {
            for (MarketEvent event : events)
                System.out.println(event.getEventSymbol() + ": " + event);
        });
        sub.addSymbols(getSymbols(argIpfFile));
        Thread.sleep(Long.MAX_VALUE);
    }

    private static List<String> getSymbols(String filename) throws IOException {
        System.out.printf("Reading instruments from %s ...%n", filename);
        List<InstrumentProfile> profiles = new InstrumentProfileReader().readFromFile(filename);
        ProfileFilter filter = profile -> {
            // This is just a sample, any arbitrary filtering may go here.
            return
                profile.getType().equals("STOCK") && // stocks
                profile.getSIC() / 10 == 357 && // Computer And Office Equipment
                profile.getExchanges().contains("XNYS"); // traded at NYSE
        };
        ArrayList<String> result = new ArrayList<>();
        System.out.println("Selected symbols are:");
        for (InstrumentProfile profile : profiles)
            if (filter.accept(profile)) {
                result.add(profile.getSymbol());
                System.out.println(profile.getSymbol() + " (" + profile.getDescription() + ")");
            }
        return result;
    }

    private static interface ProfileFilter {
        public boolean accept(InstrumentProfile profile);
    }
}
