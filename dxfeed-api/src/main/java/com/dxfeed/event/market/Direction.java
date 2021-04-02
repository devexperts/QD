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
 * Direction of the price movement. For example tick direction for last trade price.
 */
public enum Direction {
    /**
     * Direction is undefined, unknown or inapplicable.
     * It includes cases with undefined price value or when direction computation was not performed.
     */
    UNDEFINED(0),

    /**
     * Current price is lower than previous price.
     */
    DOWN(1),

    /**
     * Current price is the same as previous price and is lower than the last known price of different value.
     */
    ZERO_DOWN(2),

    /**
     * Current price is equal to the only known price value suitable for price direction computation.
     * Unlike <b>UNDEFINED</b> the <b>ZERO</b> direction implies that current price is defined and
     * direction computation was duly performed but has failed to detect any upward or downward price movement.
     * It is also reported for cases when price sequence was broken and direction computation was restarted anew.
     */
    ZERO(3),

    /**
     * Current price is the same as previous price and is higher than the last known price of different value.
     */
    ZERO_UP(4),

    /**
     * Current price is higher than previous price.
     */
    UP(5);

    private static final Direction[] DIRECTIONS = Util.buildEnumArrayByOrdinal(UNDEFINED, 8);

    /**
     * Returns direction by integer code bit pattern.
     * @param code integer code.
     * @return restriction.
     * @throws ArrayIndexOutOfBoundsException if code is invalid.
     */
    public static Direction valueOf(int code) {
        return DIRECTIONS[code];
    }

    private final int code;

    private Direction(int code) {
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
