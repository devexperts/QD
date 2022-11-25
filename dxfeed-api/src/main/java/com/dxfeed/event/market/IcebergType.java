/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.event.market;

/**
 * Type of an iceberg order.
 */
public enum IcebergType {

    /**
     * Iceberg type is undefined, unknown or inapplicable.
     */
    UNDEFINED(0),

    /**
     * Represents native (exchange-managed) iceberg type.
     */
    NATIVE(1),

    /**
     * Represents synthetic (managed outside of the exchange) iceberg type
     */
    SYNTHETIC(2);

    private static final IcebergType[] TYPES = Util.buildEnumArrayByOrdinal(UNDEFINED, 4);

    /**
     * Returns iceberg type by integer code bit pattern.
     *
     * @param code integer code.
     * @return iceberg type.
     * @throws ArrayIndexOutOfBoundsException if code is invalid.
     */
    public static IcebergType valueOf(int code) {
        return TYPES[code];
    }

    private final int code;

    private IcebergType(int code) {
        this.code = code;
        if (code != ordinal())
            throw new IllegalArgumentException("code differs from ordinal");
    }

    /**
     * Returns integer code that is used in flag bits.
     *
     * @return integer code.
     */
    public int getCode() {
        return code;
    }
}
