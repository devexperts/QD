/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.market;

/**
 * Type of prices on the OTC Markets.
 * Please see <a href="https://downloads.dxfeed.com/specifications/OTC_Markets_Data_Display_Requirements.pdf">OTC Markets Data Display Requirements</a>
 * section "4.2 Quote Price Types" for complete description.
 */
public enum OtcMarketsPriceType {
    /**
     * Unpriced quotes are an indication of interest (IOI) in a security
     * used when a trader does not wish to show a price or size.
     * Unpriced, name-only quotes are also used as the other side of a one-sided, priced quote.
     * Unpriced quotes may not have a Quote Access Payment (QAP) value.
     */
    UNPRICED(0),

    /**
     * Actual (Priced) is the actual amount a trader is willing to buy or sell securities.
     */
    ACTUAL(1),

    /**
     * Offer Wanted/Bid Wanted (OW/BW) is used to solicit sellers/buyers,
     * without displaying actual price or size.
     * OW/BW quotes may not have a Quote Access Payment (QAP) value.
     */
    WANTED(2);

    private static final OtcMarketsPriceType[] TYPES = Util.buildEnumArrayByOrdinal(UNPRICED, 4);

    /**
     * Returns price type by integer code bit pattern.
     * @param code integer code.
     * @return price type.
     * @throws ArrayIndexOutOfBoundsException if code is invalid.
     */
    public static OtcMarketsPriceType valueOf(int code) {
        return TYPES[code];
    }

    private final int code;

    private OtcMarketsPriceType(int code) {
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
