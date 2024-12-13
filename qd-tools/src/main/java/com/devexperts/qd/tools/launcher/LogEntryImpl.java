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
package com.devexperts.qd.tools.launcher;

import com.devexperts.qd.tools.module.LogEntry;

import java.util.Objects;
import java.util.logging.Level;

class LogEntryImpl implements LogEntry {

    private final Level level;
    private final long timestamp;
    private final Object event;

    public LogEntryImpl(long timestamp, Level level, Object event) {
        this.timestamp = timestamp;
        this.level = Objects.requireNonNull(level, "level must not be null");
        this.event = Objects.requireNonNull(event, "event must not be null");
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public Object getEvent() {
        return event;
    }

    @Override
    public Level getLevel() {
        return level;
    }
}
