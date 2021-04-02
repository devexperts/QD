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

import com.devexperts.io.ByteArrayInput;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.util.IndexerFunction;
import com.dxfeed.ondemand.impl.event.MDREvent;
import com.dxfeed.ondemand.impl.event.MDREventUtil;
import com.dxfeed.ondemand.impl.event.MDRTradeHistory;

import java.io.IOException;

class CurrentSegment {
    static final IndexerFunction<Key, CurrentSegment> KEY_INDEXER = segment -> segment.segment.block;

    Segment segment;
    MDREvent event;
    ByteArrayInput input;

    // SYNC(Cache instance)
    CurrentSegment(Segment segment) {
        acquire(segment);
    }

    // SYNC(Cache instance)
    private void acquire(Segment segment) {
        this.segment = segment;
        segment.currentCounter++;
    }

    // SYNC(Cache instance)
    void release() {
        segment.currentCounter--;
    }

    void replaceSegment(Segment newSegment, long time, long usage) {
        long skipTime = event == null ? Long.MIN_VALUE : Math.min(time, event.getEventTime() - 1);
        release(); // release old segment
        acquire(newSegment); // acquire new segment
        restart();
        if (skipTime != Long.MIN_VALUE)
            read(null, skipTime, usage);
    }

    void restart() {
        event = null;
        input = null;
    }

    // buffer == null means "skip"
    void read(RecordBuffer buffer, long time, long usage) {
        boolean isTradeHistory = segment.block.getType() == 'H';

        if (event == null) {
            event = MDREventUtil.createEvent(segment.block.getType());
            input = segment.block.getInput();
            event.init(segment.block.getStartTime());
            readNext();
        }
        if (event.getEventTime() <= time) {
            DataRecord[] records = MDREventUtil.getRecords(segment.block.getType(), segment.block.getExchange());
            while (event.getEventTime() <= time) {
                if (buffer != null) {
                    int cipher = MDREventUtil.CODEC.encode(segment.block.getSymbol());
                    for (DataRecord record : records)
                        event.getInto(buffer.add(record, cipher, segment.block.getSymbol()));

                    if (isTradeHistory) {
                        // Get record by exchange from the event to publish to proper regional TnS
                        DataRecord[] regRecords = MDREventUtil.getRecords('H', ((MDRTradeHistory) event).getExchange());
                        if (regRecords != null) {
                            for (DataRecord record : regRecords)
                                event.getInto(buffer.add(record, cipher, segment.block.getSymbol()));
                        }
                    }
                }
                readNext();
            }
        }
        segment.updateUsage(usage);
    }

    private void readNext() {
        try {
            if (input.available() > 0)
                event.read(input);
            else
                event.setEventTime(Long.MAX_VALUE);
        } catch (IOException e) {
            event.setEventTime(Long.MAX_VALUE);
            Log.log.error("Unexpected IOException", e);
        }
    }
}
