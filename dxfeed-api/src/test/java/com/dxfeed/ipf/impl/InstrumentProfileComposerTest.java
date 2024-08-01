/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf.impl;

import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileType;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InstrumentProfileComposerTest {

    @Test
    public void testNoFilter() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InstrumentProfileComposer composer = new InstrumentProfileComposer(out);

        compose(composer, profile(InstrumentProfileType.STOCK, "MSFT", "CURRENCY", "USD", "FOO", "BAR"));

        BufferedReader in = new BufferedReader(new StringReader(out.toString()));
        assertEquals("#STOCK::=TYPE,SYMBOL,CURRENCY,FOO", in.readLine());
        assertEquals("STOCK,MSFT,USD,BAR", in.readLine());
    }

    @Test
    public void testFilter() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InstrumentProfileComposer composer = new InstrumentProfileComposer(out,
            f -> Arrays.asList("CURRENCY","BAZ").contains(f));

        compose(composer,
            profile(InstrumentProfileType.STOCK, "MSFT", "CURRENCY", "USD", "FOO", "BAR"),
            profile(InstrumentProfileType.FUTURE, "/ESH7", "EXCHANGE_DATA", "XCHG", "BAZ", "QUX")
        );

        BufferedReader in = new BufferedReader(new StringReader(out.toString()));
        assertEquals("#FUTURE::=TYPE,SYMBOL,BAZ", in.readLine());
        assertEquals("#STOCK::=TYPE,SYMBOL,CURRENCY", in.readLine());
        assertEquals("STOCK,MSFT,USD", in.readLine());
        assertEquals("FUTURE,/ESH7,QUX", in.readLine());
    }

    /**
     * Tests that the field filter in the {@link InstrumentProfileComposer} is not called excessively.
     *
     * <p>Since the filter is specified by an external user, it may be inefficient.
     * The composer should not call the filter directly on individual {@link InstrumentProfile profiles},
     * but rather process all profiles and then apply the filter to a set of new or updated fields.
     */
    @Test
    public void testFilterIsEffective() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        Predicate<String> countingFilter = s -> {
            counter.incrementAndGet();
            return s.equals("FOO") || s.equals("BAZ");
        };

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        InstrumentProfileComposer composer = new InstrumentProfileComposer(out, countingFilter);

        compose(composer,
            profile(InstrumentProfileType.STOCK, "A1", "CURRENCY", "USD", "FOO", "A1"),
            profile(InstrumentProfileType.STOCK, "A2", "CURRENCY", "USD", "FOO", "A2")
        );

        compose(composer,
            profile(InstrumentProfileType.STOCK, "B1", "CURRENCY", "USD", "FOO", "B1"),
            profile(InstrumentProfileType.STOCK, "B2", "CURRENCY", "USD", "FOO", "B2")
        );

        int expected = 3;
        assertTrue("Filter call count: expected " + expected + ", actual " + counter.get(), counter.get() <= expected);

        // Adding fields that are filtered out should not cause a new format line
        compose(composer,
            profile(InstrumentProfileType.STOCK, "C1", "CURRENCY", "USD", "FOO", "C1", "BAR", "C1"),
            profile(InstrumentProfileType.STOCK, "C2", "CURRENCY", "USD", "FOO", "C2", "BAR", "C2")
        );
        // Allow extra testing cycle when format changes
        expected += 4;
        assertTrue("Filter call count: expected " + expected + ", actual " + counter.get(), counter.get() <= expected);

        // Adding new fields should cause the new format line to be printed
        compose(composer,
            profile(InstrumentProfileType.STOCK, "D1", "CURRENCY", "USD", "FOO", "D1", "BAZ", "D1"),
            profile(InstrumentProfileType.STOCK, "D2", "CURRENCY", "USD", "FOO", "D2", "BAZ", "D2")
        );
        // Allow extra testing cycle when format changes
        expected += 5;
        assertTrue("Filter call count: expected " + expected + ", actual " + counter.get(), counter.get() <= expected);

        BufferedReader in = new BufferedReader(new StringReader(out.toString()));
        assertEquals("#STOCK::=TYPE,SYMBOL,FOO", in.readLine());
        assertEquals("STOCK,A1,A1", in.readLine());
        assertEquals("STOCK,A2,A2", in.readLine());
        assertEquals("STOCK,B1,B1", in.readLine());
        assertEquals("STOCK,B2,B2", in.readLine());
        assertEquals("STOCK,C1,C1", in.readLine());
        assertEquals("STOCK,C2,C2", in.readLine());
        // Note that custom fields are sorted by name
        assertEquals("#STOCK::=TYPE,SYMBOL,BAZ,FOO", in.readLine());
        assertEquals("STOCK,D1,D1,D1", in.readLine());
        assertEquals("STOCK,D2,D2,D2", in.readLine());
    }

    private static void compose(InstrumentProfileComposer composer, InstrumentProfile... profiles) throws IOException {
        composer.compose(Arrays.asList(profiles), false);
    }

    private static InstrumentProfile profile(InstrumentProfileType type, String symbol, String... fields) {
        InstrumentProfile ip = new InstrumentProfile();
        ip.setType(type.name());
        ip.setSymbol(symbol);
        for (int i = 0; i < fields.length; i += 2) {
            ip.setField(fields[i], fields[i + 1]);
        }
        return ip;
    }
}
