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
package com.dxfeed.event.candle.test;

import com.dxfeed.event.candle.CandlePriceLevel;
import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class CandlePriceLevelTest {

    @Test
    public void testInfiniteAndNegativeValueNotAllowedForCandlePriceLevel() {
        assertThrows("Candle price level 'POSITIVE_INFINITY' is not supported", IllegalArgumentException.class,
            () -> CandlePriceLevel.valueOf(Double.POSITIVE_INFINITY));
        assertThrows("Candle price level 'NEGATIVE_INFINITY' is not supported", IllegalArgumentException.class,
            () -> CandlePriceLevel.valueOf(Double.NEGATIVE_INFINITY));
        assertThrows("Candle price level 'NEGATIVE' is not supported", IllegalArgumentException.class,
            () -> CandlePriceLevel.valueOf(-1.0));
        assertThrows("Candle price level 'NEGATIVE' is not supported", IllegalArgumentException.class,
            () -> CandlePriceLevel.valueOf(-0.0));
    }
}
