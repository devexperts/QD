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
import com.devexperts.qd.impl.matrix.*;
import com.devexperts.qd.ng.*;
import com.devexperts.qd.qtp.BuiltinFields;

class DumpDataVisitor extends DumpVisitorBase implements CollectorVisitor {
    DumpDataVisitor(PrintStream out, String filterSymbol, String filterRecord) {
        super(out, filterSymbol, filterRecord);
    }

    @Override
    public void visit(Collector collector) {
        out.println("--- Data from " + collector);
        collector.examineData(new DumpDataSink());
    }

    class DumpDataSink extends AbstractRecordSink implements HistoryBufferDebugSink {
        @Override
        public void visitHistoryBuffer(DataRecord r, int cipher, String encodedSymbol, long timeTotalSub, HistoryBuffer hb) {
            String record = r.getName();
            String symbol = r.getScheme().getCodec().decode(cipher, encodedSymbol);
            if (!matches(record, symbol))
                return;
            out.println("HistoryBuffer " + r + " " + symbol);
            out.println("\tisTx=" + hb.isTx());
            out.println("\tisSweepTx=" + hb.isSweepTx());
            out.println("\twasSnapshotBeginSeen=" + hb.wasSnapshotBeginSeen());
            out.println("\twasSnapshotEndSeen=" + hb.wasSnapshotEndSeen());
            out.println("\twasEverSnapshotMode=" + hb.wasEverSnapshotMode());
            out.println("\tisWaitingForSnapshotBegin=" + hb.isWaitingForSnapshotBegin());
            out.println("\tgetSnapshotTime=" + DumpUtil.timeString(r, hb.getSnapshotTime()));
            out.println("\tgetEverSnapshotTime=" + DumpUtil.timeString(r, hb.getEverSnapshotTime()));
            out.println("\tgetSnipSnapshotTime=" + DumpUtil.timeString(r, hb.getSnipSnapshotTime()));
            out.println("\ttimeTotalSub=" + DumpUtil.timeString(r, timeTotalSub));
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
            for (int i = 0; i < cursor.getIntCount(); i++) {
                out.print('\t');
                out.print(r.getIntField(i).toString(cursor.getInt(i)));
            }
            for (int i = 0; i < cursor.getObjCount(); i++) {
                out.print('\t');
                out.print(r.getObjField(i).toString(cursor.getObj(i)));
            }
            if (cursor.getEventFlags() != 0) {
                out.print('\t');
                out.print(BuiltinFields.EVENT_FLAGS_FIELD_NAME);
                out.print('=');
                out.print(EventFlag.formatEventFlags(cursor.getEventFlags()));
            }
            out.println();
        }

        @Override
        public void visitDone(DataRecord r, int cipher, String encodedSymbol, int examineMethodResult) {
            String record = r.getName();
            String symbol = r.getScheme().getCodec().decode(cipher, encodedSymbol);
            if (!matches(record, symbol))
                return;
            out.println("HistoryBuffer examined=" + examineMethodResult);
        }
    }
}
