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

import com.devexperts.logging.Logging;
import com.devexperts.test.isolated.Isolated;
import com.devexperts.test.isolated.IsolatedRunner;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@RunWith(IsolatedRunner.class)
@Isolated({"com.devexperts.logging", "org.apache.logging", "org.apache.log4j"})
public class Log4j2DefaultLoggingTest {

    @Test
    public void testDevexpertsLoggingDefaultLevelIsDebug() {
        System.setProperty(Logging.LOG_CLASS_NAME, "com.devexperts.logging.Log4j2Logging");
        Logging log = Logging.getLogging(Log4j2DefaultLoggingTest.class);
        assertTrue(log.debugEnabled());
    }
}
