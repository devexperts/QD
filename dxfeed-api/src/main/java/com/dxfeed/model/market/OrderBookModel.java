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

import com.devexperts.util.SystemProperties;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.DXFeed;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.event.IndexedEvent;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderSource;
import com.dxfeed.event.market.Scope;
import com.dxfeed.event.market.Side;
import com.dxfeed.model.AbstractIndexedEventModel;
import com.dxfeed.model.ObservableListModel;
import com.dxfeed.model.ObservableListModelListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

import static com.dxfeed.model.market.CheckedTreeList.Node;

/**
 * Model for convenient Order Book management.
 * This class handles all snapshot and transaction logic of {@link Order} event class and
 * arranges incoming orders into a lists of {@link #getBuyOrders() buyOrders} and
 * {@link #getSellOrders() sellOrders} that are, in turn, arranged by price, so that
 * the first element of the list (at index 0) is at the top of the corresponding side of the book.
 *
 * <p>Users of order book model only see the order book in a consistent state. This model delays incoming events which
 * are part of incomplete snapshot or ongoing transaction until snapshot is complete or transaction has ended.
 * These pending events cannot be seen neither via get methods of buy/sell order lists nor via listener calls, and so
 * {@link IndexedEvent#getEventFlags() eventFlags} of orders in the model are set to zero.
 * The eventFlags are only used and must be taken into account when processing orders directly via low-level
 * {@link DXFeedSubscription} class.
 *
 * <h3>Sample usage</h3>
 * The following code prints orders for "AAPL" to console whenever the book is updated:
 *
 * <pre><tt>
 * {@link DXFeed DXFeed} feed = ...;
 * OrderBookModel model = <b>new</b> {@link #OrderBookModel() OrderBookModel}();
 * model.{@link #setFilter(OrderBookModelFilter) setFilter}({@link OrderBookModelFilter OrderBookModelFilter}.{@link OrderBookModelFilter#ALL ALL});
 * model.{@link #setSymbol(String) setSymbol}("AAPL");
 * model.{@link #addListener(OrderBookModelListener) addListener}(<b>new</b> {@link OrderBookModelListener OrderBookModelListener}() {
 *     <b>public void</b> modelChanged({@link OrderBookModelListener.Change OrderBookModelListener.Change} change) {
 *         System.out.println("Buy orders:");
 *         <b>for</b> (Order order : model.{@link #getBuyOrders() getBuyOrders}())
 *             System.out.println(order);
 *         System.out.println("Sell orders:");
 *         <b>for</b> (Order order : model.{@link #getSellOrders() getSellOrders}())
 *             System.out.println(order);
 *         System.out.println();
 *     }
 * });
 * model.{@link #attach attach}(feed);
 * </tt></pre>
 *
 * dxFeed API comes with a set of samples. {@code DXFeedMarketDepth} sample is a very simple UI application
 * that shows how to use this model, concentrating your
 * effort on data representation logic, while delegating all the data-handling logic to this model.
 *
 * <h3>Resource management and closed models</h3>
 *
 * Attached model is a potential memory leak. If the pointer to attached model is lost, then there is no way to detach
 * this model from the feed and the model will not be reclaimed by the garbage collector as long as the corresponding
 * feed is still used. Detached model can be reclaimed by the garbage collector, but detaching model requires knowing
 * the pointer to the feed at the place of the call, which is not always convenient.
 *
 * <p> The convenient way to detach model from the feed is to call its {@link #close close} method. Closed model
 * becomes permanently detached from all feeds, removes all its listeners and is guaranteed to be reclaimable by
 * the garbage collector as soon as all external references to it are cleared.
 *
 * <h3><a name="threadsAndLocksSection">Threads and locks</a></h3>
 *
 * This class is <b>not</b> tread-safe and requires external synchronization.
 * The only thread-safe methods are {@link #attach attach}, {@link #detach detach} and {@link #close close}.
 * See {@link AbstractIndexedEventModel} class documentation for details and constrains on
 * the usage of this class.
 *
 * <p> Installed {@link ObservableListModelListener} instances are invoked from a separate thread via the executor.
 * Default executor for all models is configured with {@link DXEndpoint#executor(Executor) DXEndpoint.executor}
 * method. Each model can individually override its executor with {@link #setExecutor(Executor) setExecutor}
 * method. The corresponding
 * {@link ObservableListModelListener#modelChanged(ObservableListModelListener.Change) modelChanged}
 * notification is guaranteed to never be concurrent, even though it may happen from different
 * threads if executor is multi-threaded.
 *
 * <p>Custom executor can be used by backend applications that do not need to immediately update this model on
 * arrival of new events, but want to update the model at a later time, for example, from inside of a servlet request.
 * This approach is explained with code samples in
 * <a href="../../api/DXFeedSubscription.html#threadsAndLocksSection">Threads and locks</a>
 * section of {@link DXFeedSubscription} class documentation.
 */
public final class OrderBookModel implements AutoCloseable {

    // ================================== private instance fields ==================================

    private final IndexedOrderModel indexedEvents = new IndexedOrderModel();

    private final OrderBookList buyOrders = new OrderBookList(BUY_COMPARATOR);
    private final OrderBookList sellOrders = new OrderBookList(SELL_COMPARATOR);
    private final List<OrderBookModelListener> listeners = new CopyOnWriteArrayList<>();
    private final OrderBookModelListener.Change change = new OrderBookModelListener.Change(this);

    private OrderBookModelFilter filter = OrderBookModelFilter.ALL;
    private int lotSize = 1;

    private static final boolean CORRECT = SystemProperties.getBooleanProperty(OrderBookModel.class, "correct", false);
    private static final long KEEP_TTL = SystemProperties.getIntProperty(OrderBookModel.class, "keepTTL", 24 * 60 * 60)  * 1000L;
    private static final long FLIP_TTL = SystemProperties.getIntProperty(OrderBookModel.class, "flipTTL", 60)  * 1000L;

    private OrderBookCorrector corrector;

    // ================================== public instance methods & constructor ==================================

    /**
     * Creates new model. This model is not attached to any feed, not subscribed to any symbol,
     * and has filter set by default to {@link OrderBookModelFilter#ALL}.
     * Use {@link #setSymbol} to specify subscription symbol and
     * {@link #attach} method to specify feed to start receiving events.
     */
    public OrderBookModel() {
        buyOrders.setFilter(filter);
        sellOrders.setFilter(filter);
    }

    /**
     * Attaches model to the specified feed.
     * @param feed feed to attach to.
     */
    public void attach(DXFeed feed) {
        indexedEvents.attach(feed);
    }

    /**
     * Detaches model from the specified feed.
     * @param feed feed to detach from.
     */
    public void detach(DXFeed feed) {
        indexedEvents.detach(feed);
    }

    /**
     * Closes this model and makes it <i>permanently detached</i>.
     *
     * <p> This method ensures that model can be safely garbage-collected when all outside references to it are lost.
     */
    @Override
    public void close() {
        indexedEvents.close();
        buyOrders.close();
        sellOrders.close();
        listeners.clear();
    }

    /**
     * Returns executor for processing event notifications on this model.
     * See <a href="#threadsAndLocksSection">Threads and locks</a> section of this class documentation.
     * @return executor for processing event notifications on this model,
     *         or {@code null} if default executor of the attached {@link DXFeed} is used.
     */
    public Executor getExecutor() {
        return indexedEvents.getExecutor();
    }

    /**
     * Changes executor for processing event notifications on this model.
     * See <a href="#threadsAndLocksSection">Threads and locks</a> section of this class documentation.
     * @param executor executor for processing event notifications on this model,
     *         or {@code null} if default executor of the attached {@link DXFeed} is used.
     */
    public void setExecutor(Executor executor) {
        indexedEvents.setExecutor(executor);
    }

    /**
     * Clears subscription symbol and, subsequently, all events in this model.
     * This is a shortcut for <code>{@link #setSymbol(String) setSymbol}(<b>null</b>)</code>.
     */
    public void clear() {
        setSymbol(null);
    }

    /**
     * Returns filter for the model. This filter specifies which order events are shown in the book.
     * @see OrderBookModelFilter
     * @return model filter.
     */
    public OrderBookModelFilter getFilter() {
        return filter;
    }

    /**
     * Sets the specified filter to the model.
     * @param filter model filter
     */
    public void setFilter(OrderBookModelFilter filter) {
        if (filter == null)
            throw new IllegalArgumentException("filter is null");
        if (this.filter != filter) {
            this.filter = filter;
            buyOrders.setFilter(filter);
            sellOrders.setFilter(filter);
            updateAllOrders(1, 1);
        }
    }

    /**
     * Returns order book symbol, or {@code null} for empty subscription.
     * @return order book symbol.
     */
    public String getSymbol() {
        return (String) indexedEvents.getSymbol();
    }

    /**
     * Sets symbol for the order book to subscribe for.
     * @param symbol order book symbol, use {@code null} to unsubscribe.
     */
    public void setSymbol(String symbol) {
        if (Objects.equals(symbol, getSymbol()))
            return;
        if (CORRECT) {
            corrector = symbol != null && (symbol.startsWith("/") || symbol.startsWith("./") || symbol.startsWith("=")) ?
                new OrderBookCorrector(KEEP_TTL, FLIP_TTL, symbol) : null;
        }
        indexedEvents.setSymbol(symbol);
    }

    /**
     * Returns lot size. Lot size is a multiplier applied to {@link Scope#COMPOSITE},
     * {@link Scope#REGIONAL}, and {@link Scope#AGGREGATE} orders.
     * @return lot size
     */
    public int getLotSize() {
        return lotSize;
    }

    /**
     * Sets the lot size.
     * @see #getLotSize()
     * @param lotSize lot size multiplier
     */
    public void setLotSize(int lotSize) {
        if (lotSize < 1)
            throw new IllegalArgumentException("Invalid lot size: " + lotSize);
        if (this.lotSize != lotSize) {
            int oldLotSize = this.lotSize;
            this.lotSize = lotSize;
            updateAllOrders(lotSize, oldLotSize);
        }
    }

    /**
     * Returns the view of bid side (buy orders) of the order book.
     * This method returns the reference to the same object on each invocation.
     * The resulting list is immutable. It reflects the current list of orders
     * and is updated on arrival of new orders. See
     * <a href="#threadsAndLocksSection">Threads and locks</a> section for
     * details on concurrency of these updates.
     *
     * @return the view of bid side (buy orders) of the order book.
     */
    public ObservableListModel<Order> getBuyOrders() {
        return buyOrders;
    }

    /**
     * Returns the view of offer side (sell orders) of the order book.
     * This method returns the reference to the same object on each invocation.
     * The resulting list is immutable. It reflects the current list of orders
     * and is updated on arrival of new orders. See
     * <a href="#threadsAndLocksSection">Threads and locks</a> section for
     * details on concurrency of these updates.
     *
     * @return the view of offer side (sell orders) of the order book.
     */
    public ObservableListModel<Order> getSellOrders() {
        return sellOrders;
    }

    /**
     * Adds a listener to this order book model.
     *
     * <p><b>Note</b>, that the listener currently provides only information about the
     * source of the change (what model has changed) and does not actually provide
     * information about what orders in the model has changed.
     * The model has to be queried using lists of {@link #getBuyOrders() buy} and {@link #getSellOrders() sell}
     * orders to get the new set of active orders in the book.
     *
     * @param listener the listener for listening to the list changes.
     */
    public void addListener(OrderBookModelListener listener) {
        if (indexedEvents.isClosed())
            return;
        listeners.add(listener);
    }

    /**
     * Removes a listener from this order book model.
     * If the listener is not attached to this model, nothing happens.
     * @param listener the listener to remove.
     */
    public void removeListener(OrderBookModelListener listener) {
        listeners.remove(listener);
    }

    // ================================== private implementation details ==================================

    private static final Comparator<Order> ORDER_COMPARATOR = (o1, o2) -> {
        boolean ind1 = o1.getScope() == Scope.ORDER;
        boolean ind2 = o2.getScope() == Scope.ORDER;
        if (ind1 && ind2) {
            // Both orders are individual orders
            int c = compareLong(o1.getTimeSequence(), o2.getTimeSequence()); // asc
            if (c != 0) return c;
            c = compareLong(o1.getIndex(), o2.getIndex()); // asc
            return c;
        } else if (ind1) {
            // First order is individual, second is not
            return 1;
        } else if (ind2) {
            // Second order is individual, first is not
            return -1;
        } else {
            // Both orders are non-individual orders
            int c = Double.compare(o2.getSizeAsDouble(), o1.getSizeAsDouble()); // desc
            if (c != 0) return c;
            c = compareLong(o1.getTimeSequence(), o2.getTimeSequence()); // asc
            if (c != 0) return c;
            c = o1.getScope().getCode() - o2.getScope().getCode(); // asc
            if (c != 0) return c;
            c = o1.getExchangeCode() - o2.getExchangeCode(); // asc
            if (c != 0) return c;
            c = compareString(o1.getMarketMaker(), o2.getMarketMaker()); // asc
            if (c != 0) return c;
            c = compareLong(o1.getIndex(), o2.getIndex()); // asc
            return c;
        }
    };

    private static final Comparator<Order> BUY_COMPARATOR = (o1, o2) ->
        o1.getPrice() < o2.getPrice() ? 1 : // desc
        o1.getPrice() > o2.getPrice() ? -1 :
            ORDER_COMPARATOR.compare(o1, o2);

    private static final Comparator<Order> SELL_COMPARATOR = (o1, o2) ->
        o1.getPrice() < o2.getPrice() ? -1 : // asc
        o1.getPrice() > o2.getPrice() ? 1 :
            ORDER_COMPARATOR.compare(o1, o2);

    private void updateAllOrders(int mul, int div) {
        beginChange();
        for (ListIterator<Node<Order>> it = indexedEvents.entryListIterator(); it.hasNext();) {
            Node<Order> node = it.next();
            Order order = node.getValue();
            // process lot change
            correctOrderSize(order, mul, div);
            // remove/add order (as needed) thus reapplying filter and changing order position
            OrderBookList book = getBookForOrder(order);
            book.deleteOrderNode(node);
            if (shallAddToBook(order))
                book.insertOrderNode(node);
        }
        endChange();
    }

    private void beginChange() {
        buyOrders.beginChange();
        sellOrders.beginChange();
    }

    private void endChange() {
        boolean modelChanged = false;
        if (buyOrders.endChange())
            modelChanged = true;
        if (sellOrders.endChange())
            modelChanged = true;
        if (modelChanged)
            fireModelChanged();
    }

    private void fireModelChanged() {
        for (OrderBookModelListener listener : listeners)
            listener.modelChanged(change);
    }

    private void correctOrderSize(Order order, int mul, int div) {
        if (order.getScope() != Scope.ORDER && mul != div) {
            order.setSizeAsDouble(order.getSizeAsDouble() * mul / div);
        }
    }

    private OrderBookList getBookForOrder(Order order) {
        return (order.getOrderSide() == Side.BUY) ? buyOrders : sellOrders;
    }

    private static boolean shallAddToBook(Order order) {
        // add order node to book if order size is non-zero or order is composite
        return order.hasSize() || order.getScope() == Scope.COMPOSITE;
    }

    private static int compareLong(long l1, long l2) {
        return (l1 < l2) ? -1 : (l1 > l2) ? 1 : 0;
    }

    private static int compareString(String s1, String s2) {
        return (s1 != null) ? ((s2 != null) ? s1.compareTo(s2) : 1) : ((s2 != null) ? -1 : 0);
    }

    private class IndexedOrderModel extends AbstractIndexedEventModel<Order, Node<Order>> {
        List<Order> corrections; // used only when corrector != null

        IndexedOrderModel() {
            super(Order.class);
        }

        @Override
        protected boolean isClosed() {
            return super.isClosed();
        }

        // make it available from the outer class
        @Override
        protected ListIterator<Node<Order>> entryListIterator() {
            return super.entryListIterator();
        }

        @Override
        protected Node<Order> createEntry() {
            return new Node<>();
        }

        @Override
        protected boolean isSnapshotEnd(Order event) {
            // The path via EventFlags works for modern QD which properly emits EventFlags according to subscription.
            if (super.isSnapshotEnd(event))
                return true;
            // The path via (index == 0) works for files from older QD which missed SNAPSHOT_END flags.
            long index = event.getIndex();
            return (int) index == 0 && (!OrderSource.isSpecialSourceId((int) (index >> 48)) || (char) (index >> 32) == '\0');
        }

        @Override
        protected void modelChanged(List<Node<Order>> changedEntries) {
            beginChange();
            if (corrector != null && corrections == null)
                corrections = new ArrayList<>();
            for (Node<Order> node : changedEntries) {
                // Remove old order
                Order order = node.getValue();
                if (order != null) {
                    // submit this remove to corrector (if there is one), so that it knows about removal
                    // "acceptEvent" in corrector shall always return true for removals.
                    if (corrector != null)
                        corrector.acceptEvent(OrderBookCorrector.copy(order, 0), null);
                    // delete order node from book (in case it was added -- deleteOrderNode checks)
                    getBookForOrder(order).deleteOrderNode(node);
                }
                // Commit entry change (value updates)
                node.commitChange();
                // Insert new order
                order = node.getValue();
                if (order != null) {
                    // submit this new event to corrector (if there is one)
                    boolean accept = corrector == null || processCorrections(order);
                    if (shallAddToBook(order) && accept) {
                        correctOrderSize(order, lotSize, 1);
                        getBookForOrder(order).insertOrderNode(node);
                    }
                }
            }
            endChange();
        }

        private boolean processCorrections(Order order) {
            boolean accept = corrector.acceptEvent(order, corrections);
            // process corrections emitted by acceptEvent method
            for (Order correction : corrections) {
                assert !correction.hasSize(); // removal is the only kind of correction
                OrderBookList correctBook = getBookForOrder(correction);
                Node<Order> oldNode = correctBook.getNode(correction);
                if (oldNode != null)
                    correctBook.deleteOrderNode(oldNode);
            }
            // processed corrections
            corrections.clear();
            return accept;
        }
    }
}
