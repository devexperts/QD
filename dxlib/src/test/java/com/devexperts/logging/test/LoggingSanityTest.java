/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.logging.test;

import com.devexperts.logging.Logging;
import junit.framework.TestCase;

/**
 * Tries to log using standard approach.
 */
public class LoggingSanityTest extends TestCase {
    /**
     * This testcase should not throw any exceptions or errors.
     */
    public void testLogging() {
        Logging log = Logging.getLogging(LoggingSanityTest.class);
        log.configureDebugEnabled(true);
        log.debug("Test message");
    }
}
