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
import com.devexperts.util.TimeFormat;

/**
 * @deprecated Use {@link TimeSecondsField} instead.
 */
@Deprecated()
public class TimeField extends CompactIntField {
    public TimeField(int index, String name) {
        this(index, name, SerialFieldType.TIME_SECONDS.forNamedField(name));
    }

    public TimeField(int index, String name, SerialFieldType serialType) {
        super(index, name, serialType);
        if (!serialType.hasSameRepresentationAs(SerialFieldType.TIME_SECONDS))
            throw new IllegalArgumentException("Invalid serialType: " + serialType);
    }

    @Override
    public String toString(int value) {
        return TimeFormat.DEFAULT.withTimeZone().format(value * 1000L);
    }

    @Override
    public int parseString(String value) {
        return (int) (TimeFormat.DEFAULT.parse(value).getTime() / 1000);
    }
}
