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
 * Type of the price value.
 */
public enum PriceType {
    /**
     * Regular price.
     */
    REGULAR(0),

    /**
     * Indicative price (derived via math formula).
     */
    INDICATIVE(1),

    /**
     * Preliminary price (preliminary settlement price), usually posted prior to {@link #FINAL} price.
     */
    PRELIMINARY(2),

    /**
     * Final price (final settlement price).
     */
    FINAL(3);

    private static final PriceType[] TYPES = Util.buildEnumArrayByOrdinal(REGULAR, 4);

    /**
     * Returns price type by integer code bit pattern.
     * @param code integer code.
     * @return price type.
     * @throws ArrayIndexOutOfBoundsException if code is invalid.
     */
    public static PriceType valueOf(int code) {
        return TYPES[code];
    }

    private final int code;

    PriceType(int code) {
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
