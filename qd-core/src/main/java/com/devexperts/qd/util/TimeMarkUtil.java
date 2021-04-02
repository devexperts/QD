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
package com.devexperts.qd.util;

import java.util.concurrent.TimeUnit;

public class TimeMarkUtil {
    public static final int TIME_MARK_MASK = 0x3fffffff;
    public static final TimeUnit TIME_MARK_UNIT = TimeUnit.MICROSECONDS;

    private TimeMarkUtil() {}

    public static int currentTimeMark() {
        return ((int) (System.nanoTime() / 1000)) & TIME_MARK_MASK;
    }

    public static int signedDeltaMark(int mark) {
        return (mark << 2) >> 2;
    }
}
