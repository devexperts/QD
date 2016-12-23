/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
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
    private final double multiplier;
    private final boolean asIs;

    public DecimalField(int index, String name) {
        this(index, name, SerialFieldType.DECIMAL.forNamedField(name));
    }

    public DecimalField(int index, String name, SerialFieldType serialType) {
        this(index, name, serialType, 1);
    }

    public DecimalField(int index, String name, SerialFieldType serialType, double multiplier) {
        super(index, name, serialType);
        if (!serialType.hasSameRepresentationAs(SerialFieldType.DECIMAL))
            throw new IllegalArgumentException("Invalid serialType: " + serialType);
        this.multiplier = multiplier;
        asIs = multiplier == 1;
    }

    @Override
    public String toString(int value) {
        if (asIs)
            return Decimal.toString(value);
        double v = toDouble(value);
        if (Double.isNaN(v))
            return Decimal.NAN_STRING;
        return v == (long) v ? Long.toString((long) v) : Double.toString(v);
    }

    @Override
    public int parseString(String value) {
        if (asIs)
            return Decimal.parseDecimal(value);
        if (value.equals(Decimal.NAN_STRING))
            return toInt(Double.NaN);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '-' && (c < '0' || c > '9'))
                return toInt(Double.parseDouble(value));
        }
        return toInt(value.length() <= 18 ? Long.parseLong(value) : Double.parseDouble(value));
    }

    @Override
    public double toDouble(int value) {
        double v = Decimal.toDouble(value);
        return asIs ? v : v * multiplier;
    }

    @Override
    public int toInt(double value) {
        if (!asIs)
            value /= multiplier;
        return Decimal.compose(value);
    }
}
