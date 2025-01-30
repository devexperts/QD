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
package com.dxfeed.event.market;

/**
 * Type of orders on the Nuam Market.
 */
public enum NuamOrderType {
    /**
     * The order type is undefined, unknown or inapplicable.
     */
    UNDEFINED(0),

    /**
     * The order must execute within a limit price.
     */
    LIMIT(1),

    /**
     * The order can execute at any price.
     */
    MARKET(2),

    /**
     * The order is first executed as a market order. If it cannot be fully executed,
     * any remaining quantity is then converted into a limit order, with the execution price serving as the limit.
     */
    MARKET_TO_LIMIT(3),

    /**
     * The order is entered without a price and if there is a best price on the same side of the order book (BBO),
     * the order will be given this price and added to the order book.
     */
    BEST_ORDER(4),

    /**
     * Imbalance orders only participate in auctions and only of there is a surplus on the same side
     * as the imbalance order. Imbalance orders are not part of the EP calculation.
     */
    IMBALANCE(5);

    private static final NuamOrderType[] TYPES = Util.buildEnumArrayByOrdinal(UNDEFINED, 16);

    /**
     * Returns the order type by integer code bit pattern.
     * @param code integer code.
     * @return order type.
     * @throws ArrayIndexOutOfBoundsException if code is invalid.
     */
    public static NuamOrderType valueOf(int code) {
        return TYPES[code];
    }

    private final int code;

    private NuamOrderType(int code) {
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
