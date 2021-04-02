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
 * Scope of an order.
 */
public enum Scope {
    /**
     * Represents best bid or best offer for the whole market.
     */
    COMPOSITE(0),

    /**
     * Represents best bid or best offer for a given exchange code.
     */
    REGIONAL(1),

    /**
     * Represents aggregate information for a given price level or
     * best bid or best offer for a given market maker.
     */
    AGGREGATE(2),

    /**
     * Represents individual order on the market.
     */
    ORDER(3);

    private static final Scope[] SCOPES = Util.buildEnumArrayByOrdinal(COMPOSITE, 4);

    /**
     * Returns scope by integer code bit pattern.
     * @param code integer code.
     * @return scope.
     * @throws ArrayIndexOutOfBoundsException if code is invalid.
     */
    public static Scope valueOf(int code) {
        return SCOPES[code];
    }

    private final int code;

    private Scope(int code) {
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
