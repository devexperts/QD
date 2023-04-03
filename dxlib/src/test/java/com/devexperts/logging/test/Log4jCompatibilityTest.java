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
import org.apache.log4j.Category;
import org.apache.log4j.Priority;
import org.apache.log4j.PropertyConfigurator;
import org.junit.After;
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
 * Tests that {@link com.devexperts.logging.DetailedLogLayout} works as layout with either Log4j version
 * using both Log4J logging facilities and {@link Logging}.
 */
public class Log4jCompatibilityTest extends LogFormatterTestBase {
    private static final Category log = Category.getInstance(Log4jCompatibilityTest.class);

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private File logFile;

    @Before
    public void setUp() throws Exception {
        System.setProperty(Logging.LOG_CLASS_NAME, "com.devexperts.logging.Log4jLogging");
        initLogFormatter();

        final Properties props = new Properties();
        props.load(Log4jCompatibilityTest.class.getResourceAsStream("/test.log4j.properties"));

        logFile = new File(tempFolder.getRoot(), "test.log");
        props.setProperty("log4j.appender.commonFileAppender.file", logFile.getPath());
        PropertyConfigurator.configure(props);
    }

    @After
    public void tearDown() throws Exception {
        Category.shutdown();
        Category.getDefaultHierarchy().resetConfiguration();
    }

    protected void initLogFormatter() {
        System.setProperty(LogFormatter.CONFIG_FILE_PROPERTY,
            Log4jCompatibilityTest.class.getResource("/test.logformatter.properties").toExternalForm());
    }

    @Test
    public void testLog4JLogging() throws IOException {
        final String log4jVersion = Package.getPackage("org.apache.log4j").getImplementationVersion();
        final String log4jMessage = "Log4j version: ";
        final String testMessage = "Test log4j message";
        log.debug(log4jMessage + log4jVersion);
        log.debug(testMessage);
        log.debug("error", new IllegalArgumentException());

        final String content = loadFile(logFile);
        assertTrue("'" + log4jMessage + "' not found in the log", content.contains(log4jMessage));
        assertTrue("'" + testMessage + "' not found in log file", content.contains(testMessage));
        assertTrue("Exception not found in log file", content.contains(IllegalArgumentException.class.getName()));
        assertTrue("Exception stack trace not found in log file", content.contains("\tat " + getClass().getName()));
    }

    @Test
    public void testDevexpertsLogging() throws IOException {
        Logging log = Logging.getLogging(Log4jCompatibilityTest.class);
        log.configureDebugEnabled(true);
        final String testMessage = "Test com.devexperts.logging message";
        log.debug(testMessage);
        log.debug("error", new IllegalArgumentException());

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
        Logging log = Logging.getLogging(Log4jCompatibilityTest.class);
        Category defaultLog = Category.getInstance("default.test");
        Category errorLog = Category.getInstance("default.error");
        // TODO: log4j 1.1.3 doesn't support more specific levels than DEBUG (but 1.2.x does)
        //Category traceLog = Category.getInstance("default.trace");

        assertEquals(Priority.DEBUG, defaultLog.getChainedPriority()); 
        assertEquals(Priority.ERROR, errorLog.getPriority());
        //assertEquals(Priority.TRACE, traceLog.getPriority());

        List<Category> loggers = Arrays.asList(defaultLog, errorLog);
        List<Priority> levels = Arrays.asList(Priority.ERROR, Priority.WARN, Priority.INFO, Priority.DEBUG);
        for (Category logger : loggers) {
            Logging.getLogging(logger.getName()).configureDebugEnabled(defaultDebugEnabled);
            for (Priority level : levels) {
                logger.log(level, logger.getName() + "-" + level.toString());
            }
        }
        final String content = loadFile(logFile);

        if (defaultDebugEnabled) {
            loggers.forEach(
                l -> assertTrue("Debug disabled for " + l.getName(), Logging.getLogging(l.getName()).debugEnabled())
            );
            checkLevelRange(defaultLog, levels, content, Priority.ERROR, Priority.DEBUG);
            checkLevelRange(errorLog, levels, content, Priority.ERROR, Priority.DEBUG);
            //checkLevelRange(traceLog, levels, content, Level.ERROR, Level.TRACE);
        } else {
            loggers.forEach(
                l -> assertFalse("Debug enabled for " + l.getName(), Logging.getLogging(l.getName()).debugEnabled())
            );
            checkLevelRange(defaultLog, levels, content, Priority.ERROR, Priority.INFO);
            checkLevelRange(errorLog, levels, content, Priority.ERROR, Priority.ERROR);
            //checkLevelRange(traceLog, levels, content, Level.ERROR, Level.INFO);
        }
    }

    private void checkLevelRange(Category logger, List<Priority> levels, String content, Priority top, Priority low) {
        assert top.isGreaterOrEqual(low) : "more specific log4j priorities has higher int-values";
        String name = logger.getName();
        for (Priority level : levels) {
            boolean expected = level.isGreaterOrEqual(low) && top.isGreaterOrEqual(level);
            assertEquals((expected ? "Expected" : "Unexpected") + " message of level " + level + " for logger " + name,
                expected, content.contains(logger.getName() + "-" + level.toString()));
        }
    }
}
