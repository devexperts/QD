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
package com.devexperts.qd.test;

import com.devexperts.qd.DataBuffer;
import com.devexperts.qd.DataScheme;
import org.junit.Test;

import java.util.Random;

public class DataBufferTest {
    private Random rnd = new Random();

    private static final int REPEAT = 100;

    @Test
    public void testVisitIterate() {
        long providerSeed = rnd.nextLong();
        DataBuffer buffer = new DataBuffer();
        DataScheme scheme = new TestDataScheme(rnd.nextLong());
        TestDataProvider provider1 = new TestDataProvider(scheme, providerSeed);
        TestDataProvider provider2 = new TestDataProvider(scheme, providerSeed);
        for (int repeat = 0; repeat < REPEAT; repeat++) {
            provider1.retrieveData(buffer);
            ComparingDataVisitor.compare(provider2, buffer);
        }
    }
}
