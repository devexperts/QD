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
package com.dxfeed.viewer;

import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;
import com.dxfeed.event.market.MarketEvent;
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Summary;
import com.dxfeed.event.market.Trade;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

class QuoteBoardTableModel implements TableModel {
    private final ArrayList<QuoteBoardTableRow> rows = new ArrayList<QuoteBoardTableRow>();
    private final IndexedSet<String, QuoteBoardTableRow> rowsBySymbol = IndexedSet.create((IndexerFunction <String, QuoteBoardTableRow>) row -> row.symbol);
    private final Set<TableModelListener> modelListeners = new HashSet<TableModelListener>();
    private final SubscriptionChangeListener subscriptionChangeListener;

    private TimeZone timeZone = TimeZone.getDefault();

    private boolean frozen = false;

    QuoteBoardTableModel(SubscriptionChangeListener subscriptionChangeListener) {
        this.subscriptionChangeListener = subscriptionChangeListener;
    }

    public void setSymbols(String symbolsStr) {
        clearInternal();
        if (symbolsStr == null || symbolsStr.isEmpty())
            return;
        for (String symbol : symbolsStr.split(","))
            addRowInternal(rows.size(), symbol);
        fireTableChanged(new TableModelEvent(this));
    }

    public String getSymbols() {
        if (rows.size() == 0)
            return "";
        StringBuilder result = new StringBuilder();
        for (QuoteBoardTableRow row : rows)
            result.append(row.symbol).append(',');
        result.setLength(result.length() - 1);
        return result.toString();
    }

    public void clear() {
        clearInternal();
        fireTableChanged(new TableModelEvent(this));
    }

    public void addRow(int rowIndex) {
        addRowInternal(rowIndex, "");
        fireTableChanged(new TableModelEvent(this, rowIndex, rowIndex, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT));
    }

    public QuoteBoardTableRow getRowAt(int rowIndex) {
        return rows.get(rowIndex);
    }

    public void removeRows(int[] rowIndexes) {
        if (rowIndexes.length == 0)
            return;
        Arrays.sort(rowIndexes);
        for (int i = rowIndexes.length - 1; i >= 0; i--)
            removeRowInternal(rowIndexes[i]);
        fireTableChanged(new TableModelEvent(this, rowIndexes[0], rowIndexes[rowIndexes.length - 1], TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE));
    }

    public void swapRows(int row1, int row2) {
        if (row1 == row2)
            return;
        QuoteBoardTableRow row = rows.get(row1);
        rows.set(row1, rows.get(row2));
        rows.set(row2, row);
        updateRowIndexes();
        fireTableChanged(new TableModelEvent(this, Math.min(row1, row2), Math.max(row1, row2)));
    }

    private void clearInternal() {
        while (!rows.isEmpty())
            removeRowInternal(rows.size() - 1);
    }

    private void addRowInternal(int rowIndex, String symbol) {
        QuoteBoardTableRow row = rowsBySymbol.getByKey(symbol);
        if (row == null) {
            row = new QuoteBoardTableRow(symbol);
            rowsBySymbol.add(row);
            if (symbol.length() > 0)
                subscriptionChangeListener.addSymbol(symbol);
        }
        rows.add(rowIndex, row);
        updateRowIndexes();
    }

    private void removeRowInternal(int rowIndex) {
        QuoteBoardTableRow row = rows.remove(rowIndex);
        if (row.indexes.size() < 2) {
            rowsBySymbol.removeValue(row);
            if (row.symbol.length() > 0)
                subscriptionChangeListener.removeSymbol(row.symbol);
        }
        updateRowIndexes();
    }

    private void updateRowIndexes() {
        for (QuoteBoardTableRow row : rowsBySymbol)
            row.indexes.clear();
        for (int i = 0; i < rows.size(); i++)
            rows.get(i).indexes.add(i);
    }

    private void fireTableChanged(TableModelEvent event) {
        for (TableModelListener listener : modelListeners)
            listener.tableChanged(event);
    }

    // -------------------- Events processing --------------------

    private int prevMinIndex = Integer.MAX_VALUE;
    private int prevMaxIndex = Integer.MIN_VALUE;

    public void eventsReceived(List<? extends MarketEvent> events) {
        if (frozen) return;

        long curTime = System.currentTimeMillis();

        int minRowIndex = Integer.MAX_VALUE;
        int maxRowIndex = Integer.MIN_VALUE;

        for (MarketEvent event : events) {
            QuoteBoardTableRow row;
            row = rowsBySymbol.getByKey(event.getEventSymbol());
            if (row == null)
                continue;
            Class<? extends MarketEvent> eventClass = event.getClass();
            if (eventClass == Quote.class)
                row.updateQuote((Quote) event, curTime);
            else if (eventClass == Trade.class)
                row.updateTrade((Trade) event, curTime);
            else if (eventClass == Summary.class)
                row.updateSummary((Summary) event, curTime);
            else if (eventClass == Profile.class)
                row.updateProfile((Profile) event);

            for (int index : row.indexes) {
                minRowIndex = Math.min(minRowIndex, index);
                maxRowIndex = Math.max(maxRowIndex, index);
            }
        }

        int oldMinIndex = prevMinIndex;
        int oldMaxIndex = prevMaxIndex;
        prevMinIndex = minRowIndex;
        prevMaxIndex = maxRowIndex;
        minRowIndex = Math.min(minRowIndex, oldMinIndex);
        maxRowIndex = Math.max(maxRowIndex, oldMaxIndex);
        if (minRowIndex != Integer.MAX_VALUE)
            fireTableChanged(new TableModelEvent(this, minRowIndex, maxRowIndex));
    }

    // -------------------- TableModel implementation --------------------

    public int getRowCount() {
        return rows.size();
    }

    public int getColumnCount() {
        return QuoteBoardTableColumn.values().length;
    }

    public String getColumnName(int columnIndex) {
        return QuoteBoardTableColumn.values()[columnIndex].caption;
    }

    public Class<?> getColumnClass(int columnIndex) {
        return Object.class;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == QuoteBoardTableColumn.SYMBOL.ordinal();
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        return QuoteBoardTableColumn.values()[columnIndex].getValue(rows.get(rowIndex), timeZone);
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex != QuoteBoardTableColumn.SYMBOL.ordinal())
            return;
        String symbol = (String) aValue;
        if (symbol.equals(symbol.toLowerCase()))
            symbol = symbol.toUpperCase();

        removeRowInternal(rowIndex);
        addRowInternal(rowIndex, symbol);
        fireTableChanged(new TableModelEvent(this, rowIndex));
    }

    public void addTableModelListener(TableModelListener l) {
        modelListeners.add(l);
    }

    public void removeTableModelListener(TableModelListener l) {
        modelListeners.remove(l);
    }

    boolean isFrozen() {
        return frozen;
    }

    void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }

    TimeZone getTimeZone() {
        return timeZone;
    }

    void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
        fireTableChanged(new TableModelEvent(this));
    }
}
