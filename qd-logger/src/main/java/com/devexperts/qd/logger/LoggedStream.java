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

import com.devexperts.qd.QDStream;

public class LoggedStream extends LoggedCollector implements QDStream {
    private final QDStream delegate;

    public LoggedStream(Logger log, QDStream delegate, Builder<?> builder) {
        super(log, delegate, builder);
        this.delegate = delegate;
    }

    @Override
    public void setEnableWildcards(boolean enableWildcards) {
        log.debug("setEnableWildcards(" + enableWildcards + ")");
        delegate.setEnableWildcards(enableWildcards);
    }

    @Override
    public boolean getEnableWildcards() {
        return delegate.getEnableWildcards();
    }
}
