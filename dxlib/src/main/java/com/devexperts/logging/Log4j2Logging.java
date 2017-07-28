/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.logging;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.*;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.NullConfiguration;
import org.apache.logging.log4j.core.filter.ThresholdFilter;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.status.StatusLogger;

import static org.apache.logging.log4j.Level.*;
import static org.apache.logging.log4j.core.Filter.Result.ACCEPT;
import static org.apache.logging.log4j.core.Filter.Result.DENY;
import static org.apache.logging.log4j.core.config.ConfigurationSource.NULL_SOURCE;

/**
 * Logging implementation that uses log4j logging facilities.
 */
class Log4j2Logging extends DefaultLogging {
    private static final String FQCN = Logging.class.getName() + ".";

    static {
        StatusLogger.getLogger().setLevel(OFF);
    }

    @Override
    Map<String, Exception> configure() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        if (ctx.getConfiguration().getConfigurationSource() != NULL_SOURCE)
            return Collections.emptyMap(); // do nothing since log4j2 was already configured
        return configureLogFile(getProperty(Logging.LOG_FILE_PROPERTY, null));
    }

    private static Map<String, Exception> reconfigure(String logFile) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        Map<String, Exception> errors = new LinkedHashMap<>();
        config.getRootLogger().setLevel(INFO);
        String errFile = getProperty(Logging.ERR_FILE_PROPERTY, null);
        for (Map.Entry<String, Appender> entry : config.getRootLogger().getAppenders().entrySet()) {
            entry.getValue().stop();
            // Safe to delete here since config.getRootLogger().getAppenders() returns new map
            config.getRootLogger().removeAppender(entry.getKey());
        }
        if (logFile != null) {
            try {
                Appender appender = createFileAppender("common", logFile, Logging.LOG_MAX_FILE_SIZE_PROPERTY, errors);
                if (appender != null) {
                    config.getRootLogger().addAppender(appender, INFO,
                        errFile == null ? null : ThresholdFilter.createFilter(WARN, DENY, ACCEPT));
                }
            } catch (Exception e) {
                errors.put(logFile, e);
            }
        }

        if (errFile != null) {
            try {
                Appender appender = createFileAppender("error", errFile, Logging.ERR_MAX_FILE_SIZE_PROPERTY, errors);
                config.getRootLogger().addAppender(appender, WARN, ThresholdFilter.createFilter(WARN, ACCEPT, DENY));
            } catch (Exception e) {
                errors.put(errFile, e);
            }
        }
        ctx.updateLoggers();
        return errors;
    }

    private static PatternLayout getDetailedLayout() {
        // Outputs the first 1M lines of the stack trace as a workaround to make Log4j2 stacktrace format as Log4j
        return PatternLayout.newBuilder().withPattern(
            "%p{length=1} %d{yyMMdd HHmmss.SSS} [%t] %c{1} - %m%n%ex{1000000}%n").build();
    }

    private static RollingFileAppender createFileAppender(String name, String logFile, String maxSizeKey,
        Map<String, Exception> errors) throws IOException
    {
        RollingFileAppender.Builder builder = RollingFileAppender.newBuilder();
        builder.setConfiguration(new NullConfiguration());
        builder.withName(name);
        builder.withLayout(getDetailedLayout());
        builder.withFileName(logFile);
        builder.withFilePattern(logFile);
        builder.withAppend(true);
        builder.withImmediateFlush(true);

        int limit = getLimit(maxSizeKey, errors);
        if (limit == 0)
            limit = 900 * 1024 * 1024; // Default in Logging.DEFAULT_MAX_FILE_SIZE
        builder.withPolicy(SizeBasedTriggeringPolicy.createPolicy(Integer.toString(limit)));

        return builder.build();
    }

    @Override
    Map<String, Exception> configureLogFile(String logFile) {
        return reconfigure(logFile);
    }

    @Override
    Object getPeer(String name) {
        return LogManager.getLogger(name);
    }

    @Override
    String getName(Object peer) {
        return ((Logger) peer).getName();
    }

    @Override
    boolean debugEnabled(Object peer) {
        return ((Logger) peer).isDebugEnabled();
    }

    @Override
    void setDebugEnabled(Object peer, boolean debugEnabled) {
        ((Logger) peer).setLevel(debugEnabled ? DEBUG : INFO);
    }

    @Override
    void log(Object peer, Level level, String msg, Throwable t) {
        org.apache.logging.log4j.Level priority;
        if (level.intValue() <= Level.FINE.intValue())
            priority = DEBUG;
        else if (level.intValue() <= Level.INFO.intValue())
            priority = INFO;
        else if (level.intValue() <= Level.WARNING.intValue())
            priority = WARN;
        else
            priority = ERROR;

        if (!((Logger) peer).isEnabled(priority))
            return;

        // Before calling log4j logger we must clear "interrupted" flag from current thread.
        // If this flag is "true", log4j will log error in 1 appender only (and probably clear the flag).
        // We will re-establish "interrupted" flag later.
        boolean interrupted = Thread.interrupted();
        try {
            ((Logger) peer).logMessage(FQCN, priority, null, new SimpleMessage(msg == null ? "" : msg), t);
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
