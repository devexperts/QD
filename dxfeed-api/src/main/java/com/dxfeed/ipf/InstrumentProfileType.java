/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf;

import java.util.HashMap;

/**
 * Defines standard types of {@link InstrumentProfile}. Note that other (unknown) types
 * can be used without listing in this class - use it for convenience only.
 * Please see <b>Instrument Profile Format</b> documentation for complete description.
 */
public enum InstrumentProfileType {
    CURRENCY,
    FOREX,
    BOND,
    INDEX,
    STOCK,
    ETF,
    MUTUAL_FUND,
    MONEY_MARKET_FUND,
    PRODUCT,
    FUTURE,
    OPTION,
    WARRANT,
    CERTIFICATE,
    CFD,
    SPREAD,
    OTHER,
    REMOVED;

    private static final HashMap<String, InstrumentProfileType> MAP = new HashMap<String, InstrumentProfileType>();
    static {
        for (InstrumentProfileType ipt : values())
            MAP.put(ipt.name(), ipt);
    }

    /**
     * Returns field for specified name or <b>null</b> if field is not found.
     * The difference from {@link #valueOf} method is that later method throws exception for unknown fields.
     */
    public static InstrumentProfileType find(String name) {
        return MAP.get(name);
    }

    /**
     * Compares two specified types for order. Returns a negative integer, zero, or a positive integer
     * as this object is less than, equal to, or greater than the specified object.
     * <p>
     * Unlike natual ordering of {@link InstrumentProfileType} enum itself this method supports
     * unknown types and orders them alphabetically after standard ones.
     * <p>
     * The natural ordering implied by this method is designed for convenient data representation
     * in a file and shall not be used for business purposes.
     */
    public static int compareTypes(String type1, String type2) {
        InstrumentProfileType t1 = find(type1);
        InstrumentProfileType t2 = find(type2);
        if (t1 == null)
            return t2 == null ? type1.compareTo(type2) : +1;
        if (t2 == null)
            return -1;
        return t1.compareTo(t2);
    }
}
