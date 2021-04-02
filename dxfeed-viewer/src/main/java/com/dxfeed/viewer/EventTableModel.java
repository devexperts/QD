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

import com.dxfeed.event.market.MarketEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

abstract class EventTableModel<E extends MarketEvent> implements TableModel {
    protected final ArrayList<E> events = new ArrayList<E>();
    protected final ArrayList<Boolean> isUpdated = new ArrayList<Boolean>();
    protected final ArrayList<Integer> tags = new ArrayList<Integer>();
    protected final ArrayList<Boolean> isDisabled = new ArrayList<Boolean>();

    protected int scheme = ViewerCellRenderer.DEFAULT_SCHEME;

    protected TimeZone timeZone = TimeZone.getDefault();

    protected boolean frozen = false;

    private final Set<TableModelListener> modelListeners = new HashSet<TableModelListener>();
    public final EventTableColumn<E>[] columns;

    protected EventTableModel(EventTableColumn<E>[] columns) {
        this.columns = columns;
    }

    // -------------------- Events processing --------------------

    public abstract void eventsReceived(List<E> events);

    protected void fireTableChanged(TableModelEvent event) {
        synchronized (modelListeners) {
            for (TableModelListener listener : modelListeners)
                listener.tableChanged(event);
        }
    }

    protected void clearUpdates() {
        int minIndex = Integer.MAX_VALUE;
        int maxIndex = Integer.MIN_VALUE;
        for (int i = 0; i < isUpdated.size(); i++)
            if (isUpdated.get(i)) {
                minIndex = Math.min(minIndex, i);
                maxIndex = Math.max(maxIndex, i);
                isUpdated.set(i, false);
            }

        if (minIndex != Integer.MAX_VALUE)
            fireTableChanged(new TableModelEvent(this, minIndex, maxIndex));
    }

    // -------------------- TableModel implementation --------------------

    public int getRowCount() {
        return events.size();
    }

    public int getColumnCount() {
        return columns.length;
    }

    public String getColumnName(int columnIndex) {
        return columns[columnIndex].getCaption();
    }

    public Class<?> getColumnClass(int columnIndex) {
        return Object.class;
    }

    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    TimeZone getTimeZone() {
        return timeZone;
    }

    void setTimeZone(TimeZone timeZone) {
        this.timeZone = timeZone;
        fireTableChanged(new TableModelEvent(this));
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        return columns[columnIndex].getValue(events.get(rowIndex), isUpdated.get(rowIndex), isDisabled.get(rowIndex), tags.get(rowIndex), scheme, timeZone);
    }

    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        throw new UnsupportedOperationException();
    }

    public void addTableModelListener(TableModelListener l) {
        modelListeners.add(l);
    }

    public void removeTableModelListener(TableModelListener l) {
        modelListeners.remove(l);
    }

    public int getScheme() {
        return scheme;
    }

    public void setScheme(int scheme) {
        this.scheme = scheme;
        fireTableChanged(new TableModelEvent(this));
    }

    boolean isFrozen() {
        return frozen;
    }

    void setFrozen(boolean frozen) {
        this.frozen = frozen;
    }
}
