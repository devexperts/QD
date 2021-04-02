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
package com.dxfeed.plotter;

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedEventListener;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.Quote;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class Feed {
    final String name;
    final LabelFlashSupport flasher;

    private final DXEndpoint endpoint;
    private final DXFeed feed;
    private final DXFeedSubscription<Quote> subscription;
    private final Set<String> symbols;

    private String lastAddress;
    private long lastReceivingTime;

    Feed(String name, LabelFlashSupport flasher, DXEndpoint endpoint) {
        this.name = name;
        this.flasher = flasher;
        this.endpoint = endpoint;
        this.feed = endpoint.getFeed();
        this.subscription = feed.createSubscription(Quote.class);
        this.subscription.addEventListener(events -> {
            lastReceivingTime = System.currentTimeMillis();
            if (flasher.isFlashing()) {
                flasher.updateColor(DXFeedMarketDataPlotter.WORKING_COLOR);
                flasher.stopFlashing();
            }
        });
        this.symbols = new HashSet<>();
    }

    void connect(String address) {
        endpoint.connect(address);
        this.lastAddress = address;
    }

    void reconnect() {
        endpoint.connect(lastAddress);
    }

    void disconnect() {
        endpoint.disconnect();
    }

    void addListener(DXFeedEventListener<Quote> listener) {
        subscription.addEventListener(listener);
    }

    void removeSymbols(List<String> symbols) {
        if (this.symbols.removeAll(symbols)) {
            subscription.removeSymbols(symbols);
        }
    }

    void addSymbols(List<String> symbols) {
        if (this.symbols.addAll(symbols)) {
            subscription.addSymbols(symbols);
        }
    }

    long lastReceivingTime() {
        return lastReceivingTime;
    }
}
