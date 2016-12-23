/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.logging.test;

import java.io.*;
import java.util.Properties;

import com.devexperts.logging.LogFormatter;
import com.devexperts.logging.Logging;
import org.apache.log4j.Category;
import org.apache.log4j.PropertyConfigurator;

/**
 * Tests that {@link com.devexperts.logging.DetailedLogLayout} works as layout with either Log4j version
 * using both Log4J logging facilities and {@link com.devexperts.logging.Logging}.
 */
public class Log4jCompatibilityTest extends LogFormatterTestBase {
	private static final Category log = Category.getInstance(Log4jCompatibilityTest.class);
	private static File logFile;
	public static final File BUILD_TEST_DIR = new File("build/test/");

	protected static String loadFile(final File file) throws IOException {
		InputStream is = null;
		try {
			is = new BufferedInputStream(new FileInputStream(file), 1024);

			final Reader reader = new InputStreamReader(is);
			final StringBuilder result = new StringBuilder();
			char[] buf = new char[1024];
			int count;
			while ((count = reader.read(buf)) != -1) {
				result.append(buf, 0, count);
			}

			return result.toString();
		} finally {
			if (is != null) {
				is.close();
			}
		}
	}

	protected void setUp() throws Exception {
		super.setUp();

		initLogFormatter();

		final Properties props = new Properties();
		props.load(Log4jCompatibilityTest.class.getResourceAsStream("/test.log4j.properties"));
		// Create log file in folder that will be eventually cleared - "deleteOnExit" does not work for log files.
		BUILD_TEST_DIR.mkdirs();
		logFile = File.createTempFile("test.", ".log", BUILD_TEST_DIR);
		props.setProperty("log4j.appender.commonFileAppender.file", logFile.getPath());
		PropertyConfigurator.configure(props);
	}

	protected void initLogFormatter() {
		System.getProperties().setProperty(LogFormatter.CONFIG_FILE_PROPERTY,
			Log4jCompatibilityTest.class.getResource("/test.logformatter.properties").toExternalForm());
	}

	public void testLog4JLogging() throws IOException {
//		Logging log = Logging.getLogging(Log4jCompatibilityTest.class);
//		log.configureDebugEnabled(true);
		final String log4jVersion = Package.getPackage("org.apache.log4j").getImplementationVersion();
		final String log4j_message = "Log4j version: ";
		final String test_message = "Test log4j message";
		log.debug(log4j_message + log4jVersion);
		log.debug(test_message);

		final String content = loadFile(logFile);
		assertTrue("'" + log4j_message + "' not found in the log", content.indexOf(log4j_message) != -1);
		assertTrue("'" + test_message + "' not found in log file", content.indexOf(test_message) != -1);
	}

	public void testDevexpertsLogging() throws IOException {
		Logging log = Logging.getLogging(Log4jCompatibilityTest.class);
		log.configureDebugEnabled(true);
		final String test_message = "Test com.devexperts.logging message";
		log.debug(test_message);

		final String content = loadFile(logFile);
		assertTrue("'" + test_message + "' not found in log file", content.indexOf(test_message) != -1);
	}
}
