/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.candle.test;

import com.dxfeed.event.candle.CandlePriceLevel;
import junit.framework.TestCase;
import org.junit.Assert;

public class CandlePriceLevelTest extends TestCase {
    public void testInfiniteAndNegativeValueNotAllowedForCandlePriceLevel() {
        try {
            CandlePriceLevel.valueOf(Double.POSITIVE_INFINITY);
            Assert.fail("Candle price level 'POSITIVE_INFINITY' is not supported");
        } catch (IllegalArgumentException ignored) {
            // ignore
        }
        try {
            CandlePriceLevel.valueOf(Double.NEGATIVE_INFINITY);
            Assert.fail("Candle price level 'NEGATIVE_INFINITY' is not supported");
        } catch (IllegalArgumentException ignored) {
            // ignore
        }
        try {
            CandlePriceLevel.valueOf(-1.0);
            Assert.fail("Candle price level 'NEGATIVE' is not supported");
        } catch (IllegalArgumentException ignored) {
            // ignore
        }
        try {
            CandlePriceLevel.valueOf(-0.0);
            Assert.fail("Candle price level 'NEGATIVE' is not supported");
        } catch (IllegalArgumentException ignored) {
            // ignore
        }
    }
}
