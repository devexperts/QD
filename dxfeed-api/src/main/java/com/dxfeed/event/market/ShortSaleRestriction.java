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
 * Short sale restriction on an instrument.
 */
public enum ShortSaleRestriction {
    /**
     * Short sale restriction is undefined, unknown or inapplicable.
     */
    UNDEFINED(0),

    /**
     * Short sale restriction is active.
     */
    ACTIVE(1),

    /**
     * Short sale restriction is inactive.
     */
    INACTIVE(2);

    private static final ShortSaleRestriction[] RESTRICTIONS = Util.buildEnumArrayByOrdinal(UNDEFINED, 4);

    /**
     * Returns restriction by integer code bit pattern.
     * @param code integer code.
     * @return restriction.
     * @throws ArrayIndexOutOfBoundsException if code is invalid.
     */
    public static ShortSaleRestriction valueOf(int code) {
        return RESTRICTIONS[code];
    }

    private final int code;

    private ShortSaleRestriction(int code) {
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
