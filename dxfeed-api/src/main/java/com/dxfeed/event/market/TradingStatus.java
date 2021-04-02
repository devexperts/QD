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
 * Trading status of an instrument.
 */
public enum TradingStatus {
    /**
     * Trading status is undefined, unknown or inapplicable.
     */
    UNDEFINED(0),

    /**
     * Trading is halted.
     */
    HALTED(1),

    /**
     * Trading is active.
     */
    ACTIVE(2);

    private static final TradingStatus[] STATUSES = Util.buildEnumArrayByOrdinal(UNDEFINED, 4);

    /**
     * Returns status by integer code bit pattern.
     * @param code integer code.
     * @return status.
     * @throws ArrayIndexOutOfBoundsException if code is invalid.
     */
    public static TradingStatus valueOf(int code) {
        return STATUSES[code];
    }

    private final int code;

    private TradingStatus(int code) {
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
