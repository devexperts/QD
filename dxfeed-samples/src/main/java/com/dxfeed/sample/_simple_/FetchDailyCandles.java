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

import com.devexperts.util.TimeUtil;
import com.dxfeed.api.DXFeed;
import com.dxfeed.event.candle.Candle;
import com.dxfeed.event.candle.CandlePeriod;
import com.dxfeed.event.candle.CandleSymbol;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fetches last 20 days of candles for a specified symbol, prints them, and exits.
 */
public class FetchDailyCandles {
    private static final int DAYS = 20;

    public static void main(String[] args) {
        String baseSymbol = args[0];
        CandleSymbol candleSymbol = CandleSymbol.valueOf(baseSymbol, CandlePeriod.DAY);
        long toTime = System.currentTimeMillis();
        long fromTime = toTime - DAYS * TimeUtil.DAY;
        System.out.printf("Fetching last %d days of candles for %s...%n", DAYS, baseSymbol);
        try {
            fetchAndPrint(candleSymbol, toTime, fromTime);
        } finally {
            System.exit(0); // Exit when done
        }
    }

    private static void fetchAndPrint(CandleSymbol candleSymbol, long toTime, long fromTime) {
        // Use default DXFeed instance for that data feed address is defined by dxfeed.properties file
        List<Candle> result = DXFeed.getInstance().
            getTimeSeriesPromise(Candle.class, candleSymbol, fromTime, toTime).await(5, TimeUnit.SECONDS);
        for (Candle candle : result)
            System.out.println(candle);
    }
}
