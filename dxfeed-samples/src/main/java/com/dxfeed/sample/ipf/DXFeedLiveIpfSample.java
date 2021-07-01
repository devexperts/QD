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

import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileType;
import com.dxfeed.ipf.live.InstrumentProfileCollector;
import com.dxfeed.ipf.live.InstrumentProfileConnection;

import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class DXFeedLiveIpfSample {

    private static final String DXFEED_IPF_URL = "https://demo:demo@tools.dxfeed.com/ipf";

    public static void main(String[] args) throws InterruptedException {
        if (args.length > 1) {
            System.err.println("usage: DXFeedLiveIpfSample [<ipf-url>]");
            System.err.println("where: <ipf-url>  is URL for the instruments profiles, default: " + DXFEED_IPF_URL);
            return;
        }
        String url = (args.length > 0) ? args[0] : DXFEED_IPF_URL;

        InstrumentProfileCollector collector = new InstrumentProfileCollector();
        InstrumentProfileConnection connection = InstrumentProfileConnection.createConnection(url, collector);
        // Update period can be used to re-read IPF files, not needed for services supporting IPF "live-update"
        connection.setUpdatePeriod(60_000L);
        connection.addStateChangeListener(event -> {
            System.out.println("Connection state: " + event.getNewValue());
        });
        connection.start();
        // We can wait until we get first full snapshot of instrument profiles
        connection.waitUntilCompleted(10, TimeUnit.SECONDS);

        // Data model to keep all instrument profiles mapped by their ticker symbol
        Map<String, InstrumentProfile> profiles = new ConcurrentHashMap<>();

        // It is possible to add listener after connection is started - updates will not be missed in this case
        collector.addUpdateListener(instruments -> {
            System.out.println("\nInstrument Profiles:");
            // We can observe REMOVED elements - need to add necessary filtering
            // See javadoc for InstrumentProfileCollector for more details

            // (1) We can either process instrument profile updates manually
            instruments.forEachRemaining(profile -> {
                if (InstrumentProfileType.REMOVED.name().equals(profile.getType())) {
                    // Profile was removed - remove it from our data model
                    profiles.remove(profile.getSymbol());
                } else {
                    // Profile was updated - collector only notifies us if profile was changed
                    profiles.put(profile.getSymbol(), profile);
                }
            });
            System.out.println("Total number of profiles (1): " + profiles.size());

            // (2) or access the concurrent view of instrument profiles
            Set<String> symbols = StreamSupport.stream(collector.view().spliterator(), false)
                .filter(profile -> !InstrumentProfileType.REMOVED.name().equals(profile.getType()))
                .map(InstrumentProfile::getSymbol)
                .collect(Collectors.toSet());
            System.out.println("Total number of profiles (2): " + symbols.size());

            System.out.println("Last modified: " + new Date(collector.getLastUpdateTime()));
        });

        try {
            Thread.sleep(Long.MAX_VALUE);
        } finally {
            connection.close();
        }
    }
}
