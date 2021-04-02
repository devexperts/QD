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

@SuppressWarnings("deprecation")
public class TimeSecondsField extends TimeField {
    public TimeSecondsField(int index, String name) {
        super(index, name);
    }

    public TimeSecondsField(int index, String name, SerialFieldType serialType) {
        super(index, name, serialType);
    }
}
