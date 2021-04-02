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
package com.devexperts.qd.sample;

import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;

import javax.swing.table.AbstractTableModel;

/**
 * The <code>HistoryModel</code> shows last 20 values from QDHistory.
 */
public class HistoryModel extends AbstractTableModel implements RecordListener, ActivatableModel {
    private static final int MAX_RECS = 20;

    private final SymbolCodec codec;
    private final QDHistory history;
    private final QDAgent agent;

    private final RecordBuffer buffer = new RecordBuffer();
    private final long[] positions = new long[MAX_RECS];

    private final GUIColumn[] columns;

    private final String symbol; // For subscription only.

    public HistoryModel(QDHistory history, GUIColumn[] columns, String symbol) {
        this.codec = history.getScheme().getCodec();
        this.history = history;
        this.columns = columns;
        this.symbol = symbol;

        agent = history.agentBuilder().build();
        agent.setRecordListener(this);

        setActive(true);
    }

    public void setActive(boolean active) {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.HISTORY_SUBSCRIPTION);
        if (active)
            sub.add(columns[0].getRecord(), codec.encode(symbol), symbol).
                setTime((System.currentTimeMillis() / 1000 - 20) << 32);
        agent.setSubscription(sub);
        sub.release();
    }

    // ========== DataListener Implementation ==========


    public void recordsAvailable(RecordProvider provider) {
        provider.retrieve(RecordSink.VOID);
        buffer.clear();
        history.examineData(columns[0].getRecord(), codec.encode(symbol), symbol, Long.MAX_VALUE, Long.MIN_VALUE, buffer);
        // advance position after MAX_RECS first
        for (int i = MAX_RECS; --i >= 0;)
            if (buffer.next() == null)
                break;
        buffer.setLimit(buffer.getPosition()); // trim buffer at position
        // rebuild positions
        buffer.rewind();
        for (int i = 0; i < buffer.size(); i++) {
            positions[i] = buffer.getPosition();
            buffer.next();
        }
        fireTableDataChanged();
    }

    // ========== TableModel Implementation ==========

    public int getRowCount() {
        return buffer.size();
    }

    public int getColumnCount() {
        return columns.length + 1;
    }

    @Override
    public String getColumnName(int column) {
        if (column == 0)
            return "SYMBOL";
        return columns[column - 1].getName();
    }

    public Object getValueAt(int row, int column) {
        if (column == 0)
            return symbol;
        RecordCursor cur = buffer.cursorAt(positions[row]);
        return columns[column - 1].getValue(cur);
    }
}
