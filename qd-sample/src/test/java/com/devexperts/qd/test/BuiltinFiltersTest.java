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

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.kit.CompositeFilters;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

import static org.junit.Assert.assertEquals;

public class BuiltinFiltersTest {
    private static final DataScheme SCHEME = QDFactory.getDefaultScheme();
    private static final DataRecord RECORD = SCHEME.findRecordByName("Quote");

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testOptFilter() {
        SubscriptionFilter filter = CompositeFilters.getFactory(SCHEME).createFilter("opt");
        assertAccepts(true, filter, ".IBM");
        assertAccepts(false, filter, "IBM");
    }

    @Test
    public void testCompositePatternFilter() {
        SubscriptionFilter filter = CompositeFilters.getFactory(SCHEME).createFilter("I*,MMM");
        assertAccepts(false, filter, ".IBM");
        assertAccepts(true, filter, "IBM");
        assertAccepts(true, filter, "MMM");
    }

    @Test
    public void testIpfFilter() throws IOException {
        // Create temp IPF file
        File tempIpf = new File(tempFolder.getRoot(), "test.ipf");
        try (PrintWriter out = new PrintWriter(tempIpf)) {
            out.println("#T::=TYPE,SYMBOL");
            out.println("T,IBM");
            out.println("##COMPLETE");
        }

        // test this IPF file
        SubscriptionFilter filter = CompositeFilters.getFactory(SCHEME).createFilter("ipf[" + tempIpf.toURI() + "]");
        assertAccepts(false, filter, ".IBM");
        assertAccepts(true, filter, "IBM");
    }

    private void assertAccepts(boolean expected, SubscriptionFilter filter, String symbol) {
        assertEquals(expected, filter.acceptRecord(RECORD, SCHEME.getCodec().encode(symbol), symbol));
    }
}
