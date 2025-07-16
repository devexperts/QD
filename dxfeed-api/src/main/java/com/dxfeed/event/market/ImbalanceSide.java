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

import com.devexperts.annotation.Experimental;
import com.dxfeed.event.impl.EventUtil;

/**
 * Side of an order imbalance.
 */
@Experimental
public enum ImbalanceSide {
    /**
     * Side is undefined, unknown or inapplicable.
     */
    UNDEFINED(0),

    /**
     * Buy side (bid) imbalance.
     */
    BUY(1),

    /**
     * Sell side (ask or offer) imbalance.
     */
    SELL(2),

    /**
     * No order imbalance exists.
     */
    NO_IMBALANCE(3),

    /**
     * Insufficient orders to calculate imbalance.
     *
     * <p>Applicable only to NASDAQ and OTC Markets exchanges.
     */
    INSUFFICIENT_ORDERS(4),

    /**
     * Imbalance calculation is paused.
     *
     * <p>Applicable only to NASDAQ exchange.
     */
    PAUSED(5);

    private static final ImbalanceSide[] SIDES = EventUtil.buildEnumArrayByOrdinal(UNDEFINED, 8);

    /**
     * Returns the imbalance side by integer code bit pattern.
     *
     * @param code integer code.
     * @return the imbalance side.
     * @throws ArrayIndexOutOfBoundsException if code is invalid.
     */
    public static ImbalanceSide valueOf(int code) {
        return SIDES[code];
    }

    private final int code;

    private ImbalanceSide(int code) {
        this.code = code;
        if (code != ordinal())
            throw new IllegalArgumentException("code differs from ordinal");
    }

    /**
     * Returns the integer code used in flag bits.
     *
     * @return the integer code.
     */
    public int getCode() {
        return code;
    }
}
