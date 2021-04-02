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
package com.dxfeed.plotter;

import com.dxfeed.event.market.Quote;

import java.util.List;

class PlotData {
    final String name;
    final List<Quote> data;
    final List<Long> times;

    PlotData(String name, List<Quote> data, List<Long> times) {
        this.name = name;
        this.data = data;
        this.times = times;
    }
}
