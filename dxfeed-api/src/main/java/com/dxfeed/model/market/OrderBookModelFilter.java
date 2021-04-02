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

import com.dxfeed.event.market.Scope;

/**
 * An order events filter for {@link OrderBookModel}.
 * Filter specifies which order scopes are allowed to be shown in the OrderBook.
 *
 * <p>Note that if more than one order scope is allowed, events with mode detailed scope will hide
 * the ones with the less detailed scope. E.g. aggregate order ({@link Scope#AGGREGATE})
 * for the specified exchange and market-maker would disable regional order for that exchange
 * ({@link Scope#REGIONAL} which in turn would disable best bid and offer order ({@link Scope#COMPOSITE}).
 *
 * @see Scope
 */
public enum OrderBookModelFilter {

    /** Only {@link Scope#COMPOSITE COMPOSITE} orders are enabled. */
    COMPOSITE(Scope.COMPOSITE),
    /** Only {@link Scope#REGIONAL REGIONAL} orders are enabled. */
    REGIONAL(Scope.REGIONAL),
    /** Only {@link Scope#AGGREGATE AGGREGATE} orders are enabled. */
    AGGREGATE(Scope.AGGREGATE),
    /** Only {@link Scope#ORDER ORDER} orders are enabled. */
    ORDER(Scope.ORDER),

    /** {@link Scope#COMPOSITE COMPOSITE} and {@link Scope#REGIONAL REGIONAL} orders are enabled. */
    COMPOSITE_REGIONAL(Scope.COMPOSITE, Scope.REGIONAL),
    /** All orders except {@link Scope#ORDER ORDER} orders are enabled. */
    COMPOSITE_REGIONAL_AGGREGATE(Scope.COMPOSITE, Scope.REGIONAL, Scope.AGGREGATE),

    /** All order scopes are enabled. */
    ALL(Scope.values()),
    ;

    private final int filter;

    private OrderBookModelFilter(Scope... scopes) {
        int result = 0;
        for (Scope scope : scopes)
            result |= 1 << scope.getCode();
        filter = result;
    }

    /**
     * Returns {@code true} if the specified order scope is accepted, {@code false} otherwise.
     * @param scope order scope
     * @return {@code true} if the specified order scope is accepted, {@code false} otherwise.
     */
    public boolean allowScope(Scope scope) {
        return (filter & (1 << scope.getCode())) != 0;
    }
}
