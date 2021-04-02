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

import com.dxfeed.event.market.TimeAndSale;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.event.TableModelEvent;

class TimeAndSalesTableModel extends EventTableModel<TimeAndSale> {
    static final TimeAndSale CLEAR = new TimeAndSale();

    private static final Comparator<TimeAndSale> TIME_AND_SALES_BY_EVENT_ID = (t1, t2) -> -Long.compare(t1.getIndex(), t2.getIndex());

    private final TreeSet<TimeAndSale> timeAndSales = new TreeSet<>(TIME_AND_SALES_BY_EVENT_ID);
    private final Set<TimeAndSale> updatedTimeAndSales = new HashSet<>();

    private int maxCapacity;

    TimeAndSalesTableModel(int maxCapacity) {
        super(TimeAndSalesTableColumn.values());
        this.maxCapacity = maxCapacity;
    }

    @Override
    public void eventsReceived(List<TimeAndSale> timeAndSaleEvents) {
        if (frozen) return;

        boolean updated = false;
        boolean isComplexUpdate = false;
        for (TimeAndSale timeAndSale : timeAndSaleEvents) {
            updated = true;
            if (timeAndSale == CLEAR) {
                timeAndSales.clear();
                updatedTimeAndSales.clear();
                isComplexUpdate = true;
                continue;
            }

            timeAndSales.add(timeAndSale);
            updatedTimeAndSales.add(timeAndSale);
            isComplexUpdate |= (timeAndSales.first() != timeAndSale);
        }

        if (!updated) {
            clearUpdates();
            return;
        }

        int removed = Math.max(0, timeAndSales.size() - maxCapacity);
        while (timeAndSales.size() > maxCapacity)
            timeAndSales.remove(timeAndSales.last());

        events.clear();
        events.addAll(timeAndSales);

        isUpdated.clear();
        isDisabled.clear();
        tags.clear();
        for (TimeAndSale timeAndSale : events) {
            isUpdated.add(updatedTimeAndSales.contains(timeAndSale));
            tags.add(0);
            isDisabled.add(false);
        }

        if (isComplexUpdate) {
            fireTableChanged(new TableModelEvent(this));
        } else {
            fireTableChanged(new TableModelEvent(this, 0, updatedTimeAndSales.size() - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.INSERT));
            if (removed > 0)
                fireTableChanged(new TableModelEvent(this, maxCapacity, maxCapacity + removed - 1, TableModelEvent.ALL_COLUMNS, TableModelEvent.DELETE));
        }
        updatedTimeAndSales.clear();
    }
}
