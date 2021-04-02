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
 * Type of a time and sale event.
 */
public enum TimeAndSaleType {
    /**
     * Represents new time and sale event.
     */
    NEW(0),

    /**
     * Represents correction time and sale event.
     */
    CORRECTION(1),

    /**
     * Represents cancel time and sale event.
     */
    CANCEL(2);

    private static final TimeAndSaleType[] TYPES = Util.buildEnumArrayByOrdinal(NEW, 4);

    /**
     * Returns type by integer code bit pattern.
     * @param code integer code.
     * @return type.
     * @throws ArrayIndexOutOfBoundsException if code is invalid.
     */
    public static TimeAndSaleType valueOf(int code) {
        return TYPES[code];
    }

    private final int code;

    TimeAndSaleType(int code) {
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
