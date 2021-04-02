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
package com.devexperts.qd.kit;

import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.util.Decimal;

/**
 * The <code>DecimalField</code> represents a decimal field with compact serialized form.
 * See {@link Decimal} for description of internal representation.
 * It can be used for fields which are usually represented with
 * floating point values, such as prices, amounts, etc.
 */
public class DecimalField extends CompactIntField {
    public DecimalField(int index, String name) {
        this(index, name, SerialFieldType.DECIMAL.forNamedField(name));
    }

    public DecimalField(int index, String name, SerialFieldType serialType) {
        super(index, name, serialType);
        if (!serialType.hasSameRepresentationAs(SerialFieldType.DECIMAL))
            throw new IllegalArgumentException("Invalid serialType: " + serialType);
    }

    public String toString(int value) {
        return Decimal.toString(value);
    }

    public int parseString(String value) {
        return Decimal.parseDecimal(value);
    }

    public double toDouble(int value) {
        return Decimal.toDouble(value);
    }

    public int toInt(double value) {
        return Decimal.compose(value);
    }
}
