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
package com.devexperts.qd.test;

import java.io.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.logging.Logging;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.qtp.nio.NioServerConnector;
import com.devexperts.qd.qtp.socket.ServerSocketConnector;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.test.ThreadCleanCheck;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

public class SocketRebindTest {
    private static final String LOG_FILE = "SocketRebindTest.log";

    private final int PORT = 20_000 + ThreadLocalRandom.current().nextInt(10_000);

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
        Logging.configureLogFile(LOG_FILE);
    }

    @After
    public void tearDown() throws Exception {
        Logging.configureLogFile(System.getProperty("log.file"));
        assertTrue(new File(LOG_FILE).delete());
        ThreadCleanCheck.after();
    }

    @Test
    public void testRebind() throws IOException, InterruptedException {
        implTestRebind(false);
    }

    @Test
    public void testRebindNio() throws IOException, InterruptedException {
        assumeFalse("Known issue: QD-1137",
            System.getProperty("os.name").toLowerCase().startsWith("mac") &&
            System.getProperty("java.version").startsWith("1.8"));
        implTestRebind(true);
    }

    private static class NamedFakeFactory implements MessageAdapter.Factory {
        final String name;

        public NamedFakeFactory(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        public MessageAdapter createAdapter(QDStats stats) {
            return null;
        }
    }

    // note: delays in this test are subject to be tuned
    private void implTestRebind(boolean nio) throws IOException, InterruptedException {
        ApplicationConnectionFactory aFactory = MessageConnectors.applicationConnectionFactory(new NamedFakeFactory("Foo"));
        ApplicationConnectionFactory bFactory = MessageConnectors.applicationConnectionFactory(new NamedFakeFactory("Bar"));

        ServerSocketConnector a = new ServerSocketConnector(aFactory, PORT);
        a.start();
        Thread.sleep(100);

        MessageConnector b = nio ? new NioServerConnector(bFactory, PORT) : new ServerSocketConnector(bFactory, PORT);
        b.setReconnectDelay(100);
        b.start();

        Thread.sleep(500);
        a.stop();

        Thread.sleep(300);
        b.stop();
        Thread.sleep(100);
        assertFalse(a.isActive());
        assertFalse(b.isActive());

        final String TIME_PATTERN = " \\d{6}? \\d{6}?\\.\\d{3}? ";
        final String THREAD_START = "\\[";
        final String THREAD_END = "\\] ";
        String connectorName = nio ? "NioServer" : "ServerSocket";
        String acceptorLoggingClass = nio ? "NioAcceptor" : "ServerSocketConnector";

        String[] patterns = {
            "I" + TIME_PATTERN + THREAD_START + "ServerSocket-" + aFactory + "-:" + PORT + "-Acceptor" + THREAD_END + "ServerSocketConnector - Trying to listen at \\*:" + PORT,
            "I" + TIME_PATTERN + THREAD_START + "ServerSocket-" + aFactory + "-:" + PORT + "-Acceptor" + THREAD_END + "ServerSocketConnector - Listening at \\*:" + PORT,
            "I" + TIME_PATTERN + THREAD_START + connectorName + "-" + bFactory + "-:" + PORT + "-Acceptor" + THREAD_END + acceptorLoggingClass + " - Trying to listen at \\*:" + PORT,
            "E" + TIME_PATTERN + THREAD_START + connectorName + "-" + bFactory + "-:" + PORT + "-Acceptor" + THREAD_END + acceptorLoggingClass + " - Failed to listen at \\*:" + PORT,
            "I" + TIME_PATTERN + THREAD_START + connectorName + "-" + bFactory + "-:" + PORT + "-Acceptor" + THREAD_END + acceptorLoggingClass + " - Trying to listen at \\*:" + PORT,
            "E" + TIME_PATTERN + THREAD_START + connectorName + "-" + bFactory + "-:" + PORT + "-Acceptor" + THREAD_END + acceptorLoggingClass + " - Failed to listen at \\*:" + PORT,
            "I" + TIME_PATTERN + THREAD_START + connectorName + "-" + bFactory + "-:" + PORT + "-Acceptor" + THREAD_END + acceptorLoggingClass + " - Trying to listen at \\*:" + PORT,
            "I" + TIME_PATTERN + THREAD_START + connectorName + "-" + bFactory + "-:" + PORT + "-Acceptor" + THREAD_END + acceptorLoggingClass + " - Listening at \\*:" + PORT,
            "I" + TIME_PATTERN + THREAD_START + "main" + THREAD_END + acceptorLoggingClass + " - Stopped listening at \\*:" + PORT,
        };
        checkLogFile(patterns);
    }

    private static void checkLogFile(String[] patterns) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(LOG_FILE));
        try {
            for (String patternStr : patterns) {
                Pattern pattern = Pattern.compile(patternStr);
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                    if (pattern.matcher(line).matches())
                        break;
                }
                if (line == null)
                    fail("Failed to find required subsequence of log lines in " + LOG_FILE  + "\n(last unmatched pattern: " + pattern + ")");
            }
        } finally {
            br.close();
        }
    }
}
