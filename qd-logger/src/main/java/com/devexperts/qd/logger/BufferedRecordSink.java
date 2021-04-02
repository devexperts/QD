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
package com.devexperts.qd.logger;

import com.devexperts.qd.ng.AbstractRecordSink;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordSink;

public class BufferedRecordSink extends AbstractRecordSink {
    private final StringBuilder sb = new StringBuilder();
    private final RecordSink delegate;

    // delegate may be null (only records to string buffer)
    public BufferedRecordSink(RecordSink delegate) {
        this.delegate = delegate;
    }

    @Override
    public boolean hasCapacity() {
        return delegate == null || delegate.hasCapacity();
    }

    @Override
    public void append(RecordCursor cursor) {
        if (sb.length() > 0)
            sb.append(", ");
        Logger.appendRecord(sb, cursor.getRecord(), cursor.getCipher(), cursor.getSymbol());
        if (cursor.hasTime())
            Logger.appendTime(sb, cursor.getRecord(), cursor.getInt(0));
        if (delegate != null)
            delegate.append(cursor);
    }

    public String toString() {
        return sb.toString();
    }
}
