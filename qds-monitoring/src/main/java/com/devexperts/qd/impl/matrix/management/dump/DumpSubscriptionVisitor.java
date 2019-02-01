/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.matrix.management.dump;

import java.io.PrintStream;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.impl.matrix.Collector;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordCursor;

class DumpSubscriptionVisitor extends DumpVisitorBase implements CollectorVisitor {
    DumpSubscriptionVisitor(PrintStream out, String filterSymbol, String filterRecord) {
        super(out, filterSymbol, filterRecord);
    }

    @Override
    public void visit(Collector collector) {
        DumpSubscriptionSink dss = new DumpSubscriptionSink(collector instanceof QDHistory);
        collector.visitAgents(agent -> {
            out.println("--- Subscription from " + agent);
            agent.examineSubscription(dss);
        });
    }

    class DumpSubscriptionSink extends AbstractRecordSink {
        private final boolean history;

        DumpSubscriptionSink(boolean history) {
            this.history = history;
        }

        @Override
        public void append(RecordCursor cursor) {
            DataRecord r = cursor.getRecord();
            String record = r.getName();
            String symbol = cursor.getDecodedSymbol();
            if (!matches(record, symbol))
                return;
            out.print(record);
            out.print('\t');
            out.print(symbol);
            if (history)
                DumpUtil.printTime(out, r, cursor);
            out.println();
        }
    }
}
