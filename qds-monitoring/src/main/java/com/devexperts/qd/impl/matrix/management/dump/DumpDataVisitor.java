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

import com.devexperts.qd.DataField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.impl.matrix.Collector;
import com.devexperts.qd.impl.matrix.HistoryBuffer;
import com.devexperts.qd.impl.matrix.HistoryBufferDebugSink;
import com.devexperts.qd.kit.VoidIntField;
import com.devexperts.qd.kit.VoidObjField;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.BuiltinFields;

import java.io.PrintStream;

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
        public void visitHistoryBuffer(DataRecord record, int cipher, String encodedSymbol, long timeTotalSub, HistoryBuffer hb) {
            String symbol = record.getScheme().getCodec().decode(cipher, encodedSymbol);
            if (!matches(record, symbol))
                return;
            out.println("HistoryBuffer " + record + " " + symbol);
            out.println("\tisTx=" + hb.isTx());
            out.println("\tisSweepTx=" + hb.isSweepTx());
            out.println("\twasSnapshotBeginSeen=" + hb.wasSnapshotBeginSeen());
            out.println("\twasSnapshotEndSeen=" + hb.wasSnapshotEndSeen());
            out.println("\twasEverSnapshotMode=" + hb.wasEverSnapshotMode());
            out.println("\tisWaitingForSnapshotBegin=" + hb.isWaitingForSnapshotBegin());
            out.println("\tgetSnapshotTime=" + DumpUtil.timeString(record, hb.getSnapshotTime()));
            out.println("\tgetEverSnapshotTime=" + DumpUtil.timeString(record, hb.getEverSnapshotTime()));
            out.println("\tgetSnipSnapshotTime=" + DumpUtil.timeString(record, hb.getSnipSnapshotTime()));
            out.println("\ttimeTotalSub=" + DumpUtil.timeString(record, timeTotalSub));
        }

        @Override
        public void append(RecordCursor cursor) {
            DataRecord record = cursor.getRecord();
            String symbol = cursor.getDecodedSymbol();
            if (!matches(record, symbol))
                return;
            out.print(record.getName());
            out.print('\t');
            out.print(symbol);
            for (int i = 0; i < cursor.getIntCount(); i++) {
                DataField field = record.getIntField(i);
                if (field instanceof VoidIntField)
                    continue;
                out.print('\t');
                out.print(field.getString(cursor));
            }
            for (int i = 0; i < cursor.getObjCount(); i++) {
                DataField field = record.getIntField(i);
                if (field instanceof VoidObjField)
                    continue;
                out.print('\t');
                out.print(field.getString(cursor));
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
        public void visitDone(DataRecord record, int cipher, String encodedSymbol, int examineMethodResult) {
            String symbol = record.getScheme().getCodec().decode(cipher, encodedSymbol);
            if (!matches(record, symbol))
                return;
            out.println("HistoryBuffer examined=" + examineMethodResult);
        }
    }
}
