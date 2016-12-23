/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.logging;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

import org.apache.log4j.*;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Logging implementation that uses log4j logging facilities.
 */
class Log4jLogging extends DefaultLogging {
    private static final String FQCN = Logging.class.getName() + ".";

    @Override
    Map<String, Exception> configure() {
        if (Category.getCurrentCategories().hasMoreElements() || Category.getRoot().getAllAppenders().hasMoreElements())
            return Collections.emptyMap(); // do nothing since log4j was already configured
        return reconfigure(getProperty(Logging.LOG_FILE_PROPERTY, null));
    }

    @Override
    Map<String, Exception> configureLogFile(String log_file) {
        Category.getRoot().removeAllAppenders();
        return reconfigure(log_file);
    }

    private static Map<String, Exception> reconfigure(String log_file) {
        Map<String, Exception> errors = new LinkedHashMap<String, Exception>();
        Appender appender = null;
        if (log_file != null)
            try {
                appender = createFileAppender(log_file, Logging.LOG_MAX_FILE_SIZE_PROPERTY, errors);
            } catch (Exception e) {
                errors.put(log_file, e);
            }
        if (appender == null)
            appender = new ConsoleAppender(new DetailedLogLayout());
        Category.getRoot().addAppender(appender);
        String err_file = getProperty(Logging.ERR_FILE_PROPERTY, null);
        if (err_file != null)
            try {
                RollingFileAppender rf_appender = createFileAppender(err_file, Logging.ERR_MAX_FILE_SIZE_PROPERTY, errors);
                rf_appender.setThreshold(Priority.WARN);
                Category.getRoot().addAppender(rf_appender);
            } catch (Exception e) {
                errors.put(err_file, e);
            }
        return errors;
    }

    private static RollingFileAppender createFileAppender(String log_file, String max_size_key, Map<String, Exception> errors) throws IOException {
        RollingFileAppender rf_appender = new RollingFileAppender(new DetailedLogLayout(), log_file, true);
        int limit = getLimit(max_size_key, errors);
        if (limit != 0)
            rf_appender.setMaxFileSize(Integer.toString(limit));
        return rf_appender;
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
    void setDebugEnabled(Object peer, boolean debug_enabled) {
        ((Category) peer).setPriority(debug_enabled ? Priority.DEBUG : Priority.INFO);
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
                Thread.currentThread().getName(), "Log4j",	e + " during logging of " + msg));
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
