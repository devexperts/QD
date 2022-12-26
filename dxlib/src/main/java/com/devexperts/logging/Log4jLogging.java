/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.logging;

import org.apache.log4j.Appender;
import org.apache.log4j.Category;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Priority;
import org.apache.log4j.RollingFileAppender;
import org.apache.log4j.spi.LoggingEvent;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Logging implementation that uses log4j logging facilities.
 */
class Log4jLogging extends DefaultLogging {
    private static final String FQCN = Logging.class.getName() + ".";

    @Override
    Map<String, Exception> configure() {
        if (Category.getCurrentCategories().hasMoreElements() || Category.getRoot().getAllAppenders().hasMoreElements())
            return defaultErrors(); // do nothing since log4j was already configured
        return reconfigure(getProperty(Logging.LOG_FILE_PROPERTY, null));
    }

    @Override
    Map<String, Exception> configureLogFile(String logFile) {
        Category.getRoot().removeAllAppenders();
        return reconfigure(logFile);
    }

    private static Map<String, Exception> defaultErrors() {
        Map<String, Exception> errors = new LinkedHashMap<>();
        errors.put(
            "WARNING: DEPRECATED use of log4j 1.x logging. Migrate to log4j2 or use its bridge to log4j 1.x", null);
        return errors;
    }

    private static Map<String, Exception> reconfigure(String logFile) {
        Map<String, Exception> errors = defaultErrors();
        Appender appender = null;
        if (logFile != null) {
            try {
                appender = createFileAppender(logFile, Logging.LOG_MAX_FILE_SIZE_PROPERTY, errors);
            } catch (Exception e) {
                errors.put(logFile, e);
            }
        }
        if (appender == null)
            appender = new ConsoleAppender(new DetailedLogLayout());
        Category.getRoot().addAppender(appender);

        String errFile = getProperty(Logging.ERR_FILE_PROPERTY, null);
        if (errFile != null) {
            try {
                RollingFileAppender errAppender = createFileAppender(errFile, Logging.ERR_MAX_FILE_SIZE_PROPERTY, errors);
                errAppender.setThreshold(Priority.WARN);
                Category.getRoot().addAppender(errAppender);
            } catch (Exception e) {
                errors.put(errFile, e);
            }
        }
        return errors;
    }

    private static RollingFileAppender createFileAppender(String logFile, String maxSizeKey,
        Map<String, Exception> errors) throws IOException
    {
        RollingFileAppender appender = new RollingFileAppender(new DetailedLogLayout(), logFile, true);
        int limit = getLimit(maxSizeKey, errors);
        if (limit != 0)
            appender.setMaxFileSize(Integer.toString(limit));
        return appender;
    }

    @Override
    Object getPeer(String name) {
        return Category.getInstance(name);
    }

    @Override
    String getName(Object peer) {
        return ((Category) peer).getName();
    }

    @Override
    boolean debugEnabled(Object peer) {
        return ((Category) peer).isEnabledFor(Priority.DEBUG);
    }

    @Override
    void setDebugEnabled(Object peer, boolean debugEnabled) {
        Category category = (Category) peer;
        Priority priority = category.getPriority(); // may be null if was not directly configured
        if (debugEnabled) {
            if (priority == null || priority.isGreaterOrEqual(Priority.DEBUG))
                category.setPriority(Priority.DEBUG);
        } else {
            if (priority == null || Priority.INFO.isGreaterOrEqual(priority))
                category.setPriority(Priority.INFO);
        }
    }

    @Override
    void log(Object peer, Level level, String msg, Throwable t) {
        Priority priority;
        if (level.intValue() <= Level.FINE.intValue())
            priority = Priority.DEBUG;
        else if (level.intValue() <= Level.INFO.intValue())
            priority = Priority.INFO;
        else if (level.intValue() <= Level.WARNING.intValue())
            priority = Priority.WARN;
        else
            priority = Priority.ERROR;

        if (!((Category) peer).isEnabledFor(priority))
            return;

        // Before calling log4j logger we must clear "interrupted" flag from current thread.
        // If this flag is "true", log4j will log error in 1 appender only (and probably clear the flag).
        // We will re-establish "interrupted" flag later.
        boolean interrupted = Thread.interrupted();
        try {
            ((Category) peer).callAppenders(new LoggingEvent(FQCN, ((Category) peer), priority, msg == null ? "" : msg, t));
        } catch (Exception e) {
            System.err.println(new LogFormatter().format('E', System.currentTimeMillis(),
                Thread.currentThread().getName(), "Log4j", e + " during logging of " + msg));
            if (!(e instanceof IllegalStateException) || e.getMessage() == null ||
                !e.getMessage().equals("Current state = FLUSHED, new state = CODING"))
            {
                e.printStackTrace(System.err);
            }
        } finally {
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }
}
