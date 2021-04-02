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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/**
 * Tests that {@link com.devexperts.logging.DxFeedPatternLayout} works as layout with either Log4j2 version
 * using both Log4J2 logging facilities and {@link Logging}.
 */
public class Log4j2CompatibilityTest extends LogFormatterTestBase {
    private static File logFile;
    public static final File BUILD_TEST_DIR = new File("build/test/");

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        System.getProperties().setProperty(Logging.LOG_CLASS_NAME, "com.devexperts.logging.Log4j2Logging");
        initLogFormatter();

        // Create log file in folder that will be eventually cleared - "deleteOnExit" does not work for log files.
        BUILD_TEST_DIR.mkdirs();
        logFile = File.createTempFile("test.", ".log", BUILD_TEST_DIR);
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

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (logFile.exists())
            logFile.delete();
    }

    protected void initLogFormatter() {
        System.getProperties().setProperty(LogFormatter.CONFIG_FILE_PROPERTY,
            Log4j2CompatibilityTest.class.getResource("/test.logformatter.properties").toExternalForm());
    }

    public void testLog4JLogging() throws IOException {
        Logger logger = LogManager.getContext(false).getLogger("file");
        final String log4jVersion = Package.getPackage("org.apache.logging.log4j.core").getImplementationVersion();
        final String log4j_message = "Log4j version: ";
        final String test_message = "Test log4j message";
        logger.debug(log4j_message + log4jVersion);
        logger.debug(test_message);
        logger.debug("error", new IllegalArgumentException());
        logger.debug(test_message);

        final String content = loadFile(logFile);
        assertTrue("'" + log4j_message + "' not found in the log", content.contains(log4j_message));
        assertTrue("'" + test_message + "' not found in log file", content.contains(test_message));
        assertTrue("Exception not found in log file", content.contains(IllegalArgumentException.class.getName()));
        assertTrue("Exception stack trace not found in log file", content.contains("\tat " + getClass().getName()));
    }

    public void testDevexpertsLogging() throws IOException {
        Logging log = Logging.getLogging(Log4j2CompatibilityTest.class);
        log.configureDebugEnabled(true);
        final String test_message = "Test com.devexperts.logging message";
        log.debug(test_message);
        log.debug("error", new IllegalArgumentException());
        log.debug(test_message);

        final String content = loadFile(logFile);
        assertTrue("'" + test_message + "' not found in log file", content.contains(test_message));
        assertTrue("Exception not found in log file", content.contains(IllegalArgumentException.class.getName()));
        assertTrue("Exception stack trace not found in log file", content.contains("\tat " + getClass().getName()));
    }

    public void testConfigureDebugEnabled() throws IOException {
        goTestConfigureDebugEnbled(true);
    }
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
