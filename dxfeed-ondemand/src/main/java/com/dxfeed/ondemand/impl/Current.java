/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ondemand.impl;

import com.devexperts.util.IndexedSet;

class Current {
    final IndexedSet<Key, Key> subscription = IndexedSet.create();
    final IndexedSet<Key, CurrentSegment> segments = IndexedSet.create(CurrentSegment.KEY_INDEXER);
    long version; // last Cache.version it was rebuilt for
    long time;
    long startTime;
    long endTime;
    long size;
    double replaySpeed = 1;

    boolean isCurrentInterval(long time) {
        return time >= startTime && time < endTime;
    }

    void resetInterval() {
        startTime = Long.MIN_VALUE;
        endTime = Long.MAX_VALUE;
    }
}
