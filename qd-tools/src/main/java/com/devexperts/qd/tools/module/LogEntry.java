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
package com.devexperts.qd.tools.module;

import com.devexperts.annotation.Experimental;

import java.util.logging.Level;

/**
 * Represents an entry recorded within {@link EventLog}
 */
@Experimental
public interface LogEntry {

    public long getTimestamp();

    /**
     * FIXME: {@link com.devexperts.logging.Logging} doesn't provide any log level abstraction, but java.util.logging
     * is always available.
     */
    public Level getLevel();

    public Object getEvent();

}
