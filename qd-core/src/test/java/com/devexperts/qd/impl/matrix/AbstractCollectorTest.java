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
package com.devexperts.qd.impl.matrix;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDErrorHandler;
import com.devexperts.qd.kit.CompactIntField;
import com.devexperts.qd.kit.DefaultRecord;
import com.devexperts.qd.kit.DefaultScheme;
import com.devexperts.qd.kit.PentaCodec;
import com.devexperts.qd.kit.VoidIntField;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import org.junit.After;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class AbstractCollectorTest {

    protected static final int VALUE_INDEX = 2;
    protected static final DataRecord RECORD = new DefaultRecord(0, "Test", true,
        new DataIntField[] {
            new VoidIntField(0, "Test.Dummy"),
            new CompactIntField(1, "Test.Index"),
            new CompactIntField(VALUE_INDEX, "Test.Value")
        }, null);
    protected static final DataScheme SCHEME = new DefaultScheme(PentaCodec.INSTANCE, RECORD);

    // Test symbols (long to avoid SymbolCodec encoding)
    protected static final String SYMBOL = "SYMBOL_SYMBOL";
    protected static final String AAPL = "AAPL_AAPL_AAPL";
    protected static final String MSFT = "MSFT_MSFT_MSFT";

    protected QDCollector collector;
    protected QDDistributor distributor;
    protected QDAgent agent;

    protected void setUp(QDCollector c) {
        collector = c;
        collector.setErrorHandler(QDErrorHandler.THROW);
        distributor = collector.distributorBuilder().build();
        agent = collector.agentBuilder().build();
    }

    @After
    public void tearDown() throws Exception {
        if (agent != null)
            agent.close();
        if (distributor != null)
            distributor.close();
        if (collector != null)
            collector.close();
    }

    protected RecordMode getRecordMode() {
        return RecordMode.FLAGGED_DATA.withTimeMark();
    }

    void process(Consumer<RecordBuffer> consumer, String symbol) {
        process(consumer, symbol, 0, 0);
    }

    void process(Consumer<RecordBuffer> consumer, String symbol, int value, int timeMark) {
        process(consumer, symbol, value, timeMark, 0);
    }

    void process(Consumer<RecordBuffer> consumer, String symbol, int value, int timeMark, long time) {
        RecordBuffer buf = new RecordBuffer(getRecordMode());
        RecordCursor cursor = buf.add(RECORD, 0, symbol);
        if (cursor.hasTime())
            cursor.setTime(time);
        cursor.setInt(VALUE_INDEX, value);
        cursor.setTimeMark(timeMark);
        consumer.accept(buf);
    }

    void assertRetrieve(String symbol, int value, int timeMark) {
        assertRetrieve(symbol, value, timeMark, 0);
    }

    void assertRetrieve(String symbol, int value, int timeMark, long time) {
        assertRetrieve(agent, symbol, value, timeMark, time);
    }

    void assertRetrieve(RecordProvider provider, String symbol, int value, int timeMark, long time) {
        AtomicBoolean received = new AtomicBoolean();
        provider.retrieve(new AbstractRecordSink() {
            @Override
            public boolean hasCapacity() {
                return !received.get();
            }

            @Override
            public void append(RecordCursor cursor) {
                assertEquals("symbol", symbol, cursor.getDecodedSymbol());
                if (cursor.hasTime()) {
                    assertEquals("time", time, cursor.getTime());
                }
                assertEquals("timeMark", timeMark, cursor.getTimeMark());
                assertEquals("value", value, cursor.getInt(VALUE_INDEX));
                received.set(true);
            }
        });
        assertTrue("received", received.get());
    }

    void dumpRetrieve(RecordProvider provider) {
        AtomicBoolean received = new AtomicBoolean();
        provider.retrieve(new AbstractRecordSink() {
            @Override
            public boolean hasCapacity() {
                return !received.get();
            }

            @Override
            public void append(RecordCursor cursor) {
                System.out.println(cursor.getDecodedSymbol() +
                    ", value=" + cursor.getInt(VALUE_INDEX) +
                    ", timeMark=" + cursor.getTimeMark());
                received.set(true);
            }
        });
    }

    void assertRetrieveNothing() {
        assertRetrieveNothing(agent);
    }

    void assertRetrieveNothing(RecordProvider provider) {
        boolean hasMore = provider.retrieve(new AbstractRecordSink() {
            @Override
            public void append(RecordCursor cursor) {
                fail("available");
            }
        });
        assertFalse("available", hasMore);
    }
}
