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
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class HistorySubscriptionTest {

    @Test
    public void testEmptySubscription() {
        // tests [QD-1230] and [QD-1232] - absence of phantom subscription if not actually subscribed
        QDFactory.getDefaultScheme();
        System.out.println();
        int tests = 0;
        int failures = 0;
        for (int mask = 0; mask < 16; mask++) {
            for (int size = 1; size < 100; size *= 2) {
                tests++;
                if (!checkEmptySub((mask & 8) != 0, (mask & 4) != 0, (mask & 2) != 0, (mask & 1) != 0, size))
                    failures++;
            }
        }
        System.out.println((tests - failures) + " tests passed, " + failures + " tests failed");
        assertEquals("tests failed: " + failures, 0, failures);
    }

    private static CandleSymbol symbol(int i) {
        return CandleSymbol.valueOf("S" + i, CandlePeriod.valueOf(1, CandleType.MINUTE));
    }

    private boolean checkEmptySub(
        boolean storeEverything, boolean promise, boolean subscribe, boolean publish, int size)
    {
        System.out.println("testing storeEverything " + storeEverything +
            ", promise " + promise + ", subscribe " + subscribe + ", publish " + publish + ", size " + size);

        DXEndpoint endpoint = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.LOCAL_HUB)
            .withProperty(DXEndpoint.DXENDPOINT_STORE_EVERYTHING_PROPERTY, String.valueOf(storeEverything))
            .build();
        endpoint.executor(Runnable::run);

        List<Promise<List<Candle>>> promises = new ArrayList<>();
        if (promise && publish) {
            for (int i = 0; i < size; i++) {
                promises.add(endpoint.getFeed().getTimeSeriesPromise(Candle.class, symbol(i), 0, 1000));
            }
        }
        ArrayList<Candle> events = new ArrayList<>();
        DXFeedSubscription<Candle> sub = new DXFeedSubscription<>(Candle.class);
        sub.addEventListener(events::addAll);
        if (subscribe) {
            List<TimeSeriesSubscriptionSymbol<CandleSymbol>> symbols = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                symbols.add(new TimeSeriesSubscriptionSymbol<>(symbol(i), 0));
            }
            sub.setSymbols(symbols);
        }
        sub.attach(endpoint.getFeed());
        assertEquals("garbage events received", 0, events.size());
        if (publish) {
            List<Candle> candles = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                candles.add(new Candle(symbol(i)));
            }
            endpoint.getPublisher().publishEvents(candles);
        }
        assertEquals("events received", subscribe && publish ? size : 0, events.size());
        sub.setSymbols();
        sub.close();
        assertEquals("events received", subscribe && publish ? size : 0, events.size());
        promises.forEach(p -> assertEquals("promise completed", 1, p.getResult().size()));

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
