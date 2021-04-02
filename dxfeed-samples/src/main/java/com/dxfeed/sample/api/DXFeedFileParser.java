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
package com.dxfeed.sample.api;

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedEventListener;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.MarketEvent;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DXFeedFileParser {
    public static void main(String[] args) throws InterruptedException {
        if (args.length != 3) {
            String eventTypeNames = getEventTypeNames();
            System.err.println("usage: DXFeedFileParser <file> <type> <symbol>");
            System.err.println("where: <file>    is a file name");
            System.err.println("       <type>    is a dxfeed event type (" + eventTypeNames + ")");
            System.err.println("       <symbol>  is a security symbols to get events for (e.g. \"IBM\", \"C\", etc)");
            return;
        }
        String argFile = args[0];
        String argType = args[1];
        String argSymbol = args[2];

        // create endpoint specifically for file parsing
        DXEndpoint endpoint = DXEndpoint.create(DXEndpoint.Role.STREAM_FEED);
        DXFeed feed = endpoint.getFeed();

        // subscribe to a specified event and symbol
        DXFeedSubscription<Object> sub = feed.createSubscription(findEventType(argType));
        sub.addEventListener(new PrintListener());
        sub.addSymbols(argSymbol);

        // connect endpoint to a file
        endpoint.connect("file:" + argFile + "[speed=max]");

        // wait until file is completely parsed
        endpoint.awaitNotConnected();

        // close endpoint when we're done
        // this method will gracefully close endpoint, waiting while data processing completes
        endpoint.closeAndAwaitTermination();
    }

    private static class PrintListener implements DXFeedEventListener<Object> {
        private int eventCounter;

        @Override
        public void eventsReceived(List<Object> events) {
            for (Object event : events)
                System.out.println((++eventCounter) + ": " + event);
        }
    }

    // ---- Utility methods to make this sample generic for use with any event type as specified on command line ----

    private static Class<?> findEventType(String type) {
        Class<?> result = getEventTypesMap().get(type);
        if (result == null)
            throw new IllegalArgumentException("Cannot find type '" + type + "'");
        return result;
    }

    private static Map<String, Class<?>> getEventTypesMap() {
        TreeMap<String, Class<?>> result = new TreeMap<>();
        for (Class<?> eventType : DXEndpoint.getInstance(DXEndpoint.Role.STREAM_FEED).getEventTypes())
            if (MarketEvent.class.isAssignableFrom(eventType))
                result.put(eventType.getSimpleName(), eventType);
        return result;
    }

    private static String getEventTypeNames() {
        StringBuilder sb = new StringBuilder();
        for (String s : getEventTypesMap().keySet()) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }
}
