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

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.osub.WildcardSymbol;
import com.dxfeed.event.EventType;

/**
 * Converts one tape file into another tape file with optional intermediate processing or filtering.
 */
public class ConvertTapeFile {
    public static void main(String[] args) throws InterruptedException {
        // Determine input and output tapes and specify appropriate configuration parameters
        String inputAddress = args.length > 0 ? args[0] : "file:ConvertTapeFile.in[readAs=stream_data,speed=max]";
        String outputAddress = args.length > 1 ? args[1] : "tape:ConvertTapeFile.out[saveAs=stream_data,format=text]";

        // Create input endpoint configured for tape reading
        DXEndpoint inputEndpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.STREAM_FEED) // prevents event conflation and loss due to buffer overflow
            .withProperty(DXEndpoint.DXFEED_WILDCARD_ENABLE_PROPERTY, "true") // enables wildcard subscription
            .withProperty(DXEndpoint.DXENDPOINT_EVENT_TIME_PROPERTY, "true") // use provided event times
            .build();
        // Create output endpoint configured for tape writing
        DXEndpoint outputEndpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.STREAM_PUBLISHER) // prevents event conflation and loss due to buffer overflow
            .withProperty(DXEndpoint.DXFEED_WILDCARD_ENABLE_PROPERTY, "true") // enables wildcard subscription
            .withProperty(DXEndpoint.DXENDPOINT_EVENT_TIME_PROPERTY, "true") // use provided event times
            .build();

        // Create and link event processor for all types of events
        // Note: set of processed event types could be limited if needed
        @SuppressWarnings("unchecked")
        Class<? extends EventType<?>>[] eventTypes = inputEndpoint.getEventTypes().toArray(
            new Class[inputEndpoint.getEventTypes().size()]);
        DXFeedSubscription<? extends EventType<?>> sub = inputEndpoint.getFeed().createSubscription(eventTypes);
        sub.addEventListener(events -> {
            // Here event processing occurs. Events could be modified, removed, or new events added.
            /* For example, the below code adds 1 hour to event times:
            for (EventType event : events)
                event.setEventTime(event.getEventTime() + 3600_000);
            */
            // Publish processed events
            outputEndpoint.getPublisher().publishEvents(events);
        });
        // Subscribe to all symbols
        // Note: set of processed symbols could be limited if needed
        sub.addSymbols(WildcardSymbol.ALL);

        // Connect output endpoint and start output tape writing BEFORE starting input tape reading
        outputEndpoint.connect(outputAddress);
        // Connect input endpoint and start input tape reading AFTER starting output tape writing
        inputEndpoint.connect(inputAddress);

        // Wait until all data is read and processed, and then gracefully close input endpoint
        inputEndpoint.awaitNotConnected();
        inputEndpoint.closeAndAwaitTermination();

        // Wait until all data is processed and written, and then gracefully close output endpoint
        outputEndpoint.awaitProcessed();
        outputEndpoint.closeAndAwaitTermination();
    }
}
