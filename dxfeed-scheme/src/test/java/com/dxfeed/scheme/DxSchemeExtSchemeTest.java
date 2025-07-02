/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.scheme;

import com.devexperts.qd.DataRecord;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;

public class DxSchemeExtSchemeTest {
    @Test
    public void testNanoTimePropWithExtScheme() throws IOException, SchemeException {
        Properties props = new Properties();
        props.setProperty("dxscheme.nanoTime", "true");

        DXScheme scheme = DXScheme.newLoader()
            .fromSpecification("opt:dxprops,opt:sysprops,resource:dxfeed.xml")
            .load()
            .withProperties(props);

        DataRecord quote = scheme.findRecordByName("Quote");
        assertNotNull(quote.findFieldByName("Sequence"));
        assertNotNull(quote.findFieldByName("TimeNanoPart"));
    }
}
