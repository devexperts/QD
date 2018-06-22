/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.candle.test;

import com.dxfeed.event.candle.CandleType;
import junit.framework.TestCase;
import org.junit.Assert;

public class CandleTypeTest extends TestCase {
    public void testCandleTypeEIsNotSupported() {
        try {
            CandleType.valueOf("E");
            Assert.fail("Candle Type 'E' is not supported");
        } catch (IllegalArgumentException ignored) {
            // ignore
        }
        try {
            CandleType.valueOf("e");
            Assert.fail("Candle Type 'e' is not supported");
        } catch (IllegalArgumentException ignored) {
            // ignore
        }
    }
}