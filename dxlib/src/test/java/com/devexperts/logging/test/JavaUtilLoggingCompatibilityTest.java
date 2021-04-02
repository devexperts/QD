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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Tests that {@link com.devexperts.logging.DxFeedPatternLayout} works as layout with {@link java.util.logging}
 * using both java.util.logging facilities and {@link Logging}.
 */
public class JavaUtilLoggingCompatibilityTest extends LogFormatterTestBase {

    // mapping java.util.logging levels to conventional DxLogging/Log4j levels
    private static final Level DEBUG = Level.FINE;
    private static final Level ERROR = Level.SEVERE;
    private static final Level TRACE = Level.FINEST;

    private static File logFile;
    public static final File BUILD_TEST_DIR = new File("build/test/");

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    protected void setUp() throws Exception {
        super.setUp();

        System.getProperties().setProperty(Logging.LOG_CLASS_NAME, "com.devexperts.logging.DefaultLogging");
        initLogFormatter();

        // init default levels from external configuration
        try (InputStream cfgIS = getClass().getResourceAsStream("/test.logging.properties")) {
            LogManager.getLogManager().readConfiguration(cfgIS);
        }

        // Create log file in folder that will be eventually cleared - "deleteOnExit" does not work for log files.
        BUILD_TEST_DIR.mkdirs();
        File logFileBase = File.createTempFile("test.", ".log", BUILD_TEST_DIR);
        logFileBase.delete();

        Logging.configureLogFile(logFileBase.getPath());
        // Logging.configureLogFile with java.util.logging creates rotating java.util.logging.FileHandler with count 2.
        // By design of java.util.logging.FileHandler, it will append '.<count>' to the end of a target file name if
        // another place is not specified with '%g' placeholder
        logFile = new File(logFileBase.getPath() + ".0");
        assertTrue(logFile.exists());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (logFile.exists())
            logFile.delete();
    }

    protected void initLogFormatter() {
        System.getProperties().setProperty(LogFormatter.CONFIG_FILE_PROPERTY,
            JavaUtilLoggingCompatibilityTest.class.getResource("/test.logformatter.properties").toExternalForm());
    }

    public void testJavaUtilLogging() throws IOException {
        Logger logger = Logger.getLogger(JavaUtilLoggingCompatibilityTest.class.getName());
        String javaRuntimeVersion =
            System.getProperty("java.runtime.name", "?") + " " + System.getProperty("java.runtime.version") +
            " by " + System.getProperty("java.vendor");
        String javaLoggingMessage = "Java runtime: ";
        String testMessage = "Test java.util.logging message";
        logger.fine(javaLoggingMessage + javaRuntimeVersion);
        logger.fine(testMessage);
        logger.log(DEBUG, "error", new IllegalArgumentException());
        logger.fine(testMessage);

        String content = loadFile(logFile);
        assertTrue("'" + javaLoggingMessage + "' not found in the log", content.contains(javaLoggingMessage));
        assertTrue("'" + testMessage + "' not found in log file", content.contains(testMessage));
        assertTrue("Exception not found in log file", content.contains(IllegalArgumentException.class.getName()));
        assertTrue("Exception stack trace not found in log file", content.contains("\tat " + getClass().getName()));
    }

    public void testDevexpertsLogging() throws IOException {
        Logging log = Logging.getLogging(JavaUtilLoggingCompatibilityTest.class);
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
        Logger defaultLog = Logger.getLogger("default.test");
        Logger errorLog = Logger.getLogger("default.error");
        Logger traceLog = Logger.getLogger("default.trace");

        assertEquals(DEBUG, getEffectiveLevel(defaultLog));
        assertEquals(ERROR, errorLog.getLevel());
        assertEquals(TRACE, traceLog.getLevel());

        List<Logger> loggers = Arrays.asList(defaultLog, errorLog, traceLog);
        List<Level> levels = Arrays.asList(ERROR, Level.WARNING, Level.INFO, DEBUG, TRACE);
        for (Logger logger : loggers) {
            Logging.getLogging(logger.getName()).configureDebugEnabled(defaultDebugEnabled);
            for (Level level : levels) {
                logger.log(level, logger.getName() + "-" + level.getName());
            }
        }
        final String content = loadFile(logFile);

        if (defaultDebugEnabled) {
            loggers.forEach(
                l -> assertTrue("Debug disabled for " + l.getName(), Logging.getLogging(l.getName()).debugEnabled())
            );
            checkLevelRange(defaultLog, levels, content, ERROR, DEBUG);
            checkLevelRange(errorLog, levels, content, ERROR, DEBUG);
            checkLevelRange(traceLog, levels, content, ERROR, TRACE);
        } else {
            loggers.forEach(
                l -> assertFalse("Debug enabled for " + l.getName(), Logging.getLogging(l.getName()).debugEnabled())
            );
            checkLevelRange(defaultLog, levels, content, ERROR, Level.INFO);
            checkLevelRange(errorLog, levels, content, ERROR, ERROR);
            checkLevelRange(traceLog, levels, content, ERROR, Level.INFO);
        }
    }

    private void checkLevelRange(Logger logger, List<Level> levels, String content, Level top, Level low) {
        assertTrue("A less detailed level have greater weight", top.intValue() >= low.intValue());
        String name = logger.getName();
        for (Level level : levels) {
            boolean expected = level.intValue() <= top.intValue() && level.intValue() >= low.intValue();
            assertEquals((expected ? "Expected" : "Unexpected") + " message of level " + level + " for logger " + name,
                expected, content.contains(logger.getName() + "-" + level.getName()));
        }
    }

    private Level getEffectiveLevel(Logger logger) {
        while (logger != null) {
            Level level = logger.getLevel();
            if (level != null)
                return level;
            logger = logger.getParent();
        }
        throw new IllegalStateException("Missing level in the root of Logger hierarchy");
    }
}

