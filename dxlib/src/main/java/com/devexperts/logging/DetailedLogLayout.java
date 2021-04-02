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
package com.devexperts.logging;

import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

import java.lang.reflect.Field;

/**
 * Detailed custom layout for log4j.
 */
public class DetailedLogLayout extends Layout {
    private static final Field priority = getPriorityField();

    private static Field getPriorityField() {
        try {
            return LoggingEvent.class.getField("priority");
        } catch (NoSuchFieldException ignored) {
            // ignored
        }
        try {
            return LoggingEvent.class.getField("level");
        } catch (NoSuchFieldException ignored) {
            // ignored
        }
        throw new ExceptionInInitializerError("Cannot find LoggingEvent priority or level");
    }

    private final LogFormatter formatter = new LogFormatter();

    public void activateOptions() {
    }

    public String[] getOptionStrings() {
        return new String[0];
    }

    public void setOption(String option, String value) {
    }

    public boolean ignoresThrowable() {
        return true;
    }

    public String format(LoggingEvent event) {
        try {
            // Workaround for incompatibility between log4j 1.1.3 and 1.2.5.
            // Field priority was removed in the latter version.
            // See http://www.qos.ch/logging/preparingFor13.jsp
            return formatter.format(priority.get(event).toString().charAt(0), event.timeStamp,
                event.getThreadName(), event.categoryName, event.getRenderedMessage());
        } catch (IllegalAccessException e) {
            // should not happen
            throw (Error) (new InternalError().initCause(e));
        }
    }
}
