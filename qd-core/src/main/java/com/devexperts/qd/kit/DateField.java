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
import com.devexperts.util.Timing;

public class DateField extends CompactIntField {
    public DateField(int index, String name) {
        super(index, name, SerialFieldType.DATE.forNamedField(name));
    }

    public DateField(int index, String name, SerialFieldType serialType) {
        super(index, name, serialType);
        if (!serialType.hasSameRepresentationAs(SerialFieldType.DATE))
            throw new IllegalArgumentException("Invalid serialType: " + serialType);
    }

    @Override
    public String toString(int value) {
        return value == 0 ? "0" : Integer.toString(Timing.LOCAL.getById(value).year_month_day_number);
    }

    @Override
    public int parseString(String value) {
        int ymd = Integer.parseInt(value);
        return ymd == 0 ? 0 : Timing.LOCAL.getByYmd(ymd).day_id;
    }
}
