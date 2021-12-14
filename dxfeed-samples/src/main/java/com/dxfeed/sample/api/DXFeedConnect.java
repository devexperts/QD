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

import com.devexperts.util.TimeFormat;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXFeedTimeSeriesSubscription;
import com.dxfeed.event.EventType;
import com.dxfeed.event.TimeSeriesEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class DXFeedConnect {
    public static void main(String[] args) {
        if (args.length < 2) {
            String eventTypeNames = getEventTypeNames(EventType.class);
            System.err.println("usage: DXFeedConnect <types> <symbols> [<time>]");
            System.err.println("where: <types>   is comma-separated list of dxfeed event type (" + eventTypeNames + ")");
            System.err.println("       <symbols> is comma-separated list of security symbols to get events for (e.g. \"IBM,C,SPX\")");
            System.err.println("                 for Candle event specify symbol with aggregation like in \"IBM{=15m}\"");
            System.err.println("       <time>    is a fromTime for time-series subscription");
            return;
        }
        String argTypes = args[0];
        String argSymbols = args[1];
        String argTime = args.length > 2 ? args[2] : null;

        String[] symbols = parseSymbols(argSymbols);
        try {
            for (String type : argTypes.split(",")) {
                if (argTime != null) {
                    connectTimeSeriesEvent(type, argTime, symbols);
                } else {
                    connectEvent(type, symbols);
                }
            }
            Thread.sleep(Long.MAX_VALUE);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1); // shutdown on any error
        }
    }

    private static String[] parseSymbols(String symbolList) {
        List<String> result = new ArrayList<>();
        int parentheses = 0; // # of encountered parentheses of any type
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < symbolList.length(); i++) {
            char ch = symbolList.charAt(i);
            switch (ch) {
                case '{': case '(': case '[':
                    parentheses++;
                    sb.append(ch);
                    break;
                case '}': case ')': case ']':
                    if (parentheses > 0)
                        parentheses--;
                    sb.append(ch);
                    break;
                case ',':
                    if (parentheses == 0) {
                        // not in parenthesis -- comma is a symbol list separator
                        result.add(sb.toString());
                        sb.setLength(0);
                    } else {
                        sb.append(ch);
                    }
                    break;
                default:
                    sb.append(ch);
            }
        }
        result.add(sb.toString());
        return result.toArray(new String[result.size()]);
    }

    private static void connectEvent(String type, String... symbols) {
        Class<?> eventType = findEventType(type, EventType.class);
        DXFeedSubscription<Object> sub = DXFeed.getInstance().createSubscription(eventType);
        sub.addEventListener(DXFeedConnect::printEvents);
        sub.addSymbols(symbols);
    }

    private static void connectTimeSeriesEvent(String type, String fromTime, String... symbols) {
        Class<TimeSeriesEvent<?>> eventType = findEventType(type, TimeSeriesEvent.class);
        long from = TimeFormat.DEFAULT.parse(fromTime).getTime();
        DXFeedTimeSeriesSubscription<TimeSeriesEvent<?>> sub =
            DXFeed.getInstance().createTimeSeriesSubscription(eventType);
        sub.addEventListener(DXFeedConnect::printEvents);
        sub.setFromTime(from);
        sub.addSymbols(symbols);
    }

    private static void printEvents(List<?> events) {
        for (Object event : events) {
            System.out.println(event);
        }
    }

    // ---- Utility methods to make this sample generic for use with any event type as specified on command line ----

    public static String getEventTypeNames(Class<?> baseClass) {
        StringBuilder sb = new StringBuilder();
        for (String s : getEventTypesMap(baseClass).keySet()) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    public static <T> Class<T> findEventType(String type, Class<? super T> baseClass) {
        Class<?> result = getEventTypesMap(baseClass).get(type);
        if (result == null)
            throw new IllegalArgumentException("Cannot find " + baseClass.getSimpleName() + " '" + type + "'");
        return (Class<T>) result;
    }

    private static Map<String, Class<?>> getEventTypesMap(Class<?> baseClass) {
        TreeMap<String, Class<?>> result = new TreeMap<>();
        for (Class<?> eventType : DXEndpoint.getInstance().getEventTypes()) {
            if (baseClass.isAssignableFrom(eventType))
                result.put(eventType.getSimpleName(), eventType);
        }
        return result;
    }
}
