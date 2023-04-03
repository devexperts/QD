/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.logging.test;

import com.devexperts.logging.LogFormatter;
import com.devexperts.logging.Logging;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.properties.PropertiesConfiguration;
import org.apache.logging.log4j.core.config.properties.PropertiesConfigurationBuilder;
import org.apache.logging.log4j.core.config.properties.PropertiesConfigurationFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Tests that {@link com.devexperts.logging.DxFeedPatternLayout} works as layout with either Log4j2 version
 * using both Log4J2 logging facilities and {@link Logging}.
 */
public class Log4j2CompatibilityTest extends LogFormatterTestBase {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File logFile;

    @Before
    public void setUp() throws Exception {
        System.setProperty(Logging.LOG_CLASS_NAME, "com.devexperts.logging.Log4j2Logging");
        initLogFormatter();

        logFile = new File(tempFolder.getRoot(), "test.log");
        final Properties props = new Properties();
        props.load(Log4j2CompatibilityTest.class.getResourceAsStream("/test.log4j2.properties"));
        props.setProperty("appender.file.fileName", logFile.getPath());
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        ConfigurationFactory.setConfigurationFactory(new PropertiesConfigurationFactory() {
            @Override
            public PropertiesConfiguration getConfiguration(LoggerContext loggerContext, ConfigurationSource source) {
                return new PropertiesConfigurationBuilder()
                    .setConfigurationSource(source)
                    .setRootProperties(props)
                    .setLoggerContext(loggerContext)
                    .build();
            }
        });
        context.setConfigLocation(Log4j2CompatibilityTest.class.getResource("/test.log4j2.properties").toURI());
    }

    protected void initLogFormatter() {
        System.setProperty(LogFormatter.CONFIG_FILE_PROPERTY,
            Log4j2CompatibilityTest.class.getResource("/test.logformatter.properties").toExternalForm());
    }

    @Test
    public void testLog4JLogging() throws IOException {
        Logger logger = LogManager.getContext(false).getLogger("file");
        final String log4jVersion = Package.getPackage("org.apache.logging.log4j.core").getImplementationVersion();
        final String log4jMessage = "Log4j version: ";
        final String testMessage = "Test log4j message";
        logger.debug(log4jMessage + log4jVersion);
        logger.debug(testMessage);
        logger.debug("error", new IllegalArgumentException());
        logger.debug(testMessage);

        final String content = loadFile(logFile);
        assertTrue("'" + log4jMessage + "' not found in the log", content.contains(log4jMessage));
        assertTrue("'" + testMessage + "' not found in log file", content.contains(testMessage));
        assertTrue("Exception not found in log file", content.contains(IllegalArgumentException.class.getName()));
        assertTrue("Exception stack trace not found in log file", content.contains("\tat " + getClass().getName()));
    }

    @Test
    public void testDevexpertsLogging() throws IOException {
        Logging log = Logging.getLogging(Log4j2CompatibilityTest.class);
        log.configureDebugEnabled(true);
        final String testMessage = "Test com.devexperts.logging message";
        log.debug(testMessage);
        log.debug("error", new IllegalArgumentException());
        log.debug(testMessage);

        final String content = loadFile(logFile);
        assertTrue("'" + testMessage + "' not found in log file", content.contains(testMessage));
        assertTrue("Exception not found in log file", content.contains(IllegalArgumentException.class.getName()));
        assertTrue("Exception stack trace not found in log file", content.contains("\tat " + getClass().getName()));
    }

    @Test
    public void testConfigureDebugEnabled() throws IOException {
        goTestConfigureDebugEnbled(true);
    }

    @Test
    public void testConfigureDebugDisabled() throws IOException {
        goTestConfigureDebugEnbled(false);
    }

    private void goTestConfigureDebugEnbled(boolean defaultDebugEnabled) throws IOException {
        Logger defaultLog = LogManager.getLogger("default.test");
        Logger errorLog = LogManager.getLogger("default.error");
        Logger traceLog = LogManager.getLogger("default.trace");

        assertEquals(Level.DEBUG, defaultLog.getLevel());
        assertEquals(Level.ERROR, errorLog.getLevel());
        assertEquals(Level.TRACE, traceLog.getLevel());

        List<Logger> loggers = Arrays.asList(defaultLog, errorLog, traceLog);
        List<Level> levels = Arrays.asList(Level.ERROR, Level.WARN, Level.INFO, Level.DEBUG, Level.TRACE);
        for (Logger logger : loggers) {
            Logging.getLogging(logger.getName()).configureDebugEnabled(defaultDebugEnabled);
            for (Level level : levels) {
                logger.log(level, logger.getName() + "-" + level.name());
            }
        }
        final String content = loadFile(logFile);

        if (defaultDebugEnabled) {
            loggers.forEach(
                l -> assertTrue("Debug disabled for " + l.getName(), Logging.getLogging(l.getName()).debugEnabled())
            );
            checkLevelRange(defaultLog, levels, content, Level.ERROR, Level.DEBUG);
            checkLevelRange(errorLog, levels, content, Level.ERROR, Level.DEBUG);
            checkLevelRange(traceLog, levels, content, Level.ERROR, Level.TRACE);
        } else {
            loggers.forEach(
                l -> assertFalse("Debug enabled for " + l.getName(), Logging.getLogging(l.getName()).debugEnabled())
            );
            checkLevelRange(defaultLog, levels, content, Level.ERROR, Level.INFO);
            checkLevelRange(errorLog, levels, content, Level.ERROR, Level.ERROR);
            checkLevelRange(traceLog, levels, content, Level.ERROR, Level.INFO);
        }
    }

    private void checkLevelRange(Logger logger, List<Level> levels, String content, Level min, Level max) {
        String name = logger.getName();
        for (Level level : levels) {
            boolean expected = level.isInRange(min, max);
            assertEquals((expected ? "Expected" : "Unexpected") + " message of level " + level + " for logger " + name,
                expected, content.contains(logger.getName() + "-" + level.name()));
        }
    }
}
