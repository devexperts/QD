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
package com.devexperts.logging.test;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import com.devexperts.logging.LogFormatter;
import com.devexperts.logging.Logging;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.properties.*;

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

        initLogFormatter();
        System.getProperties().setProperty(Logging.LOG_CLASS_NAME, "com.devexperts.logging.Log4j2Logging");

        // Create log file in folder that will be eventually cleared - "deleteOnExit" does not work for log files.
        BUILD_TEST_DIR.mkdirs();
        logFile = File.createTempFile("test.", ".log", BUILD_TEST_DIR);
        final Properties props = new Properties();
        props.load(Log4jCompatibilityTest.class.getResourceAsStream("/test.log4j2.properties"));
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
        context.setConfigLocation(Log4jCompatibilityTest.class.getResource("/test.log4j2.properties").toURI());
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

        final String content = loadFile(logFile);
        assertTrue("'" + log4j_message + "' not found in the log", content.indexOf(log4j_message) != -1);
        assertTrue("'" + test_message + "' not found in log file", content.indexOf(test_message) != -1);
    }

    public void testDevexpertsLogging() throws IOException {
        Logging log = Logging.getLogging(Log4j2CompatibilityTest.class);
        log.configureDebugEnabled(true);
        final String test_message = "Test com.devexperts.logging message";
        log.debug(test_message);

        final String content = loadFile(logFile);
        assertTrue("'" + test_message + "' not found in log file", content.indexOf(test_message) != -1);
    }
}
