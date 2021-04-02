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
package com.devexperts.qd.tools;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.impl.matrix.MatrixSymbolObjectMap;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.AbstractMessageVisitor;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.util.SymbolObjectMap;
import com.devexperts.qd.util.SymbolObjectVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

final class TopSymbolsCounter extends AbstractMessageVisitor {

    private static final Logging log = Logging.getLogging(TopSymbolsCounter.class);

    private final Sink sink = new Sink();
    private final SymbolObjectMap<AtomicLong> symbolCounters = new MatrixSymbolObjectMap<AtomicLong>();
    private final SymbolCodec codec;
    private final int number;

    TopSymbolsCounter(SymbolCodec codec, int number) {
        if (number < 0)
            number = Integer.MAX_VALUE;
        this.codec = codec;
        this.number = number;
    }

    public Runnable createReportingTask() {
        return new ReportingTask();
    }

    private static class Entry implements Comparable<Entry> {
        final int cipher;
        String symbol;
        final long count;

        private Entry(int cipher, String symbol, long count) {
            this.cipher = cipher;
            this.symbol = symbol;
            this.count = count;
        }

        public String getSymbol(SymbolCodec codec) {
            return codec.decode(cipher, symbol);
        }

        public int compareTo(Entry that) {
            return this.count > that.count ? -1 : this.count < that.count ? 1 : 0;
        }
    }

    private class ReportingTask implements Runnable, SymbolObjectVisitor<AtomicLong> {
        private final ArrayList<Entry> entries = new ArrayList<Entry>();

        public void run() {
            entries.clear();
            synchronized (symbolCounters) {
                symbolCounters.examineEntries(this);
            }
            Collections.sort(entries);

            StringBuilder sb = new StringBuilder();
            sb.append("Top ");
            if (number < Integer.MAX_VALUE)
                sb.append(number).append(" ");
            sb.append("frequent symbols:");
            int n = Math.min(number, entries.size());
            for (int i = 0; i < n; i++) {
                Entry entry = entries.get(i);
                sb.append('\n').append(entry.getSymbol(codec)).append('\t').append(entry.count);
            }
            log.info(sb.toString());
        }

        public boolean hasCapacity() {
            return true;
        }

        public void visitEntry(int cipher, String symbol, AtomicLong value) {
            entries.add(new Entry(cipher, symbol, value.get()));
        }
    }

    @Override
    public boolean visitData(DataProvider provider, MessageType message) {
        return provider.retrieveData(sink);
    }

    @Override
    public boolean visitSubscription(SubscriptionProvider provider, MessageType message) {
        return provider.retrieveSubscription(sink);
    }

    public boolean hasCapacity() {
        return true;
    }


    private class Sink extends AbstractRecordSink {
        @Override
        public void append(RecordCursor cursor) {
            AtomicLong counter = symbolCounters.get(cursor.getCipher(), cursor.getSymbol());
            if (counter == null)
                synchronized (symbolCounters) {
                    counter = symbolCounters.get(cursor.getCipher(), cursor.getSymbol());
                    if (counter == null)
                        symbolCounters.put(cursor.getCipher(), cursor.getSymbol(), counter = new AtomicLong());
                }
            counter.incrementAndGet();

        }
    }
}
