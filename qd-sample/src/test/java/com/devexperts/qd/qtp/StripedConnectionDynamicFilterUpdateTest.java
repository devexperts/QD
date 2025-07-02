/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp;

import com.devexperts.qd.qtp.socket.ServerSocketTestHelper;
import com.devexperts.rmi.test.NTU;
import com.dxfeed.api.DXEndpoint;
import com.dxfeed.api.impl.DXEndpointImpl;
import com.dxfeed.promise.Promise;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class StripedConnectionDynamicFilterUpdateTest {

    private static final long WAIT_TIMEOUT = TimeUnit.SECONDS.toMillis(10);

    private String testId;
    private DXEndpoint server;
    private DXEndpoint client;
    private File ipfFile;

    @Before
    public void setUp() throws Exception {
        testId = UUID.randomUUID().toString();
    }

    @After
    public void tearDown() throws Exception {
        if (client != null)
            client.close();
        if (server != null)
            server.close();
        if (ipfFile != null)
            ipfFile.delete();
    }

    // Test is disabled because it would have to spend time checking that there are no extra reconnects
    @Ignore
    @Test
    public void testStripingWithDynamicFilterUpdate() throws Exception {
        String name = this.testId + "-pub";
        Promise<Integer> portPromise = ServerSocketTestHelper.createPortPromise(name);
        server = DXEndpoint.newBuilder()
            .withRole(DXEndpoint.Role.PUBLISHER)
            .build();

        server.connect(":0[name=" + name + ",bindAddr=127.0.0.1]");
        int port = portPromise.await(WAIT_TIMEOUT, TimeUnit.MILLISECONDS);

        ipfFile = File.createTempFile("dynamic", ".ipf");
        String ipfName =  ipfFile.getCanonicalPath().replaceAll("\\\\", "/");
        writeIpfFile(ipfFile, "IBM", "MSFT");

        client = DXEndpoint.newBuilder()
            .withName("client")
            .build();
        client.connect("ipf[" + ipfName + ",update=1s]@127.0.0.1:" + port + "[stripe=byhash4]");

        // Wait until 4 connections are established and proper adapters and distributors are created
        assertTrue(NTU.waitCondition(WAIT_TIMEOUT, 10,
            () -> client.getState() == DXEndpoint.State.CONNECTED));

        // Force dynamic filter update
        writeIpfFile(ipfFile, "GOOG");

        // Chained reconnects would happen when the bug is present
        long stopWaiting = System.currentTimeMillis() + WAIT_TIMEOUT;
        long closed = 0;
        while (System.currentTimeMillis() < stopWaiting) {
            Thread.sleep(100);
            closed = ((DXEndpointImpl)client).getQDEndpoint().getConnectors().get(0).getClosedConnectionCount();
            // Fail fast if test starts to spam connections
            assertTrue("Too many closed connections: " + closed, closed <= 4);
        }
        // 4 connections maximum for "byhash4" stripe
        assertEquals(4, closed);
    }

    private static void writeIpfFile(File file, String... symbols) throws IOException {
        List<String> ipf = new ArrayList<>();
        ipf.add("#STOCK::=TYPE,SYMBOL");
        for (String s : symbols) {
            ipf.add("STOCK," + s);
        }
        ipf.add("##COMPLETE");

        Files.write(file.toPath(), ipf);
    }
}
