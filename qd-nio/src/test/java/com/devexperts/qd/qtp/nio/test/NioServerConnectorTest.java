/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.nio.test;

import com.devexperts.qd.qtp.nio.NioServerConnector;
import junit.framework.TestCase;

public class NioServerConnectorTest extends TestCase {
    public void testStopAndWait() throws InterruptedException {
        NioServerConnector connector = new NioServerConnector(new TestApplicationConnectionFactory(), 0);
        connector.start();
        connector.stopAndWait();
    }

}
