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

import com.dxfeed.event.candle.CandleType;
import org.junit.Test;

import static org.junit.Assert.assertThrows;

public class CandleTypeTest {

    @Test
    public void testCandleTypeEIsNotSupported() {
        assertThrows("Candle Type 'E' is not supported", IllegalArgumentException.class,
            () -> CandleType.valueOf("E"));
        assertThrows("Candle Type 'e' is not supported", IllegalArgumentException.class,
            () -> CandleType.valueOf("e"));
    }
}
