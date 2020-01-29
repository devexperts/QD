/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2020 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.logging.test;

import com.devexperts.logging.LogFormatter;

/**
 * This test requires to be run in "clean" environment, where {@link LogFormatter} class is not loaded yet.
 */
public class UnconfiguredLogFormatterTest extends LogFormatterTestBase {
    protected void setUp() throws Exception {
        // Do not provide formatter configuration file.
        final String property = System.getProperties().getProperty(LogFormatter.CONFIG_FILE_PROPERTY);
        if (property != null)
            throw new Exception("Property '" + LogFormatter.CONFIG_FILE_PROPERTY + "' should not be defined for this test."
                + " You should run it in separate (clean) VM.");

        formatter = new LogFormatter();
    }

    public void testDefaultFormatting() {
        checkResultMatches("ThreadA", "ThreadA");
        checkResultDoesNotMatch("ExecutionThread", "ET");
        checkResultDoesNotMatch("Thread123Number", "T123N");
    }
}
