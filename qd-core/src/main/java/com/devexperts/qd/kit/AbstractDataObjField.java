/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.kit;

import com.devexperts.qd.DataObjField;
import com.devexperts.qd.SerialFieldType;

public abstract class AbstractDataObjField extends AbstractDataField implements DataObjField {
    AbstractDataObjField(int index, String name, SerialFieldType serialType) {
        super(index, name, serialType);
    }

    /**
     * Returns string representation of the specified field value.
     * This method is used for debugging purposes.
     * This implementation returns <code>String.valueOf(value)</code>.
     */
    public String toString(Object value) {
        return String.valueOf(value);
    }

    /**
     * Parses string representation of specified field value.
     * This method is used for debugging purposes.
     * This implementation returns {@code value}.
     */
    public Object parseString(String value) {
        return value;
    }

    /**
     * Compares two specified field values for equality.
     * This method is used for implementation of ticker contract.
     * This implementation returns {@code value1 == value2 || (value1 != null && value1.equals(value2))}.
     */
    public boolean equals(Object value1, Object value2) {
        return value1 == value2 || (value1 != null && value1.equals(value2));
    }
}

