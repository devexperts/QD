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
package com.dxfeed.ipf.filter.test;

import com.devexperts.logging.Logging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

/**
 * Makes sure IPF file is not rewritten too otten.
 */
class IPFWriter {
    private static final Logging log = Logging.getLogging(IPFWriter.class);

    // minimal interval between file rewrites
    private static final long MIN_INTERVAL = 1000; // 1sec

    private final File file;
    private long lastUpdateTime;
    private long lastFileTime;

    IPFWriter(File file) {
        this.file = file;
    }

    void writeIPFFile(String... symbols) throws IOException {
        long now;
        while (true) {
            now = System.currentTimeMillis();
            long wait = lastUpdateTime + MIN_INTERVAL - now;
            if (wait <= 0)
                break;
            if (!sleep(wait))
                return;
        }
        lastUpdateTime = now;
        log.info("Writing symbols " + Arrays.asList(symbols) + " to " + file);
        while (true) {
            try (PrintWriter out = new PrintWriter(new FileWriter(file))) {
                out.println("#TEST::=TYPE,SYMBOL");
                for (String symbol : symbols)
                    out.println("TEST," + symbol);
                out.println("##COMPLETE");
            }
            long lastModified = file.lastModified();
            if (lastModified != lastFileTime) {
                lastFileTime = lastModified;
                break; // Ok! Updated file time
            }
            // otherwise retry writing until modification time updates
            if (!sleep(100))
                return;
        }
    }

    private boolean sleep(long wait) {
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }
}
