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
import com.dxfeed.api.DXPublisher;
import com.dxfeed.event.market.Quote;

import java.util.Arrays;

/**
 * Writes a text tape file.
 */
public class WriteTapeFile {
    public static void main(String[] args) throws InterruptedException {
        // Create an appropriate endpoint
        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withProperty(DXEndpoint.DXFEED_WILDCARD_ENABLE_PROPERTY, "true") // is required for tape connector to be able to receive everything
            .withRole(DXEndpoint.Role.PUBLISHER)
            .build();
        // Connect to the address, remove [format=text] for binary format
        endpoint.connect("tape:WriteTapeFile.out.txt[format=text]");
        // Get publisher
        DXPublisher pub = endpoint.getPublisher();
        // Publish events
        Quote quote1 = new Quote("TEST1");
        quote1.setBidPrice(10.1);
        quote1.setAskPrice(10.2);
        Quote quote2 = new Quote("TEST2");
        quote2.setBidPrice(17.1);
        quote2.setAskPrice(17.2);
        pub.publishEvents(Arrays.asList(quote1, quote2));
        // Wait until all data is written, close, and wait until it closes
        endpoint.awaitProcessed();
        endpoint.closeAndAwaitTermination();
    }
}
