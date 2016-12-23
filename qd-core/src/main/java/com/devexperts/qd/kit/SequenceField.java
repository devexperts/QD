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

public class SequenceField extends CompactIntField {
    private static final int MILLIS_SHIFT = 22;
    private static final int SEQUENCE_MASK = (1 << MILLIS_SHIFT) - 1;

    public SequenceField(int index, String name) {
        super(index, name, SerialFieldType.SEQUENCE.forNamedField(name));
    }

    public SequenceField(int index, String name, SerialFieldType serialType) {
        super(index, name, serialType);
        if (!serialType.hasSameRepresentationAs(SerialFieldType.SEQUENCE))
            throw new IllegalArgumentException("Invalid serialType: " + serialType);
    }

    @Override
    public String toString(int value) {
        int millis = value >>> MILLIS_SHIFT;
        return millis != 0 ? millis + ":" + (value & SEQUENCE_MASK) : Integer.toString(value);
    }

    @Override
    public int parseString(String value) {
        int i = value.indexOf(':');
        return i >= 0 ?
            (Integer.parseInt(value.substring(0, i)) << MILLIS_SHIFT) + Integer.parseInt(value.substring(i + 1)) :
            Integer.parseInt(value);
    }
}
