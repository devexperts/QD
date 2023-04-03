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
import org.junit.Before;
import org.junit.Test;

/**
 * This test requires to be run in "clean" environment, where {@link LogFormatter} class is not loaded yet.
 */
public class UnconfiguredLogFormatterTest extends LogFormatterTestBase {

    @Before
    public void setUp() throws Exception {
        System.clearProperty(LogFormatter.CONFIG_FILE_PROPERTY);
        formatter = new LogFormatter();
    }

    @Test
    public void testDefaultFormatting() {
        checkResultMatches("ThreadA", "ThreadA");
        checkResultDoesNotMatch("ExecutionThread", "ET");
        checkResultDoesNotMatch("Thread123Number", "T123N");
    }
}
