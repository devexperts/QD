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
package com.devexperts.qd.impl.matrix.management.dump;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.impl.matrix.Collector;
import com.devexperts.qd.impl.matrix.CollectorDebug;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordCursor;

import java.io.PrintStream;

class DumpSubscriptionVisitor extends DumpVisitorBase implements CollectorVisitor {
    DumpSubscriptionVisitor(PrintStream out, String filterSymbol, String filterRecord) {
        super(out, filterSymbol, filterRecord);
    }

    @Override
    public void visit(Collector collector) {
        collector.visitAgents(new DumpSubscriptionSink(collector instanceof QDHistory));
    }

    class DumpSubscriptionSink extends AbstractRecordSink implements CollectorDebug.AgentVisitor {
        private final boolean history;
        private QDAgent agent;

        DumpSubscriptionSink(boolean history) {
            this.history = history;
        }

        @Override
        public void visitAgent(QDAgent agent) {
            this.agent = agent;
            agent.examineSubscription(this);
        }

        @Override
        public void append(RecordCursor cursor) {
            DataRecord record = cursor.getRecord();
            String symbol = cursor.getDecodedSymbol();
            if (!matches(record, symbol))
                return;
            if (agent != null) {
                out.print("--- Subscription from ");
                out.println(agent);
                agent = null;
            }
            out.print(record.getName());
            out.print('\t');
            out.print(symbol);
            if (history)
                DumpUtil.printTime(out, record, cursor);
            out.println();
        }
    }
}
