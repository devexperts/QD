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

import com.devexperts.qd.ng.AbstractRecordProvider;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSink;

public class LoggedRecordProvider extends AbstractRecordProvider {
    protected final Logger log;

    private final RecordProvider delegate;

    public LoggedRecordProvider(Logger log, RecordProvider delegate) {
        this.log = log;
        this.delegate = delegate;
    }

    @Override
    public RecordMode getMode() {
        return delegate.getMode();
    }

    @Override
    public boolean retrieve(RecordSink sink) {
        log.debug("retrieve(...)");
        BufferedRecordSink buf = new BufferedRecordSink(sink);
        boolean result = delegate.retrieve(buf);
        log.debug("retrieve = " + result + " <= " + buf);
        return result;
    }

    @Override
    public void setRecordListener(RecordListener listener) {
        log.debug("setRecordListener(" + listener + ")");
        delegate.setRecordListener(listener == null || listener == RecordListener.VOID ?
            listener : new LoggedRecordListener(log.child("listener"), listener, this));
    }
}
