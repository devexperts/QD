/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2020 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.test;

import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.qtp.QDEndpoint;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.impl.DXEndpointImpl;
import com.dxfeed.api.osub.ObservableSubscription;
import com.dxfeed.api.osub.ObservableSubscriptionChangeListener;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.candle.CandleType;
import com.dxfeed.promise.Promise;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class HistorySubscriptionTest extends TestCase {
    private static final CandleSymbol CANDLE_SYMBOL =
        CandleSymbol.valueOf("TEST", CandlePeriod.valueOf(1, CandleType.MINUTE));
    private static final TimeSeriesSubscriptionSymbol<CandleSymbol> SUB_SYMBOL =
        new TimeSeriesSubscriptionSymbol<>(CANDLE_SYMBOL, 0);

    public void testEmptySubscription() {
        // tests [QD-1230] - absence of phantom subscription if not actually subscribed
        QDFactory.getDefaultScheme();
        System.out.println();
        int result = 0;
        for (int mask = 0; mask < 16; mask++)
            result += checkEmptySub((mask & 8) != 0, (mask & 4) != 0, (mask & 2) != 0, (mask & 1) != 0) ? 0 : 1;
        assertEquals("tests failed: " + result, 0, result);
    }

    private boolean checkEmptySub(boolean storeEverything, boolean getPromise, boolean subscribe, boolean publish) {
        System.out.println("testing storeEverything " + storeEverything +
            ", getPromise " + getPromise + ", subscribe " + subscribe + ", publish " + publish);

        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.LOCAL_HUB)
            .withProperty(DXEndpoint.DXENDPOINT_STORE_EVERYTHING_PROPERTY, String.valueOf(storeEverything))
            .build();
        endpoint.executor(Runnable::run);

        Promise<List<Candle>> promise = getPromise && publish ?
            endpoint.getFeed().getTimeSeriesPromise(Candle.class, CANDLE_SYMBOL, 0, 1000) :
            Promise.completed(Collections.singletonList(new Candle(CANDLE_SYMBOL)));
        ArrayList<Candle> events = new ArrayList<>();
        DXFeedSubscription<Candle> sub = new DXFeedSubscription<>(Candle.class);
        sub.addEventListener(events::addAll);
        sub.setSymbols(SUB_SYMBOL);
        if (subscribe)
            sub.attach(endpoint.getFeed());
        assertEquals("garbage events received", 0, events.size());
        if (publish)
            endpoint.getPublisher().publishEvents(Collections.singletonList(new Candle(CANDLE_SYMBOL)));
        assertEquals("events received", subscribe && publish ? 1 : 0, events.size());
        sub.setSymbols();
        sub.close();
        assertEquals("events received", subscribe && publish ? 1 : 0, events.size());
        assertEquals("promise received", 1, promise.getResult().size());

        boolean result = assertEmptySub(endpoint);
        endpoint.close();
        System.out.println("test " + (result ? "passed" : "FAILED"));
        System.out.println();
        return result;
    }

    private boolean assertEmptySub(DXEndpoint endpoint) {
        ArrayList<String> messages = new ArrayList<>();

        QDEndpoint qdEndpoint = ((DXEndpointImpl) endpoint).getQDEndpoint();
        RecordBuffer buffer = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
        qdEndpoint.getCollector(QDContract.HISTORY).examineSubscription(buffer);
        if (!buffer.isEmpty())
            messages.add("QDHistory subscription [" + buffer.size() + "] " + buffer.next());
        buffer.clear();
        QDDistributor distributor = qdEndpoint.getCollector(QDContract.HISTORY).distributorBuilder().build();
        //noinspection StatementWithEmptyBody
        while (distributor.getAddedRecordProvider().retrieve(buffer)) {
            // do nothing
        }
        if (!buffer.isEmpty())
            messages.add("QDDistributor added [" + buffer.size() + "] " + buffer.next());
        buffer.clear();
        distributor.close();

        ObservableSubscription<Candle> subscription = endpoint.getPublisher().getSubscription(Candle.class);
        ObservableSubscriptionChangeListener listener = new ObservableSubscriptionChangeListener() {
            @Override
            public void symbolsAdded(Set<?> symbols) {
                messages.add("dxFeed added " + symbols);
            }

            @Override
            public void symbolsRemoved(Set<?> symbols) {
                messages.add("dxFeed removed " + symbols);
            }
        };
        subscription.addChangeListener(listener);
        subscription.removeChangeListener(listener);

        messages.forEach(System.out::println);
        return messages.isEmpty();
    }
}
