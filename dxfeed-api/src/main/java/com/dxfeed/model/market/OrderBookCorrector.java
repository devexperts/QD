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
package com.dxfeed.model.market;

import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;
import com.devexperts.util.SynchronizedIndexedSet;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedEventListener;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Scope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Model that corrects Order Book errors.
 */
public class OrderBookCorrector {

    private static final class Book {
        final String symbol;
        final SynchronizedIndexedSet<Long, Order> orders = SynchronizedIndexedSet.createLong(Order::getIndex);

        Book(String symbol) {
            this.symbol = symbol;
        }
    }

    private final DXFeedSubscription<Order> subscription = new DXFeedSubscription<>(Order.class);
    private final SynchronizedIndexedSet<String, Book> data = SynchronizedIndexedSet.create((IndexerFunction<String, Book>) book -> book.symbol);
    private final List<DXFeedEventListener<Order>> listeners = new CopyOnWriteArrayList<>();

    private final long keepTTL;
    private final long flipTTL;

    /**
     * Creates new model with default parameters and attaches it to specified data feed.
     */
    public OrderBookCorrector(DXFeed feed) {
        this(feed, 24 * 60 * 60 * 1000L, 60 * 1000L);
    }

    /**
     * Creates new model with specified parameters and attaches it to specified data feed.
     */
    public OrderBookCorrector(DXFeed feed, long keepTTL, long flipTTL) {
        this.keepTTL = keepTTL;
        this.flipTTL = flipTTL;
        feed.attachSubscription(subscription);
        subscription.addEventListener(new DXFeedEventListener<Order>() {
            @Override
            public void eventsReceived(List<Order> events) {
                processEvents(events);
            }
        });
    }

    OrderBookCorrector(long keepTTL, long flipTTL, String symbol) {
        this.keepTTL = keepTTL;
        this.flipTTL = flipTTL;
        setSymbols(symbol);
    }

    /**
     * Closes this model and makes it permanently detached from data feed.
     */
    public void close() {
        subscription.close();
        data.clear();
        listeners.clear();
    }

    /**
     * Changes the set of subscribed symbols so that it contains just the symbols from the specified collection.
     *
     * @param symbols the collection of symbols.
     */
    public void setSymbols(Collection<String> symbols) {
        for (String symbol : symbols)
            data.putIfAbsentAndGet(new Book(symbol));
        IndexedSet<String, String> set = new IndexedSet<>(symbols);
        for (Iterator<Book> it = data.iterator(); it.hasNext();)
            if (!set.containsKey(it.next().symbol))
                it.remove();
        subscription.setSymbols(symbols);
    }

    /**
     * Changes the set of subscribed symbols so that it contains just the symbols from the specified array.
     *
     * @param symbols the array of symbols.
     */
    public void setSymbols(String... symbols) {
        setSymbols(Arrays.asList(symbols));
    }

    /**
     * Adds listener for events.
     * Newly added listeners start receiving only new events.
     *
     * @param listener the event listener.
     * @throws NullPointerException if listener is null.
     */
    public void addEventListener(DXFeedEventListener<Order> listener) {
        if (listener == null)
            throw new NullPointerException();
        listeners.add(listener);
    }

    /**
     * Removes listener for events.
     *
     * @param listener the event listener.
     * @throws NullPointerException if listener is null.
     */
    public void removeEventListener(DXFeedEventListener<Order> listener) {
        if (listener == null)
            throw new NullPointerException();
        listeners.remove(listener);
    }

    // ========== Implementation ==========

    // returns "true" if this order is accepted without changes,
    // always true for removals (zero size) and accepts "corrections == null" for removals
    // may add orders with "size == 0" (only!) to corrections when some other orders needs to be removed
    boolean acceptEvent(Order order, List<Order> corrections) {
        Book book = data.getByKey(order.getEventSymbol());
        if (book == null)
            return true;
        if (order.getScope() != Scope.AGGREGATE)
            return true;
        if (!order.hasSize()) {
            book.orders.removeKey(order.getIndex());
            return true;
        }
        for (Order better : book.orders)
            if (better.getIndex() != order.getIndex() && better.getSizeAsDouble() > 0 &&
                (sameSlot(better, order) && better.getTime() > order.getTime()) ||
                better.getTime() > order.getTime() + keepTTL ||
                bidAskFlip(better, order) && better.getTime() > order.getTime() + flipTTL)
            {
                Order old = book.orders.put(copy(order, -order.getSizeAsDouble()));
                if (old != null && old.getSizeAsDouble() > 0)
                    corrections.add(copy(order, 0));
                return false;
            }
        Order old = book.orders.getByKey(order.getIndex());
        if (old != null && old.getSizeAsDouble() < 0 && old.getOrderSide() == order.getOrderSide() && old.getPrice() == order.getPrice() && old.getTime() >= order.getTime())
            return false;
        for (Order worse : book.orders)
            if (worse.getIndex() != order.getIndex() && worse.getSizeAsDouble() > 0 &&
                (sameSlot(worse, order) && worse.getTime() <= order.getTime()) ||
                worse.getTime() <= order.getTime() - keepTTL ||
                bidAskFlip(worse, order) && worse.getTime() <= order.getTime() - flipTTL)
            {
                book.orders.put(copy(worse, -worse.getSizeAsDouble())); // in situ replacement is not ConcurrentModification
                corrections.add(copy(worse, 0));
            }
        book.orders.put(order);
        return true;
    }

    private void processEvents(List<Order> events) {
        List<Order> filtered = new ArrayList<>(events.size() + 10);
        for (Order order : events)
            if (acceptEvent(order, filtered))
                filtered.add(order);
        for (DXFeedEventListener<Order> listener : listeners)
            listener.eventsReceived(filtered);
    }

    private static boolean sameSlot(Order order1, Order order2) {
        return order1.getOrderSide() == order2.getOrderSide() && order1.getPrice() == order2.getPrice();
    }

    private static boolean bidAskFlip(Order order1, Order order2) {
        return order1.getOrderSide() != order2.getOrderSide() && Double.compare(order1.getPrice(), order2.getPrice()) * (order1.getOrderSide().getCode() - order2.getOrderSide().getCode()) <= 0;
    }

    static Order copy(Order old, double size) {
        Order order = new Order(old.getEventSymbol());
        order.setIndex(old.getIndex());
        order.setOrderSide(old.getOrderSide());
        order.setScope(old.getScope());
        order.setTime(old.getTime());
        order.setSequence(old.getSequence());
        order.setExchangeCode(old.getExchangeCode());
        order.setMarketMaker(old.getMarketMaker());
        order.setPrice(old.getPrice());
        order.setSizeAsDouble(size);
        return order;
    }
}
