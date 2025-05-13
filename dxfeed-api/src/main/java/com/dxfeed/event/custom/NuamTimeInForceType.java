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
package com.dxfeed.event.custom;

import com.devexperts.annotation.Experimental;
import com.dxfeed.event.impl.EventUtil;

/**
 * Type of Time in Force for the orders on the Nuam Market.
 */
@Experimental
public enum NuamTimeInForceType {
    /**
     * The time in force type is undefined, unknown or inapplicable.
     */
    UNDEFINED(0),

    /**
     * The order will expire at the end of the day it was entered.
     */
    REST_OF_DAY(1),

    /**
     * The order will rest on the order book until it is cancelled or fully executed.
     */
    GTC(2),

    /**
     * In continuous matchning the order will execute upon reception but never added
     * to the order book. In an action period it will be added to the order book in order
     * to participate in the next uncross.
     */
    IMMEDIATE_OR_CANCEL(3),

    /**
     * In continuous matching the order will execute upon reception but never added to the order book.
     * The difference compared to {@link #IMMEDIATE_OR_CANCEL} is this order must execute in full.
     */
    FILL_OR_KILL(4),

    /**
     * The order will expire at the end of the specified session.
     * This time in force type will utilise the addition field for defining the session id
     */
    GOOD_TIL_SESSION(5),

    /**
     * The order will expire after the defined number of days where the day the order is entered is counted as one day.
     * This time in force type will utilise the addition field for defining the number of days.
     */
    NUMBER_OF_DAYS(6);

    private static final NuamTimeInForceType[] TYPES = EventUtil.buildEnumArrayByOrdinal(UNDEFINED, 16);

    /**
     * Returns the time in force type by integer code bit pattern.
     * @param code integer code.
     * @return time in force type.
     * @throws ArrayIndexOutOfBoundsException if code is invalid.
     */
    public static NuamTimeInForceType valueOf(int code) {
        return TYPES[code];
    }

    private final int code;

    private NuamTimeInForceType(int code) {
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
