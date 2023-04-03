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
package com.devexperts.qd.tools.test;

import com.devexperts.qd.tools.Tools;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.live.InstrumentProfileCollector;
import com.dxfeed.ipf.live.InstrumentProfileConnection;
import com.dxfeed.ipf.live.InstrumentProfileUpdateListener;
import com.dxfeed.ipf.services.InstrumentProfileServer;
import org.junit.After;
import org.junit.Test;

import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class InstrumentsServerTest {
    private final int randomPortOffset = 10000 + new Random().nextInt(10000); // port randomization

    private String updateFail;
    private final Semaphore update = new Semaphore(0);

    private InstrumentProfileServer sourceServer;
    private Thread toolThread;
    private InstrumentProfileConnection destConnection;
    private volatile boolean toolOk = true;

    @Test
    public void testLiveServer() throws InterruptedException {
        // start source server
        InstrumentProfileCollector source = new InstrumentProfileCollector();
        sourceServer = InstrumentProfileServer.createServer(":" + (randomPortOffset + 1), source);
        sourceServer.start();

        // start tool
        toolThread = new Thread() {
            @Override
            public void run() {
                toolOk = Tools.invoke("instruments",
                    "-r", "localhost:" + (randomPortOffset + 1) + "[update=500]",
                    "-w", ":" + (randomPortOffset + 2));
            }
        };
        toolThread.start();

        // start dest connection
        InstrumentProfileCollector dest = new InstrumentProfileCollector();
        destConnection = InstrumentProfileConnection.createConnection("localhost:" + (randomPortOffset + 2), dest);
        destConnection.setUpdatePeriod(10);
        destConnection.start();

        // attach listener
        dest.addUpdateListener(new InstrumentProfileUpdateListener() {
            @Override
            public void instrumentProfilesUpdated(Iterator<InstrumentProfile> instruments) {
                if (!instruments.hasNext()) {
                    update("no next");
                    return;
                }
                InstrumentProfile next = instruments.next();
                if (!"STOCK".equals(next.getType())) {
                    update("wrong stock");
                    return;
                }
                if (!"TEST".equals(next.getSymbol())) {
                    update("wrong symbol");
                    return;
                }
                if (!"X".equals(next.getField("CUSTOM"))) {
                    update("wrong custom field");
                    return;
                }
                if (instruments.hasNext()) {
                    update("more instruments");
                }
                update("");
            }
        });

        // now feed an instrument into chain
        InstrumentProfile ip = new InstrumentProfile();
        ip.setType("STOCK");
        ip.setSymbol("TEST");
        ip.setField("CUSTOM", "X");
        source.updateInstrumentProfile(ip);

        // and wait for it to appear on the other end
        assertTrue(update.tryAcquire(3, TimeUnit.SECONDS));
        if (!updateFail.isEmpty())
            fail(updateFail);
    }

    @After
    public void tearDown() throws Exception {
        destConnection.close();
        toolThread.interrupt();
        toolThread.join();
        assertTrue(toolOk);
        sourceServer.close();
    }

    private void update(String fail) {
        updateFail = fail;
        update.release();
    }
}
