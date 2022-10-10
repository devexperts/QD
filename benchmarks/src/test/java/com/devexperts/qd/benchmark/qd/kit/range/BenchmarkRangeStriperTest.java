/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.benchmark.qd.kit.range;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.kit.RangeStriper;
import com.devexperts.qd.SymbolStriper;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class BenchmarkRangeStriperTest {

    private static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE);

    // Self-test to test alternative implementations for correctness.
    // This test is ignored by default, because it requires to have "securities.ipf.zip"
    @Ignore
    @Test
    public void testUniverse() throws Exception {
        List<InstrumentProfile> profiles = new InstrumentProfileReader().readFromFile("securities.ipf.zip");

        //String spec = "byrange_EME220715_EME22071Z_";
        String spec = "byrange_MSFT_";
        SymbolStriper s1 = RangeStriper.valueOf(SCHEME, spec);
        SymbolStriper s2 = LambdaBasedRangeStriper.valueOf(SCHEME, spec);

        SymbolCodec codec = SCHEME.getCodec();
        for (InstrumentProfile profile : profiles) {
            String symbol = profile.getSymbol();
            int cipher = codec.encode(symbol);

            int r1 = s1.getStripeIndex(cipher, cipher != 0 ? null : symbol);
            int r2 = s1.getStripeIndex(0, symbol);

            int r3 = s2.getStripeIndex(cipher, cipher != 0 ? null : symbol);
            int r4 = s2.getStripeIndex(0, symbol);

            assertEquals(symbol, r1, r2);
            assertEquals(symbol, r3, r4);
            assertEquals(symbol, r1, r3);
        }
    }
}
