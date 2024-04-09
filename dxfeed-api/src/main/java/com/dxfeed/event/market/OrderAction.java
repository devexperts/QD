/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.market;

/**
 * Action enum for the Full Order Book (FOB) Orders. Action describes business meaning of the {@link Order} event:
 * whether order was added or replaced, partially or fully executed, etc.
 */
public enum OrderAction {

    /**
     * Default enum value for orders that do not support "Full Order Book" and for backward compatibility -
     * action must be derived from other {@link Order} fields.
     *
     * <p>All Full Order Book related fields for this action will be empty.
     */
    UNDEFINED(0),

    /**
     * New Order is added to Order Book.
     *
     * <p>Full Order Book fields:
     * <ul>
     * <li>{@link Order#getOrderId() orderId} - always present</li>
     * <li>{@link Order#getAuxOrderId() auxOrderId} - ID of the order replaced by this new order - if available.</li>
     * <li>Trade fields will be empty</li>
     * </ul>
     */
    NEW(1),

    /**
     * Order is modified and price-time-priority is not maintained (i.e. order has re-entered Order Book).
     * Order {@link Order#getEventSymbol() symbol} and {@link Order#getOrderSide() side} will remain the same.
     *
     * <p>Full Order Book fields:
     * <ul>
     * <li>{@link Order#getOrderId() orderId} - always present</li>
     * <li>Trade fields will be empty</li>
     * </ul>
     */
    REPLACE(2),

    /**
     * Order is modified without changing its price-time-priority (usually due to partial cancel by user).
     * Order's {@link Order#getSize() size} will contain new updated size.
     *
     * <p>Full Order Book fields:
     * <ul>
     * <li>{@link Order#getOrderId() orderId} - always present</li>
     * <li>Trade fields will be empty</li>
     * </ul>
     */
    MODIFY(3),

    /**
     * Order is fully canceled and removed from Order Book.
     * Order's {@link Order#getSize() size} will be equal to 0.
     *
     * <p>Full Order Book fields:
     * <ul>
     * <li>{@link Order#getOrderId() orderId} - always present</li>
     * <li>{@link Order#getAuxOrderId() auxOrderId} - ID of the new order replacing this order - if available.</li>
     * <li>Trade fields will be empty</li>
     * </ul>
     */
    DELETE(4),

    /**
     * Size is changed (usually reduced) due to partial order execution.
     * Order's {@link Order#getSize() size} will be updated to show current outstanding size.
     *
     * <p>Full Order Book fields:
     * <ul>
     * <li>{@link Order#getOrderId() orderId} - always present</li>
     * <li>{@link Order#getAuxOrderId() auxOrderId} - aggressor order ID, if available</li>
     * <li>{@link Order#getTradeId() tradeId} - if available</li>
     * <li>{@link Order#getTradeSize() tradeSize} and {@link Order#getTradePrice() tradePrice} -
     * contain size and price of this execution</li>
     * </ul>
     */
    PARTIAL(5),

    /**
     * Order is fully executed and removed from Order Book.
     * Order's {@link Order#getSize() size} will be equals to 0.
     *
     * <p>Full Order Book fields:
     * <ul>
     * <li>{@link Order#getOrderId() orderId} - always present</li>
     * <li>{@link Order#getAuxOrderId() auxOrderId} - aggressor order ID, if available</li>
     * <li>{@link Order#getTradeId() tradeId} - if available</li>
     * <li>{@link Order#getTradeSize() tradeSize} and {@link Order#getTradePrice() tradePrice} -
     * contain size and price of this execution - always present</li>
     * </ul>
     */
    EXECUTE(6),

    /**
     * Non-Book Trade - this Trade not refers to any entry in Order Book.
     * Order's {@link Order#getSize() size} and {@link Order#getPrice() price} will be equals to 0.
     *
     * <p>Full Order Book fields:
     * <ul>
     * <li>{@link Order#getOrderId() orderId} - always empty</li>
     * <li>{@link Order#getTradeId() tradeId} - if available</li>
     * <li>{@link Order#getTradeSize() tradeSize} and {@link Order#getTradePrice() tradePrice} -
     * contain size and price of this trade - always present</li>
     * </ul>
     */
    TRADE(7),

    /**
     * Prior Trade/Order Execution bust.
     * Order's {@link Order#getSize() size} and {@link Order#getPrice() price} will be equals to 0.
     *
     * <p>Full Order Book fields:
     * <ul>
     * <li>{@link Order#getOrderId() orderId} - always empty</li>
     * <li>{@link Order#getTradeId() tradeId} - always present</li>
     * <li>{@link Order#getTradeSize() tradeSize} and {@link Order#getTradePrice() tradePrice} - always empty</li>
     * </ul>
     */
    BUST(8);

    private static final OrderAction[] ACTIONS = Util.buildEnumArrayByOrdinal(UNDEFINED, 16);

    /**
     * Returns side by integer code bit pattern.
     * @param code integer code.
     * @return side.
     * @throws ArrayIndexOutOfBoundsException if code is invalid.
     */
    public static OrderAction valueOf(int code) {
        return ACTIONS[code];
    }

    private final int code;

    private OrderAction(int code) {
        this.code = code;
        if (code != ordinal())
            throw new IllegalArgumentException("code differs from ordinal");
    }

    /**
     * Returns integer code that is used in flag bits.
     * @return integer code.
     */
    public int getCode() {
        return code;
    }
}
