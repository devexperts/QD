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
package com.devexperts.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.SimpleMessage;
import org.apache.logging.log4j.spi.LoggerContext;
import org.apache.logging.log4j.status.StatusLogger;

import java.util.Collections;
import java.util.Map;
import java.util.logging.Level;

import static org.apache.logging.log4j.Level.DEBUG;
import static org.apache.logging.log4j.Level.ERROR;
import static org.apache.logging.log4j.Level.INFO;
import static org.apache.logging.log4j.Level.OFF;
import static org.apache.logging.log4j.Level.WARN;

/**
 * Logging implementation that uses log4j2 logging facilities.
 *
 * <p>This class should strictly use only Log4j2 API and SPI functionality.
 * This approach enables the use of other implementations that act as bridges to external logging frameworks.
 */
class Log4j2Logging extends DefaultLogging {
    private static final String FQCN = Logging.class.getName() + ".";

    private static final Log4j2Core core = createCoreIfAvailable();

    static {
        StatusLogger.getLogger().setLevel(OFF);
    }

    private static Log4j2Core createCoreIfAvailable() {
        // Use LoggerContext resolution via the LogManager,
        // since it can be overridden even in the presence of Log4j2 Core implementation
        LoggerContext context = LogManager.getContext(false);

        boolean isCoreLib = context.getClass().getName().equals("org.apache.logging.log4j.core.LoggerContext");
        try {
            // Use Log4j2 Core for configuration
            if (isCoreLib)
                return new Log4j2Core();
        } catch (Throwable ignored) {
        }
        // Use strictly Log4j2 API/SPI
        return null;
    }

    @Override
    Map<String, Exception> configure() {
        return (core != null) ? core.configure() : Collections.emptyMap();
    }

    @Override
    Map<String, Exception> configureLogFile(String logFile) {
        return (core != null) ? core.reconfigure(logFile) : Collections.emptyMap();
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
        if (core != null)
            core.setDebugEnabled(peer, debugEnabled);
    }

    @Override
    void log(Object peer, Level level, String msg, Throwable t) {
        org.apache.logging.log4j.Level priority;
        if (level.intValue() <= Level.FINE.intValue()) {
            priority = DEBUG;
        } else if (level.intValue() <= Level.INFO.intValue()) {
            priority = INFO;
        } else if (level.intValue() <= Level.WARNING.intValue()) {
            priority = WARN;
        } else {
            priority = ERROR;
        }

        if (!((Logger) peer).isEnabled(priority))
            return;

        // Before calling log4j logger we must clear "interrupted" flag from current thread.
        // If this flag is "true", log4j will log error in 1 appender only (and probably clear the flag).
        // We will re-establish "interrupted" flag later.
        boolean interrupted = Thread.interrupted();
        try {
            ((Logger) peer).logMessage(priority, null, FQCN, null, new SimpleMessage(msg == null ? "" : msg), t);
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
