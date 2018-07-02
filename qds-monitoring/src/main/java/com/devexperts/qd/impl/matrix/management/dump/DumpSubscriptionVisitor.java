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
package com.devexperts.qd.impl.matrix.management.dump;

import com.devexperts.qd.*;
import com.devexperts.qd.impl.matrix.Collector;
import com.devexperts.qd.impl.matrix.CollectorDebug;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordCursor;

class DumpSubscriptionVisitor implements CollectorVisitor {
    private final String filterSymbol;
    private final String filterRecord;

    DumpSubscriptionVisitor(String filterSymbol, String filterRecord) {
        this.filterSymbol = filterSymbol;
        this.filterRecord = filterRecord;
    }

    public void visit(Collector collector) {
        final DumpSubscriptionSink dss = new DumpSubscriptionSink(filterSymbol, filterRecord,
            collector instanceof QDHistory);
        collector.visitAgents(new CollectorDebug.AgentVisitor() {
            public void visitAgent(QDAgent agent) {
                System.out.println("--- Subscription from " + agent);
                agent.examineSubscription(dss);
            }
        });
    }

    static class DumpSubscriptionSink extends AbstractRecordSink {
        private final String filterSymbol;
        private final String filterRecord;
        private final boolean history;

        DumpSubscriptionSink(String filterSymbol, String filterRecord, boolean history) {
            this.filterSymbol = filterSymbol;
            this.filterRecord = filterRecord;
            this.history = history;
        }

        @Override
        public void append(RecordCursor cursor) {
            DataRecord r = cursor.getRecord();
            String record = r.getName();
            String symbol = cursor.getDecodedSymbol();
            if ((filterSymbol == null || filterSymbol.equals(symbol)) &&
                (filterRecord == null || filterRecord.equals(record)))
            {
                System.out.print(record);
                System.out.print('\t');
                System.out.print(symbol);
                if (history) {
                    for (int i = 0; i < 2; i++) {
                        System.out.print('\t');
                        System.out.print(r.getIntField(i).getString(cursor));
                    }
                }
                System.out.println();
            }
        }
    }
}
