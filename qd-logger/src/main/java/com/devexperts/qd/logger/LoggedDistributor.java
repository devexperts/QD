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

import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.impl.AbstractDistributor;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;

public class LoggedDistributor extends AbstractDistributor {
    protected final Logger log;
    private final QDDistributor delegate;

    private volatile RecordProvider addedProvider;
    private volatile RecordProvider removedProvider;

    public LoggedDistributor(Logger log, QDDistributor delegate) {
        this.log = log;
        this.delegate = delegate;
    }

    public RecordProvider getAddedRecordProvider() {
        if (addedProvider == null)
            addedProvider = new LoggedRecordProvider(log.child("added"), delegate.getAddedRecordProvider());
        return addedProvider;
    }

    public RecordProvider getRemovedRecordProvider() {
        if (removedProvider == null)
            removedProvider = new LoggedRecordProvider(log.child("removed"), delegate.getRemovedRecordProvider());
        return removedProvider;
    }

    public void close() {
        log.debug("close()");
        delegate.close();
    }

    public QDStats getStats() {
        return delegate.getStats();
    }

    public void process(RecordSource source) {
        log.debug("processData(...)");
        long startPosition = source.getPosition();
        delegate.processData(source);
        source.setPosition(startPosition);
        BufferedRecordSink buf = new BufferedRecordSink(null);
        RecordCursor cursor;
        while ((cursor = source.next()) != null)
            buf.append(cursor);
        log.debug("processData <= " + buf);
    }
}
