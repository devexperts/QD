/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.benchmark.transfer.blob;

import com.devexperts.io.IOUtil;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketBlobServer {

    private ServerSocket serverSocket;
    private volatile boolean running;

    public int init(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        new Thread(() -> {
            try {
                Socket socket;
                while (running && (socket = serverSocket.accept()) != null) {
                    new Thread(createWorker(new DummyBlobService(), socket)).start();
                }
            } catch (Exception ignored) {
            }
        }).start();
        return serverSocket.getLocalPort();
    }

    public void disconnect() throws IOException {
        running = false;
        serverSocket.close();
    }

    private static Runnable createWorker(DummyBlobService blobService, Socket socket) {
        return () -> {
            try {
                DataInputStream in = new DataInputStream(socket.getInputStream());
                DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                int blobSize;
                while ((blobSize = IOUtil.readCompactInt(in)) != -1) {
                    IOUtil.writeByteArray(out, blobService.getBlob(blobSize));
                }
            } catch (IOException ignored) {
            }
        };
    }
}
