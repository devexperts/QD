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
package com.devexperts.qd.monitoring;

import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Properties;

import static org.junit.Assert.assertNotNull;

public class JmxRmiTest {
    @Test
    public void testRmiRestart() throws IOException, InterruptedException {
        Properties props = new Properties();
        int port = 11987;
        props.put("jmx.rmi.port", String.valueOf(port));
        JmxConnector jmxConnector = JmxRmi.init(props);
        assertNotNull(jmxConnector);
        jmxConnector.stop();
        waitForSocket(port);
        // no exception is thrown: java.rmi.server.ExportException: internal error: ObjID already in use
        jmxConnector = JmxRmi.init(props);
        assertNotNull(jmxConnector);
        jmxConnector.stop();
    }

    private void waitForSocket(int port) throws InterruptedException {
        // probe for socket
        long deadline = System.currentTimeMillis() + 10_000L;
        while (System.currentTimeMillis() < deadline) {
            try (ServerSocket socket = new ServerSocket(port)) {
                break;
            } catch (IOException e) {
                // swallow
            }
            Thread.sleep(10);
        }
    }
}
