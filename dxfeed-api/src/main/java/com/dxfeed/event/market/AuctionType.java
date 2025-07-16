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
 * Type of auction.
 */
@Experimental
public enum AuctionType {
    /**
     * Type is undefined, unknown or inapplicable.
     */
    UNDEFINED(0),

    /**
     * Represents an opening auction.
     */
    OPENING(1),

    /**
     * Represents a closing auction.
     */
    CLOSING(2),

    /**
     * Represents a reopening auction, after a halt or pause and IPO.
     */
    REOPENING(3),

    /**
     * Other auction types.
     */
    OTHER(4);

    private static final AuctionType[] TYPES = EventUtil.buildEnumArrayByOrdinal(UNDEFINED, 8);

    /**
     * Returns the auction type by integer code bit pattern.
     *
     * @param code integer code.
     * @return the auction type.
     * @throws ArrayIndexOutOfBoundsException if code is invalid.
     */
    public static AuctionType valueOf(int code) {
        return TYPES[code];
    }

    private final int code;

    private AuctionType(int code) {
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
