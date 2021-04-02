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
package com.dxfeed.ondemand.impl.event;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;
import com.devexperts.qd.ng.RecordCursor;

import java.io.IOException;

public abstract class MDREvent {
    protected long eventTime;

    public long getEventTime() {
        return eventTime;
    }

    public void setEventTime(long eventTime) {
        this.eventTime = eventTime;
    }

    public abstract void init(long startTime);

    public abstract boolean canSkip(MDREvent newEvent);

    public abstract boolean canConflate(MDREvent newEvent);

    public abstract void getInto(RecordCursor cursor);

    public abstract void setFrom(RecordCursor cursor);

    public abstract void setFrom(MDREvent source);

    public abstract void read(ByteArrayInput in) throws IOException;

    public abstract void write(ByteArrayOutput out, MDREvent newEvent) throws IOException;


    protected static int readDeltaFlagged(ByteArrayInput in, int flag, int mask) throws IOException {
        return (flag & mask) == 0 ? 0 : in.readCompactInt();
    }

    protected static int writeDeltaFlagged(ByteArrayOutput out, int oldValue, int newValue, int mask) throws IOException {
        if (newValue == oldValue)
            return 0;
        out.writeCompactInt(newValue - oldValue);
        return mask;
    }

    public Object getExtractorConflationKey(String symbol) {
        return symbol;
    }
}
