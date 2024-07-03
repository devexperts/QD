/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.api.test;

import com.devexperts.qd.QDFactory;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.samplecert.SampleCert;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.test.ThreadCleanCheck;
import com.dxfeed.api.DXEndpoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotNull;

public class SSLUserPasswordTest {
    private static final int PORT = (new Random().nextInt(100) + 100) * 100 + 81;

    private List<MessageConnector> serverConnectors;
    private DXEndpoint dx;

    private final BlockingQueue<String> receivedAuth = new ArrayBlockingQueue<>(1);

    @Before
    public void setUp() throws Exception {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() throws Exception {
        if (dx != null)
            dx.close();
        if (serverConnectors != null)
            MessageConnectors.stopMessageConnectors(serverConnectors);
        ThreadCleanCheck.after();
    }

    @Test
    public void testSSLUserPassword() throws InterruptedException {
        SampleCert.init();
        // start custom QD server with SSL and auth
        serverConnectors = MessageConnectors.createMessageConnectors(
            MessageConnectors.applicationConnectionFactory(new AuthAgentAdapterFactory()),
            "ssl[isServer=true," + SampleCert.KEY_STORE_CONFIG + "]+:" + PORT);
        MessageConnectors.startMessageConnectors(serverConnectors);

        // connect DXEndpoint with user/password
        dx = DXEndpoint.create()
            .user("demo")
            .password("demo")
            .connect("ssl[" + SampleCert.TRUST_STORE_CONFIG + "]+localhost:" + PORT);

        // wait for received auth
        assertNotNull(receivedAuth.poll(10, TimeUnit.SECONDS));
    }

    private class AuthAgentAdapter extends AgentAdapter {
        AuthAgentAdapter(QDStats stats) {
            super(null, QDFactory.getDefaultFactory().tickerBuilder().withScheme(QDFactory.getDefaultScheme()).build(),
                null, null, null, null, stats);
        }

        @Override
        public void processDescribeProtocol(ProtocolDescriptor desc, boolean logDescriptor) {
            super.processDescribeProtocol(desc, logDescriptor);
            String auth = desc.getProperty(ProtocolDescriptor.AUTHORIZATION_PROPERTY);
            if (auth != null)
                receivedAuth.add(auth);
        }
    }

    private class AuthAgentAdapterFactory implements MessageAdapter.Factory {
        @Override
        public MessageAdapter createAdapter(QDStats stats) {
            return new AuthAgentAdapter(stats);
        }
    }
}
