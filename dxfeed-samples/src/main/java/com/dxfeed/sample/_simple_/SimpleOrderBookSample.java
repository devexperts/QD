/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.sample._simple_;

import com.dxfeed.api.DXFeed;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderSource;
import com.dxfeed.event.market.Side;
import com.dxfeed.model.IndexedEventTxModel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * This sample demonstrates how to reconstruct the order book using the {@link IndexedEventTxModel}.
 */
public class SimpleOrderBookSample {
    public static void main(String[] args) throws InterruptedException {
        if (args.length < 2) {
            System.err.println("usage: SimpleOrderBookSample <symbol> <source> [<limit>] [<period>]");
            System.err.println("where: <symbol> is the security symbol to get events for (e.g. \"AAPL,IBM\")");
            System.err.println("       <source> is the order source (e.g. \"ntv,dex\")");
            System.err.println("example: AAPL ntv");
            return;
        }

        String symbol = args[0];
        OrderSource source = OrderSource.valueOf(args[1]);

        OrderListener listener = new OrderListener();
        IndexedEventTxModel<Order> model = IndexedEventTxModel.newBuilder(Order.class) // create model
            .withFeed(DXFeed.getInstance())
            .withSymbol(symbol)
            .withSource(source)
            .withListener(listener)
            .build();

        int depthLimit = 5;
        while (true) {
            printBook(listener.getBook(), depthLimit); // prints an order book every second
            Thread.sleep(1000);
        }
    }

    private static class OrderBook {
        private final List<Order> buyOrders;
        private final List<Order> sellOrders;

        public OrderBook(List<Order> buyOrders, List<Order> sellOrders) {
            this.buyOrders = buyOrders;
            this.sellOrders = sellOrders;
        }
    }

    private static class OrderListener implements IndexedEventTxModel.Listener<Order> {
        // Map that stores orders by their unique index
        private final Map<Long, Order> ordersByIndex = new HashMap<>();
        // Tree set for storing displayed buy orders
        private final Set<Order> buyOrders = new TreeSet<>(
            Comparator.comparingDouble(Order::getPrice)
            .reversed() // descending order
            .thenComparingLong(Order::getIndex)); // sort by index if prices are equal
        // Tree set for storing displayed sell orders
        private final Set<Order> sellOrders = new TreeSet<>(
            Comparator.comparingDouble(Order::getPrice)
            .thenComparingLong(Order::getIndex)); // sort by index if prices are equal

        @Override
        public synchronized void eventsReceived(List<Order> events, boolean isSnapshot) {
            if (isSnapshot) {  // if this is a snapshot, clear the order book
                ordersByIndex.clear();
                buyOrders.clear();
                sellOrders.clear();
            }

            for (Order order : events) {
                Order oldOrder = ordersByIndex.remove(order.getIndex()); // remove the old order if it exists
                if (oldOrder != null)
                    selectSideSet(oldOrder).remove(oldOrder); // remove the old order from the appropriate side
                if ((order.getEventFlags() & IndexedEvent.REMOVE_EVENT) != 0) // skip removed events
                    continue;
                ordersByIndex.put(order.getIndex(), order);
                if (shallAddToBook(order))
                    selectSideSet(order).add(order); //add the order to the appropriate side if needed
            }
        }

        public synchronized OrderBook getBook() {
            return new OrderBook(new ArrayList<>(buyOrders), new ArrayList<>(sellOrders));
        }

        private boolean shallAddToBook(Order order) {
            return order.hasSize();
        }

        private Set<Order> selectSideSet(Order order) {
            return (order.getOrderSide() == Side.BUY) ? buyOrders : sellOrders;
        }
    }

    private static void printBook(OrderBook book, int depthLimit) {
        if (book.buyOrders.isEmpty() && book.sellOrders.isEmpty())
            return;

        StringBuilder sb = new StringBuilder();

        Iterator<Order> buyIterator = book.buyOrders.iterator();
        Iterator<Order> sellIterator = book.sellOrders.iterator();
        int limit = Math.min(Math.max(book.buyOrders.size(), book.sellOrders.size()), depthLimit);
        for (int i = 0; i < limit && (buyIterator.hasNext() || sellIterator.hasNext()); i++) {
            String buyTable = getOrderInfo(buyIterator, Side.BUY);
            String sellTable = getOrderInfo(sellIterator, Side.SELL);
            sb.append(buyTable).append("\t").append(sellTable).append("\n");
        }

        System.out.println(sb);
    }

    private static String getOrderInfo(Iterator<Order> iterator, Side side) {
        if (iterator.hasNext()) {
            Order order = iterator.next();
            return String.format("%s [Source: %s, Size: %8.4f, Price: %8.2f]",
                side, order.getSource(), order.getSizeAsDouble(), order.getPrice());
        } else {
            return String.format("%s [None]", side);
        }
    }
}
