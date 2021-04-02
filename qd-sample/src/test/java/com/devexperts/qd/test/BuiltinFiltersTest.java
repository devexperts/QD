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
package com.devexperts.qd.test;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.kit.CompositeFilters;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class BuiltinFiltersTest extends TestCase {
    private static final DataScheme SCHEME = QDFactory.getDefaultScheme();
    private static final DataRecord RECORD = SCHEME.findRecordByName("Quote");

    public void testOptFilter() {
        SubscriptionFilter filter = CompositeFilters.getFactory(SCHEME).createFilter("opt");
        assertAccepts(true, filter, ".IBM");
        assertAccepts(false, filter, "IBM");
    }

    public void testCompositePatternFilter() {
        SubscriptionFilter filter = CompositeFilters.getFactory(SCHEME).createFilter("I*,MMM");
        assertAccepts(false, filter, ".IBM");
        assertAccepts(true, filter, "IBM");
        assertAccepts(true, filter, "MMM");
    }

    public void testIpfFilter() throws IOException {
        // create temp IPF file
        File tempIpf = File.createTempFile("temp", ".ipf");
        PrintWriter out = new PrintWriter(tempIpf);
        try {
            out.println("#T::=TYPE,SYMBOL");
            out.println("T,IBM");
        } finally {
            out.close();
        }
        tempIpf.deleteOnExit();

        // test this IPF file
        SubscriptionFilter filter = CompositeFilters.getFactory(SCHEME).createFilter("ipf[" + tempIpf.toURI() + "]");
        assertAccepts(false, filter, ".IBM");
        assertAccepts(true, filter, "IBM");
    }

    private void assertAccepts(boolean expected, SubscriptionFilter filter, String symbol) {
        assertEquals(expected, filter.acceptRecord(RECORD, SCHEME.getCodec().encode(symbol), symbol));
    }
}
