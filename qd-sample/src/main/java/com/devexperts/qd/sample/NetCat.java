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
package com.devexperts.qd.sample;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Simple "NetCat" for debugging -- listens on socket and dumps all incoming data.
 */
public class NetCat {
    public static void main(String[] args) throws IOException {
        ServerSocket ss = new ServerSocket(Integer.parseInt(args[0]));
        while (true) {
            CatterThread t = new CatterThread(ss.accept());
            t.start();
        }
    }

    private static class CatterThread extends Thread {
        private final Socket s;

        public CatterThread(Socket s) {
            this.s = s;
        }

        public void run() {
            byte[] buf = new byte[4096];
            try {
                InputStream in = s.getInputStream();
                while (true) {
                    int available = Math.min(buf.length,  Math.max(1, in.available()));
                    int read = in.read(buf, 0, available);
                    if (read < 0)
                        return; // EOF
                    System.out.write(buf, 0, read);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
}
