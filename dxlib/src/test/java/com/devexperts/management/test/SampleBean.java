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
package com.devexperts.management.test;

public class SampleBean implements SampleMXBean {
    public String setPrevDayClose(String symbol, String date, String price) {
        return null;
    }

    public String removeSymbol(String symbol) {
        return null;
    }

    public String removeDeadSymbols(long ttlMillis) {
        return null;
    }

    public int scan(String[] symbols) {
        return 0;
    }

    public int avoid(int[] indices) {
        return 0;
    }
}
