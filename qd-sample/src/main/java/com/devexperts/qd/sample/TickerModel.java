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
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;

import javax.swing.table.AbstractTableModel;

/**
 * The <code>TickerModel</code> shows last values from QDTicker.
 */
public class TickerModel extends AbstractTableModel implements RecordListener, ActivatableModel {
    private final QDTicker ticker;
    private final QDAgent agent;

    private final GUIColumn[] columns;
    private final int[] ciphers;
    private final String[] symbols;

    public TickerModel(QDTicker ticker, GUIColumn[] columns, String[] symbols) {
        this.ticker = ticker;
        this.columns = columns;
        this.symbols = symbols;

        ciphers = new int[symbols.length];
        for (int i = 0; i < symbols.length; i++)
            ciphers[i] = ticker.getScheme().getCodec().encode(symbols[i]);

        agent = ticker.agentBuilder().build();
        agent.setRecordListener(this);
        setActive(true);
    }

    public void setActive(boolean active) {
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        if (active)
            for (int i = 0; i < symbols.length; i++)
                for (int j = 0; j < columns.length; j++)
                    sub.add(columns[j].getRecord(), ciphers[i], symbols[i]);
        agent.setSubscription(sub);
        sub.release();
    }

    // ========== DataListener Implementation ==========


    public void recordsAvailable(RecordProvider provider) {
        provider.retrieve(RecordSink.VOID);
        if (getRowCount() > 0)
            fireTableRowsUpdated(0, getRowCount() - 1);
    }

    // ========== TableModel Implementation ==========

    public int getRowCount() {
        return symbols.length;
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
            return symbols[row];
        return columns[column - 1].getValue(ticker, ciphers[row], symbols[row]);
    }
}
