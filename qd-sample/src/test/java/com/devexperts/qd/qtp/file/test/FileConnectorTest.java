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
package com.devexperts.qd.qtp.file.test;

import com.devexperts.qd.qtp.file.FileConnector;
import com.devexperts.util.TimeFormat;
import junit.framework.TestCase;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class FileConnectorTest extends TestCase {
    private static final String TIME_1 = "20140601-120000-0400";
    private static final String MIDDLE_TIME = "20140601-123000-0400";
    private static final String TIME_2 = "20140601-130000-0400";

    private static final String FILE_PREFIX = "FileConnectorTest-tape-";
    private static final String FILE_SUFFIX = ".qds.tmp";

    private static final File FILE_1 = new File(FILE_PREFIX + TIME_1 + FILE_SUFFIX);
    private static final File FILE_2 = new File(FILE_PREFIX + TIME_2 + FILE_SUFFIX);

    private FileConnector connector;

    @Override
    protected void tearDown() throws Exception {
        if (connector != null)
            connector.stop();
        FILE_1.delete();
        FILE_2.delete();
    }

    public void testFileReadFromTheMiddle() throws IOException, InterruptedException {
        // Create data files.
        FileConnectorTestUtils.writeTextFile(FILE_1,
            "==STREAM_DATA",
            "=Quote\tEventSymbol\tBidPrice\tAskPrice",
            "Quote\tIBM\t100\t101",
            "=Corrupted",               // <-- Corrupted records description (corrupted record #2)
            "Quote\tTEST\t100\t101");
        FileConnectorTestUtils.writeTextFile(FILE_2,
            "==STREAM_DATA",
            "=Quote\tEventSymbol\tBidPrice\tAskPrice",
            "Quote\tMSFT\t50\t51");
        BlockingQueue<String> symbols = new ArrayBlockingQueue<>(10);
        connector = FileConnectorTestUtils.initFileConnector(null, symbols, FILE_PREFIX + "~" + FILE_SUFFIX, TimeFormat.GMT.parse(MIDDLE_TIME));
        // Wait and check for all received symbols.
        assertEquals("IBM", symbols.poll(1, TimeUnit.SECONDS));
        assertEquals("MSFT", symbols.poll(1, TimeUnit.SECONDS));
        // Make sure it does not cycle and there are no more symbols.
        assertEquals(null, symbols.poll(1, TimeUnit.SECONDS));
    }

    public void testFileReadFromTheLast() throws IOException, InterruptedException {
        // Create data files.
        FileConnectorTestUtils.writeTextFile(FILE_1,
            "==STREAM_DATA",
            "=Quote\tEventSymbol\tBidPrice\tAskPrice",
            "Quote\tIBM\t100\t101",
            "=Corrupted",               // <-- Corrupted records description (corrupted record #2)
            "Quote\tTEST\t100\t101");
        FileConnectorTestUtils.writeTextFile(FILE_2,
            "==STREAM_DATA",
            "=Quote\tEventSymbol\tBidPrice\tAskPrice",
            "Quote\tMSFT\t50\t51");
        BlockingQueue<String> symbols = new ArrayBlockingQueue<>(10);
        connector = FileConnectorTestUtils.initFileConnector(null, symbols, FILE_PREFIX + "~" + FILE_SUFFIX, TimeFormat.GMT.parse(TIME_2));
        // Wait and check for all received symbols.
        assertEquals("MSFT", symbols.poll(1, TimeUnit.SECONDS));
        // Make sure it does not cycle and there are no more symbols.
        assertEquals(null, symbols.poll(1, TimeUnit.SECONDS));
    }
}
