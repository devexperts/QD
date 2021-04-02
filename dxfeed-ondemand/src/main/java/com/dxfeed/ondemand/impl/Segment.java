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

import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

class Segment {
    static final Comparator<Segment> USAGE_COMPARATOR = new Comparator<Segment>() {
        public int compare(Segment segment1, Segment segment2) {
            return segment1.usage < segment2.usage ? -1 : segment1.usage > segment2.usage ? 1 : Block.COMPARATOR.compare(segment1.block, segment2.block);
        }
    };

    private static final AtomicLongFieldUpdater<Segment> USAGE_UPDATER = AtomicLongFieldUpdater.newUpdater(Segment.class, "usage");

    // ------------------------ instance ------------------------

    final Block block;
    final long downloadTime;
    volatile long usage; // SYNC(Cache instance) on write, unsync read for Cache.writeCache
    int currentCounter; // SYNC(Cache instance)

    Segment(Block block) {
        this(block, System.currentTimeMillis());
    }

    Segment(Block block, long downloadTime) {
        block.setSymbol(block.getSymbol().intern());
        this.block = block;
        this.downloadTime = downloadTime;
    }

    public String toString() {
        return block.toString();
    }

    int size() {
        // IndexedSet overhead=8, segment=40, block=48, block.symbol=0 (shared), block.body=16+length, event=0/48, input=0/24
        // event and input are null for passive segments and non-null for current segments: (48+24)*currentSegments.size()
        return 8 + 40 + 48 + 0 + 16 + block.getBodyLength();
    }

    boolean intersects(Segment segment) {
        return block.getStartTime() < segment.block.getEndTime() && block.getEndTime() > segment.block.getStartTime();
    }

    void updateUsage(long upd) {
        long cur;
        do {
            cur = usage;
        } while (cur < upd && !USAGE_UPDATER.compareAndSet(this, cur, upd));
    }

}
