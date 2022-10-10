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
import java.net.Socket;

public class SocketBlobClient {

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    public void init(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());
    }

    public void disconnect() throws IOException {
        socket.close();
    }

    public byte[] receiveBlob(int blobSize) throws IOException {
        IOUtil.writeCompactLong(out, blobSize);
        return IOUtil.readByteArray(in);
    }
}
