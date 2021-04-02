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
import com.devexperts.qd.util.ShortString;

/**
 * The <code>ShortStringField</code> represents a short (up to 4 characters) string
 * in a single integer with compact serialized form.
 *
 * It can be used to represent short fixed-size character codes, such as MMID, etc.
 */
public class ShortStringField extends CompactIntField {
    public ShortStringField(int index, String name) {
        this(index, name, SerialFieldType.SHORT_STRING.forNamedField(name));
    }

    public ShortStringField(int index, String name, SerialFieldType serialType) {
        super(index, name, serialType);
        if (!serialType.hasSameRepresentationAs(SerialFieldType.SHORT_STRING))
            throw new IllegalArgumentException("Invalid serialType: " + serialType);
    }

    @Override
    public String toString(int value) {
        return ShortString.decode(value);
    }

    @Override
    public int parseString(String value) {
        return (int) ShortString.encode(value);
    }

    @Override
    public double toDouble(int value) {
        return Double.NaN;
    }
}
