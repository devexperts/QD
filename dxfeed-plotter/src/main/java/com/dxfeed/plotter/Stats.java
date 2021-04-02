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

import java.util.concurrent.atomic.AtomicInteger;

class Stats {
    private final String prefix;

    private final AtomicInteger counter = new AtomicInteger();
    private double average;

    Stats(String caption) {
        this.prefix = caption + ": ";
    }

    void increment(int count) {
        counter.addAndGet(count);
    }

    String update(long deltaTime) {
        if (deltaTime > 0) {
            average = counter.getAndSet(0) * 1000d / deltaTime;
        }
        return prefix + Math.round(average);
    }
}
