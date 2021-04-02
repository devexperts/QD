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
import com.devexperts.qd.QDStream;
import com.devexperts.qd.SymbolCodec;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;

import javax.swing.table.AbstractTableModel;

/**
 * The <code>StreamModel</code> shows last 20 values from QDStream.
 */
public class StreamModel extends AbstractTableModel implements RecordListener, ActivatableModel {
    private static final int MAX_RECS = 20;

    private final SymbolCodec codec;
    private final QDStream stream;
    private final QDAgent agent;

    private final RecordBuffer buffer = new RecordBuffer();
    private final long[] positions = new long[MAX_RECS];

    private final GUIColumn[] columns;

    private final String[] symbols; // For subscription only.

    public StreamModel(QDStream stream, GUIColumn[] columns, String[] symbols) {
        this.codec = stream.getScheme().getCodec();
        this.stream = stream;
        this.columns = columns;
        this.symbols = symbols;

        agent = stream.agentBuilder().build();
        agent.setRecordListener(this);

        setActive(true);
    }

    public void setActive(boolean active) {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        if (active)
            for (int i = 0; i < symbols.length; i++)
                for (int j = 0; j < columns.length; j++)
                    sub.add(columns[j].getRecord(), codec.encode(symbols[i]), symbols[i]);
        agent.setSubscription(sub);
        sub.release();
    }

    // ========== DataListener Implementation ==========

    public void recordsAvailable(RecordProvider provider) {
        provider.retrieve(buffer);
        // advance position so that at most MAX_RECS last remain
        buffer.rewind();
        for (int i = buffer.size() - MAX_RECS; --i >= 0;)
            buffer.next();
        buffer.compact();
        // rebuild positions
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
        int index = buffer.size() - 1 - row; // Reverse order.
        RecordCursor cur = buffer.cursorAt(positions[index]);
        if (column == 0)
            return codec.decode(cur.getCipher(), cur.getSymbol());
        return columns[column - 1].getValue(cur);
    }
}
