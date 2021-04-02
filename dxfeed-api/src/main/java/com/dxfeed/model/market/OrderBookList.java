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
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Scope;
import com.dxfeed.model.ObservableListModel;
import com.dxfeed.model.ObservableListModelListener;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implements buy and sell order books with filtering out lower scope order events.
 */
class OrderBookList extends CheckedTreeList<Order> implements ObservableListModel<Order> {
    // turn on to to debug (validate tree after every change and log changes)
    private static final boolean DEBUG_TREE = false; // SET TO FALSE IN PRODUCTION CODE (!!!)

    private static class MMIDEntry {
        final String mmid;
        Node<Order> aggregate; // Scope.AGGREGATE
        int orderCount; // Number of orders with Scope.ORDER

        MMIDEntry(String mmid) {
            this.mmid = (mmid == null) ? "" : mmid;
        }
    }

    private volatile boolean closed;
    private final List<ObservableListModelListener<? super Order>> listeners = new CopyOnWriteArrayList<>();

    private OrderBookModelFilter filter;
    private boolean modelChanged;

    private final ObservableListModelListener.Change<Order> change = new ObservableListModelListener.Change<>(this);

    // Nodes for orders of different scopes for easy filtration

    // Scope.COMPOSITE
    private Node<Order> composite;
    // Number of orders with scope higher than Scope.COMPOSITE
    private int nonCompositeCount;
    @SuppressWarnings("unchecked")
    private Node<Order>[] regionals = new Node[128]; // for all exchanges
    @SuppressWarnings("unchecked")
    private IndexedSet<String, MMIDEntry>[] aggregates = new IndexedSet[128]; // for all exchanges

    protected OrderBookList(Comparator<? super Order> c) {
        super(c);
    }

    void close() {
        closed = true;
        listeners.clear();
    }

    // ObservableListModel<Order> Interface Implementation

    @Override
    public void addListener(ObservableListModelListener<? super Order> listener) {
        if (closed)
            return;
        listeners.add(listener);
    }

    @Override
    public void removeListener(ObservableListModelListener<? super Order> listener) {
        listeners.remove(listener);
    }

    // Utility methods

    protected void setFilter(OrderBookModelFilter filter) {
        this.filter = filter;
    }

    @Override
    public void clear() {
        clearFilters();
        super.clear();
        modelChanged = true;
    }

    protected void clearFilters() {
        composite = null;
        nonCompositeCount = 0;
        for (int i = 0; i < 128; i++) {
            regionals[i] = null;
            if (aggregates[i] != null)
                aggregates[i].clear();
        }
    }

    // It is used in tests only (in actual usage existing nodes are reinserted)
    protected Node<Order> insertOrder(Order value) {
        return insertOrderNode(new Node<>(value));
    }

    protected Node<Order> insertOrderNode(Node<Order> node) {
        insertNode(node);
        Node<Order> oldNode = checkNode(node);
        if (oldNode != null && uncheck(oldNode)) {
            if (oldNode.getValue().getScope() != Scope.COMPOSITE)
                nonCompositeCount--;
        }
        modelChanged = true;
        if (DEBUG_TREE)
            validateTree();
        return node;
    }

    protected void deleteOrderNode(Node<Order> node) {
        if (node.isRemoved())
            return; // nothing to do on removed node
        Node<Order> newNode = uncheckNode(node);
        deleteNode(node);
        if (newNode != null && check(newNode)) {
            if (newNode.getValue().getScope() != Scope.COMPOSITE)
                nonCompositeCount++;
        }
        if (DEBUG_TREE)
            validateTree();
        modelChanged = true;
    }

    protected Node<Order> updateOrderNode(Node<Order> node) {
        deleteOrderNode(node);
        return insertOrderNode(node);
    }

    protected void beginChange() {
        modelChanged = false;
    }

    protected boolean endChange() {
        if (!modelChanged)
            return false;
        fireModelChanged();
        return true;
    }

    protected void fireModelChanged() {
        for (ObservableListModelListener<? super Order> listener : listeners)
            listener.modelChanged(change);
    }

    // Check the node and return previous scope node that must be unchecked, or null.
    protected Node<Order> checkNode(Node<Order> node) {
        Scope scope = node.getValue().getScope();
        if (!filter.allowScope(scope))
            return null;
        if (scope == Scope.COMPOSITE) {
            composite = node;
            if (nonCompositeCount == 0)
                check(node);
            return null;
        }
        char exchangeCode = node.getValue().getExchangeCode();
        IndexedSet<String, MMIDEntry> aggregate = aggregates[exchangeCode];
        if (scope == Scope.REGIONAL) {
            regionals[exchangeCode] = node;
            if (aggregate == null || aggregate.isEmpty()) {
                check(node);
                nonCompositeCount++;
            }
            return composite;
        }
        // Create entry for exchange and market-maker if needed
        if (aggregate == null) {
            aggregate = IndexedSet.create((IndexerFunction<String, MMIDEntry>) entry -> entry.mmid);
            aggregates[exchangeCode] = aggregate;
        }
        String marketMaker = node.getValue().getMarketMaker();
        MMIDEntry entry = aggregate.getByKey(marketMaker);
        if (entry == null)
            entry = aggregate.putIfAbsentAndGet(new MMIDEntry(marketMaker));
        Node<Order> regional = regionals[exchangeCode];
        if (scope == Scope.AGGREGATE) {
            entry.aggregate = node;
            if (entry.orderCount == 0) {
                check(node);
                nonCompositeCount++;
            }
            return (regional != null) ? regional : composite;
        }
        if (scope == Scope.ORDER) {
            entry.orderCount++;
            check(node);
            nonCompositeCount++;
            return (entry.aggregate != null) ? entry.aggregate :
                (regional != null) ? regional : composite;
        }
        return null;
    }

    // Uncheck the node and return the previous scope node that must be checked, or null
    protected Node<Order> uncheckNode(Node<Order> node) {
        boolean checked = uncheck(node);
        Scope scope = node.getValue().getScope();
        if (scope == Scope.COMPOSITE) {
            composite = null;
            return null;
        }
        // Decrement count for non-composite scope orders
        if (checked) {
            assert (nonCompositeCount > 0);
            nonCompositeCount--;
        }
        char exchangeCode = node.getValue().getExchangeCode();
        if (scope == Scope.REGIONAL) {
            regionals[exchangeCode] = null;

            return !checked ? null :
                (nonCompositeCount == 0) ? composite : null;
        }
        // Find entry for exchange and market-maker
        IndexedSet<String, MMIDEntry> aggregate = aggregates[exchangeCode];
        if (aggregate == null)
            return null;
        String marketMaker = node.getValue().getMarketMaker();
        MMIDEntry entry = aggregate.getByKey(marketMaker);
        if (entry == null)
            return null;
        Node<Order> regional = regionals[exchangeCode];
        if (scope == Scope.AGGREGATE) {
            entry.aggregate = null;
            if (entry.orderCount == 0)
                aggregate.removeKey(marketMaker);
            return !checked ? null :
                (regional != null) ? regional :
                (nonCompositeCount == 0) ? composite : null;
        }
        if (scope == Scope.ORDER) {
            if (checked)
                entry.orderCount--;
            assert (entry.orderCount >= 0);
            if (entry.orderCount == 0 && entry.aggregate == null)
                aggregate.removeKey(marketMaker);
            return !checked ? null :
                (entry.orderCount > 0) ? null :
                (entry.aggregate != null) ? entry.aggregate :
                (regional != null) ? regional :
                (nonCompositeCount == 0) ? composite : null;
        }
        return null;
    }
}
