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
import com.devexperts.test.isolated.Isolated;
import com.devexperts.test.isolated.IsolatedRunner;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Base class for tests for {@link LogFormatter}.
 */
@RunWith(IsolatedRunner.class)
@Isolated({"com.devexperts.logging", "org.apache.logging", "org.apache.log4j"})
public abstract class LogFormatterTestBase {
    protected final String REGEX =
        "D \\d{6}? \\d{6}?\\.\\d{3}? \\[_ThreadName_\\] MyCategory \\- My message((\\r)|(\\n))+";

    protected LogFormatter formatter;

    protected static String loadFile(final File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()));
    }

    /**
     * Calls formatter to format string with predefined data and given thread name using log4j method.
     */
    protected String getLog4jFormattedString(final String threadName) {
        return formatter.format('D', System.currentTimeMillis(), threadName, "MyCategory", "My message");
    }

    /**
     * Calls formatter to format string with predefined data and given thread name
     * using {@link java.util.logging} method.
     */
    protected String getJULFormattedString(final String threadName) {
        final LogRecord record = new LogRecord(Level.FINE, "My message");
        record.setLoggerName("MyCategory");
        final String currentThreadName = Thread.currentThread().getName();
        Thread.currentThread().setName(threadName);
        final String formattedString = formatter.format(record);
        Thread.currentThread().setName(currentThreadName);
        return formattedString;
    }

    /**
     * Creates pattern which should match formatted string created for given thread name.
     */
    protected String getPattern(final String threadName) {
        return REGEX.replace("_ThreadName_", Pattern.quote(threadName));
    }

    /**
     * Compares formatted string with pattern and throws AssertionFailedError if they do not match.
     */
    protected void checkResultMatches(final String originalThreadName, final String formattedThreadName) {
        final String log4jFormattedString = getLog4jFormattedString(originalThreadName);
        final String julFormattedString = getJULFormattedString(originalThreadName);
        final String pattern = getPattern(formattedThreadName);
        assertTrue("Obtained string '" + log4jFormattedString + "' does not match regexp '" + pattern + "'",
            log4jFormattedString.matches(pattern));
        assertTrue("Obtained string '" + julFormattedString + "' does not match regexp '" + pattern + "'",
            julFormattedString.matches(pattern));
    }

    /**
     * Compares formatted string with pattern and throws AssertionFailedError if they match.
     */
    protected void checkResultDoesNotMatch(final String originalThreadName, final String formattedThreadName) {
        final String log4jFormattedString = getLog4jFormattedString(originalThreadName);
        final String julFormattedString = getJULFormattedString(originalThreadName);
        final String pattern = getPattern(formattedThreadName);
        assertFalse("Obtained string '" + log4jFormattedString + "' should not match regexp '" + pattern + "'",
            log4jFormattedString.matches(pattern));
        assertFalse("Obtained string '" + julFormattedString + "' should not match regexp '" + pattern + "'",
            julFormattedString.matches(pattern));
    }
}
