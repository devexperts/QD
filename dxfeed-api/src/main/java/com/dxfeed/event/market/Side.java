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
package com.dxfeed.event.market;

/**
 * Side of an order or a trade.
 */
public enum Side {
    /**
     * Side is undefined, unknown or inapplicable.
     */
    UNDEFINED(0),

    /**
     * Buy side (bid).
     */
    BUY(1),

    /**
     * Sell side (ask or offer).
     */
    SELL(2);

    private static final Side[] SIDES = Util.buildEnumArrayByOrdinal(UNDEFINED, 4);

    /**
     * Returns side by integer code bit pattern.
     * @param code integer code.
     * @return side.
     * @throws ArrayIndexOutOfBoundsException if code is invalid.
     */
    public static Side valueOf(int code) {
        return SIDES[code];
    }

    private final int code;

    private Side(int code) {
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
