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

/**
 * Callback interface that receives notifications about changes in {@link OrderBookModel}.
 * @see Change
 */
public interface OrderBookModelListener {
    /**
     * Represents a notification of the change done to an OrderBookModel.
     *
     * <p><b>Note</b>, that the change object object currently provides only information about the
     * source of the change (what model has changed) and does not actually provide
     * information about what orders in the model has changed.
     * The model has to be queried using lists of
     * {@link OrderBookModel#getBuyOrders() buy} and {@link OrderBookModel#getSellOrders() sell}
     * orders to get the new set of active orders in the book.
     *
     * <p><b>Warning:</b> Change object can be reused and can become invalid when another change occurs
     * on the model. It is not safe to use this class on a different thread from the listener's thread.
     */
    public static class Change {
        private final OrderBookModel source;

        public Change(OrderBookModel source) {
            this.source = source;
        }

        /**
         * Returns the source model of the change.
         * @return the model that was changed.
         */
        public OrderBookModel getSource() {
            return source;
        }
    }

    /**
     * Called after a change has been made to an OrderBookModel.
     *
     * <p><b>Note</b>, that the change object object currently provides only information about the
     * source of the change (what model has changed) and does not actually provide
     * information about what orders in the model has changed.
     * The model has to be queried using lists of
     * {@link OrderBookModel#getBuyOrders() buy} and {@link OrderBookModel#getSellOrders() sell}
     * orders to get the new set of active orders in the book.
     *
     * <p><b>Warning:</b> Change object can be reused and can become invalid when another change occurs
     * on the model. It is not safe to use this change object on a different thread from the listener's thread.
     *
     * @param change an object representing the change.
     */
    public void modelChanged(Change change);
}
