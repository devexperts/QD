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

import java.io.*;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

import com.devexperts.logging.LogFormatter;
import junit.framework.TestCase;

/**
 * Base class for tests for {@link LogFormatter}.
 */
public abstract class LogFormatterTestBase extends TestCase {
    protected final String REGEX = "D \\d{6}? \\d{6}?\\.\\d{3}? \\[_ThreadName_\\] MyCategory \\- My message((\\r)|(\\n))+";

    protected LogFormatter formatter;

    public LogFormatterTestBase() {
    }

    public LogFormatterTestBase(String name) {
        super(name);
    }

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

    /**
     * Calls formatter to format string with predefined data and given thread name using log4j method.
     *
     * @param thread_name
     * @return
     */
    protected String getLog4jFormattedString(final String thread_name) {
        return formatter.format('D', System.currentTimeMillis(), thread_name, "MyCategory", "My message");
    }

    /**
     * Calls formatter to format string with predefined data and given thread name
     * using {@link java.util.logging} method.
     *
     * @param thread_name
     * @return
     */
    protected String getJULFormattedString(final String thread_name) {
        final LogRecord record = new LogRecord(Level.FINE, "My message");
        record.setLoggerName("MyCategory");
        final String current_thread_name = Thread.currentThread().getName();
        Thread.currentThread().setName(thread_name);
        final String formatted_string = formatter.format(record);
        Thread.currentThread().setName(current_thread_name);
        return formatted_string;
    }

    /**
     * Creates pattern which should match formatted string created for given thread name.
     *
     * @param thread_name
     * @return
     */
    protected String getPattern(final String thread_name) {
        return REGEX.replace("_ThreadName_", Pattern.quote(thread_name));
    }

    /**
     * Compares formatted string with pattern and throws AssertionFailedError if they do not match.
     */
    protected void checkResultMatches(final String original_thread_name, final String formatted_thread_name) {
        final String log4j_formatted_string = getLog4jFormattedString(original_thread_name);
        final String jul_formatted_string = getJULFormattedString(original_thread_name);
        final String pattern = getPattern(formatted_thread_name);
        assertTrue("Obtained string '" + log4j_formatted_string + "' does not match regexp '" + pattern + "'", log4j_formatted_string.matches(pattern));
        assertTrue("Obtained string '" + jul_formatted_string + "' does not match regexp '" + pattern + "'", jul_formatted_string.matches(pattern));
    }

    /**
     * Compares formatted string with pattern and throws AssertionFailedError if they match.
     */
    protected void checkResultDoesNotMatch(final String original_thread_name, final String formatted_thread_name) {
        final String log4j_formatted_string = getLog4jFormattedString(original_thread_name);
        final String jul_formatted_string = getJULFormattedString(original_thread_name);
        final String pattern = getPattern(formatted_thread_name);
        assertFalse("Obtained string '" + log4j_formatted_string + "' should not match regexp '" + pattern + "'", log4j_formatted_string.matches(pattern));
        assertFalse("Obtained string '" + jul_formatted_string + "' should not match regexp '" + pattern + "'", jul_formatted_string.matches(pattern));
    }
}
