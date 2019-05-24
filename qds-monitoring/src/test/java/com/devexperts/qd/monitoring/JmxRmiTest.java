/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.monitoring;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

public class JmxRmiTest {
    @Test
    public void testRmiRestart() throws IOException {
        Properties props = new Properties();
        props.put("jmx.rmi.port", "11987");
        JmxConnector jmxConnector = JmxRmi.init(props);
        Assert.assertNotNull(jmxConnector);
        jmxConnector.stop();
        // no exception is thrown: java.rmi.server.ExportException: internal error: ObjID already in use
        jmxConnector = JmxRmi.init(props);
        Assert.assertNotNull(jmxConnector);
        jmxConnector.stop();
    }
}
