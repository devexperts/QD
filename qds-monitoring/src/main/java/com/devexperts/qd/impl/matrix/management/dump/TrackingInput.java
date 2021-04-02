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
package com.devexperts.qd.impl.matrix.management.dump;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;

public class TrackingInput extends InputStream {
    private static final PrintStream PS = System.out;
    private final RandomAccessFile file;
    private final long length;
    private int percent;

    public TrackingInput(RandomAccessFile file) throws IOException {
        this.file = file;
        length = file.length();
        PS.printf("Reading %d bytes: ", length);
    }

    @Override
    public int read() throws IOException {
        int read = file.read();
        if (read >= 0)
            update();
        return read;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int read = file.read(b, off, len);
        if (read > 0)
            update();
        return read;
    }

    @Override
    public void close() throws IOException {
        file.close();
        PS.println(" done");
    }

    private void update() throws IOException {
        long p = file.getFilePointer() * 100 / length;
        while (percent < p) {
            PS.print(".");
            percent++;
            if (percent % 10 == 0) {
                PS.print(percent + "%");
            }
        }
    }
}
