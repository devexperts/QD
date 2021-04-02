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

import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordProvider;

public class LoggedRecordListener implements RecordListener {
    private final Logger log;
    private final RecordListener delegate;
    private final RecordProvider srcProvider;

    public LoggedRecordListener(Logger log, RecordListener delegate, RecordProvider srcProvider) {
        this.log = log;
        this.delegate = delegate;
        this.srcProvider = srcProvider;
    }

    public void recordsAvailable(RecordProvider provider) {
        log.debug("recordsAvailable(" + provider + ")");
        delegate.recordsAvailable(srcProvider);
    }
}
