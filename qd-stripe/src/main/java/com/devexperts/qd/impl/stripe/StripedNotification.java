/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.stripe;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

class StripedNotification {
    private final int n;
    private final AtomicIntegerArray flag; // this is actually a boolean array of "notified" flags, 0 - false, 1 - true
    private final AtomicInteger cnt = new AtomicInteger(); // number of 1s in cnt, incremented after flag set to 1
    private final AtomicInteger last = new AtomicInteger(); // oracle to the last index to retrieve

    StripedNotification(int n) {
        this.n = n;
        flag = new AtomicIntegerArray(n);
    }

    boolean notify(int i) {
        do {
            if (flag.get(i) == 1)
                return false;
        } while (!flag.compareAndSet(i, 0, 1));
        return cnt.getAndIncrement() == 0;
    }

    boolean hasNext() {
        return cnt.get() > 0;
    }

    int next() {
    main_loop:
        while (hasNext()) {
            int prev;
            int i;
            do {
                prev = last.get();
                i = (prev + 1) % n;
            } while (!last.compareAndSet(prev, i));
            do {
                if (flag.get(i) == 0)
                    continue main_loop;
            } while (!flag.compareAndSet(i, 1, 0));
            cnt.getAndDecrement();
            return i;
        }
        return -1;
    }
}
