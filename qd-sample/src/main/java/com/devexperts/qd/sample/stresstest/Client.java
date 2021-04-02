/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.sample.stresstest;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

class Client {

    private final static byte[] readBuf = new byte[10000];
    private final static byte[] writeBuf = new byte[400];
    private final static Random rnd = new Random();
    static {
        rnd.nextBytes(writeBuf);
    }

    private final TSTClients clients;
    private final Socket socket;
    private volatile boolean failed;
    private final AtomicLong reads = new AtomicLong();
    private final AtomicLong bytes = new AtomicLong();

    Client(TSTClients clients) throws IOException {
        this.clients = clients;
        socket = clients.socketFactory.createSocket(clients.host, clients.port);
        socket.getInputStream().read(readBuf, 0, 1); // Wait for first payload byte - to measure handshake performance
    }

    @SuppressWarnings({"InfiniteLoopStatement"})
    void start() throws IOException {
        new Thread() { // reader
            @Override
            public void run() {
                try {
                    InputStream inputStream = socket.getInputStream();
                    while (true) {
                        int result = inputStream.read(readBuf);
                        if (result == -1)
                            throw new IOException("end of stream was reached");
                        reads.incrementAndGet();
                        bytes.addAndGet(result);
                    }
                } catch (Throwable t) {
                    t.printStackTrace(System.out);
                    failed = true;
                }
            }
        }.start();
        new Thread() { // writer
            @Override
            public void run() {
                try {
                    long hp = clients.heartbeatPeriod - clients.heartbeatPeriodVariation;
                    int hpv = (int) (2 * clients.heartbeatPeriodVariation);
                    OutputStream outputStream = socket.getOutputStream();
                    while (true) {
                        outputStream.write(writeBuf);
                        Thread.sleep(hp + rnd.nextInt(hpv));
                    }
                } catch (Throwable t) {
                    t.printStackTrace(System.out);
                    failed = true;
                }
            }
        }.start();
    }

    long getReadsCount() {
        return reads.getAndSet(0);
    }

    long getBytesCount() {
        return bytes.getAndSet(0);
    }

    boolean isAlive() {
        return !failed;
    }
}
