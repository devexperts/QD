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

import com.dxfeed.event.market.Order;
import com.dxfeed.model.ObservableListModel;
import com.dxfeed.model.ObservableListModelListener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.event.TableModelEvent;

class OrderTableModel extends EventTableModel<Order> {

    private final ObservableListModel<Order> list;

    private final Set<Order> updatedOrders = new HashSet<Order>();
    private double maxSize = 0;

    double getMaxSize() {
        return maxSize;
    }

    OrderTableModel(final ObservableListModel<Order> list) {
        super(OrderTableColumn.values());
        this.list = list;

        list.addListener(new ObservableListModelListener<Order>() {
            public void modelChanged(Change<? extends Order> change) {
                processOrders(change.getSource());
            }
        });
    }

    private boolean isTrashPrice(double price, double topPrice) {
        double percentDiff = Math.abs((price - topPrice) / topPrice);
        return (topPrice < 1 && percentDiff > 0.9) ||
            (topPrice >= 1 && topPrice < 2 && percentDiff > 0.8) ||
            (topPrice >= 2 && topPrice < 5 && percentDiff > 0.7) ||
            (topPrice >= 5 && topPrice < 15 && percentDiff > 0.4) ||
            (topPrice >= 15 && topPrice < 50 && percentDiff > 0.25) ||
            (topPrice >= 50 && topPrice < 100 && percentDiff > 0.2) ||
            (topPrice >= 100 && topPrice < 200 && percentDiff > 0.10) ||
            (topPrice >= 200 && topPrice < 500 && percentDiff > 0.05) ||
            (topPrice >= 500 && topPrice < 1000 && percentDiff > 0.04) ||
            (topPrice >= 1000 && topPrice < 10000 && percentDiff > 0.03) ||
            (topPrice >= 10000 && percentDiff > 0.02);
    }

    public void processOrders(List<? extends Order> orderEvents) {
        if (frozen)
            return;

        events.clear();
        isUpdated.clear();
        isDisabled.clear();
        tags.clear();

        double prevPrice = 0;
        int priceGroup = 0;
        maxSize = 0;

        double topPrice = 1;
        if (list.size() > 0)
            topPrice = list.get(0).getPrice();

        for (Order order : list) {
            events.add(order);
            isUpdated.add(updatedOrders.contains(order));

            if (prevPrice != order.getPrice())
                priceGroup++;
            tags.add(priceGroup);    // tag will contain priceGroup value for coloring same price groups / levels
            prevPrice = order.getPrice();

            // we only calculate max size for histogram zooming for first 15 price levels and watching percent diff from top price in the book to filter out the trash on lower levels
            boolean thisPriceIsTrash = isTrashPrice(order.getPrice(), topPrice);

            if (priceGroup <= 15 && !thisPriceIsTrash && order.getSizeAsDouble() > maxSize)
                maxSize = order.getSizeAsDouble();
            isDisabled.add(thisPriceIsTrash); // special disabled coloring for prices outside of filtering margins
        }

        updatedOrders.clear();
        for (Order order : orderEvents) {
            updatedOrders.add(order);
        }

        fireTableChanged(new TableModelEvent(this));
    }

    @Override
    public void eventsReceived(List<Order> orderEvents) {
        // Do not handle events since OrderBookModel listens to its own subscription
    }
}
