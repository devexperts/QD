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
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.NullConfiguration;
import org.apache.logging.log4j.core.filter.ThresholdFilter;
import org.apache.logging.log4j.core.layout.AbstractStringLayout;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.apache.logging.log4j.Level.DEBUG;
import static org.apache.logging.log4j.Level.INFO;
import static org.apache.logging.log4j.Level.WARN;
import static org.apache.logging.log4j.core.Filter.Result.ACCEPT;
import static org.apache.logging.log4j.core.Filter.Result.DENY;
import static org.apache.logging.log4j.core.config.ConfigurationSource.NULL_SOURCE;

/**
 * Helper class for configuring logging using the Log4j2 Core module.
 */
class Log4j2Core {

    void setDebugEnabled(Object peer, boolean debugEnabled) {
        Logger logger = (Logger) peer;
        if (debugEnabled) {
            if (logger.getLevel().isMoreSpecificThan(DEBUG))
                logger.setLevel(DEBUG);
        } else {
            if (logger.getLevel().isLessSpecificThan(INFO))
                logger.setLevel(INFO);
        }
    }

    Map<String, Exception> configure() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        if (ctx.getConfiguration().getConfigurationSource() != NULL_SOURCE) {
            // Do nothing since log4j2 was already configured
            return Collections.emptyMap();
        }
        return reconfigure(DefaultLogging.getProperty(Logging.LOG_FILE_PROPERTY, null));
    }

    Map<String, Exception> reconfigure(String logFile) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();

        Map<String, Exception> errors = new LinkedHashMap<>();
        config.getRootLogger().setLevel(DEBUG);
        String errFile = DefaultLogging.getProperty(Logging.ERR_FILE_PROPERTY, null);
        for (Map.Entry<String, Appender> entry : config.getRootLogger().getAppenders().entrySet()) {
            entry.getValue().stop();
            // Safe to delete here since config.getRootLogger().getAppenders() returns new map
            config.getRootLogger().removeAppender(entry.getKey());
        }

        Appender appender = null;
        if (logFile != null) {
            try {
                appender = createFileAppender("common", logFile, Logging.LOG_MAX_FILE_SIZE_PROPERTY, errors);
            } catch (Exception e) {
                errors.put(logFile, e);
            }
        }

        if (appender == null) {
            appender = ConsoleAppender.newBuilder()
                .setName("common")
                .setLayout(getDetailedLayout())
                .setTarget(ConsoleAppender.Target.SYSTEM_OUT)
                .build();
        }

        config.getRootLogger().addAppender(appender, DEBUG,
            (errFile == null) ? null : ThresholdFilter.createFilter(WARN, DENY, ACCEPT));

        if (errFile != null) {
            try {
                config.getRootLogger().addAppender(
                    createFileAppender("error", errFile, Logging.ERR_MAX_FILE_SIZE_PROPERTY, errors),
                    WARN,
                    ThresholdFilter.createFilter(WARN, ACCEPT, DENY));
            } catch (Exception e) {
                errors.put(errFile, e);
            }
        }
        ctx.updateLoggers();
        return errors;
    }

    private static AbstractStringLayout getDetailedLayout() {
        return DxFeedPatternLayout.createDefaultLayout(null);
    }

    private static RollingFileAppender createFileAppender(String name, String logFile, String maxSizeKey,
        Map<String, Exception> errors)
    {
        int limit = DefaultLogging.getLimit(maxSizeKey, errors);
        if (limit == 0)
            limit = 900 * 1024 * 1024; // Default in Logging.DEFAULT_MAX_FILE_SIZE

        RollingFileAppender.Builder<?> builder = RollingFileAppender.newBuilder()
            .withFileName(logFile)
            .withFilePattern(logFile)
            .withAppend(true)
            .withPolicy(SizeBasedTriggeringPolicy.createPolicy(Integer.toString(limit)))
            .setConfiguration(new NullConfiguration())
            .setName(name)
            .setImmediateFlush(true)
            .setLayout(getDetailedLayout());

        return builder.build();
    }
}
