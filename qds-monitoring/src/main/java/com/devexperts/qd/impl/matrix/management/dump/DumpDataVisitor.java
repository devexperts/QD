/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.matrix.management.dump;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.impl.matrix.Collector;
import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordCursor;

class DumpDataVisitor implements CollectorVisitor {
    private final String filterSymbol;
    private final String filterRecord;

    DumpDataVisitor(String filterSymbol, String filterRecord) {
        this.filterSymbol = filterSymbol;
        this.filterRecord = filterRecord;
    }

    public void visit(Collector collector) {
        System.out.println("--- Data from " + collector);
        collector.examineData(new DumpDataSink(filterSymbol, filterRecord));
    }

    static class DumpDataSink extends AbstractRecordSink {
        private final String filterSymbol;
        private final String filterRecord;

        DumpDataSink(String filterSymbol, String filterRecord) {
            this.filterSymbol = filterSymbol;
            this.filterRecord = filterRecord;
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
                for (int i = 0; i < cursor.getIntCount(); i++) {
                    System.out.print('\t');
                    System.out.print(r.getIntField(i).toString(cursor.getInt(i)));
                }
                for (int i = 0; i < cursor.getObjCount(); i++) {
                    System.out.print('\t');
                    System.out.print(r.getObjField(i).toString(cursor.getObj(i)));
                }
                System.out.println();
            }
        }
    }
}
