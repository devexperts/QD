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
package com.dxfeed.webservice.comet;

import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.osub.TimeSeriesSubscriptionSymbol;
import com.dxfeed.event.candle.CandleSymbol;
import com.dxfeed.event.market.TimeAndSale;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * This test checks that when you subscribe using symbol decorated with {@link TimeSeriesSubscriptionSymbol} in
 * {@link DXFeedSubscription} you have to use decorated symbol to unsubscribe as well. This decoration is used to
 * represent history subscription (as an alternative to {@link com.dxfeed.api.DXFeedTimeSeriesSubscription}).
 */
public class TimeSeriesSubscriptionTest {

    @Test
    public void testSubscription() {
        DXFeedSubscription<?> sub = new DXFeedSubscription<>(TimeAndSale.class);
        String sym1 = "MSFT";
        CandleSymbol sym2 = CandleSymbol.valueOf("IBM{=d}");

        TimeSeriesSubscriptionSymbol<?> timeSeriesSym1 = new TimeSeriesSubscriptionSymbol<>(sym1, 0);
        TimeSeriesSubscriptionSymbol<?> timeSeriesSym2 = new TimeSeriesSubscriptionSymbol<>(sym2, 0);

        // Add symbols
        sub.addSymbols(timeSeriesSym1);
        assertEquals(1, sub.getSymbols().size());
        sub.addSymbols(timeSeriesSym2);
        assertEquals(2, sub.getSymbols().size());

        // Remove undecorated symbols will not work
        sub.removeSymbols(sym1, sym2);
        assertEquals(2, sub.getSymbols().size());

        // Only removal of decorated symbols should work
        sub.removeSymbols(timeSeriesSym1);
        assertEquals(1, sub.getSymbols().size());
        sub.removeSymbols(timeSeriesSym2);
        assertEquals(0, sub.getSymbols().size());
    }
}
