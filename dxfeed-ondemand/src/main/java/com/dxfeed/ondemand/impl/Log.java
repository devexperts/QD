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
package com.dxfeed.ondemand.impl;

import com.devexperts.logging.Logging;

class Log {
    static final Logging log = Logging.getLogging(MarketDataReplay.class);

    static String mb(long size) {
        return size < 525 ? "0 MB" : Double.toString(Math.floor(size / 1048.576 + 0.5) / 1000) + " MB";
    }
}
