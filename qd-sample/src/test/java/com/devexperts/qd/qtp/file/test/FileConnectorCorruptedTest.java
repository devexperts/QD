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
package com.devexperts.qd.qtp.file.test;

import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.StreamOutput;
import com.devexperts.qd.qtp.file.FileConnector;
import com.devexperts.util.TimeFormat;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FileConnectorCorruptedTest {
    private static final String TIME_1 = "20140601-120000-0400";
    private static final String TIME_2 = "20140601-130000-0400";
    private static final String TIME_3 = "20140601-140000-0400";

    private static final String FILE_PREFIX = "FileConnectorCorruptedTest-tape-";
    private static final String FILE_SUFFIX = ".qds.tmp";

    private static final File FILE_1 = new File(FILE_PREFIX + TIME_1 + FILE_SUFFIX);
    private static final File FILE_2 = new File(FILE_PREFIX + TIME_2 + FILE_SUFFIX);
    private static final File FILE_3 = new File(FILE_PREFIX + TIME_3 + FILE_SUFFIX);

    private FileConnector connector;

    @Before
    public void setUp() {
        deleteFiles();
    }

    @After
    public void tearDown() {
        if (connector != null)
            connector.stop();
        deleteFiles();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void deleteFiles() {
        FILE_1.delete();
        FILE_2.delete();
        FILE_3.delete();
    }

    @Test
    public void testSkipCorruptedTextFileAndGoNext() throws IOException, InterruptedException {
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
        connector = FileConnectorTestUtils.initFileConnector(null, symbols,
            FILE_PREFIX + "~" + FILE_SUFFIX, TimeFormat.GMT.parse(TIME_1));
        // Wait and check for all received symbols.
        assertEquals("IBM", symbols.poll(1, TimeUnit.SECONDS));
        assertEquals("MSFT", symbols.poll(1, TimeUnit.SECONDS));
        // Make sure it does not cycle and there are no more symbols.
        assertNull("No data expected", symbols.poll(1, TimeUnit.SECONDS));
    }

    @Test
    public void testSkipCorruptedBinaryFileAndGoNext() throws IOException, InterruptedException {
        // Create data files.
        writeBinaryFile(FILE_1,
            // describe Quote record
            "0x02 0x00 Quote 0x02 BidPrice 0x08 AskPrice 0x08",
            // stream data from IBM with bid=100 ask=101
            "0x0f 0xfc IBM 0x00 0x17 0x86 0x59",
            // corrupted stream data for TEST with bid=100 ask=101 (extra 0x00 byte in message)
            "0x0f 0xfc TEST 0x00 0x17 0x86 0x59 0x00"
        );
        writeBinaryFile(FILE_2,
            // describe Quote record
            "0x02 0x00 Quote 0x02 BidPrice 0x08 AskPrice 0x08",
            // stream data from MSFT with bid=50 ask=51
            "0x0f 0xfc MSFT 0x00 0x80 0x58 0x83 0x39"
        );
        BlockingQueue<String> symbols = new ArrayBlockingQueue<>(10);
        connector = FileConnectorTestUtils.initFileConnector(null, symbols,
            FILE_PREFIX + "~" + FILE_SUFFIX, TimeFormat.GMT.parse(TIME_1));
        // Wait and check for all received symbols.
        assertEquals("IBM", symbols.poll(1, TimeUnit.SECONDS));
        assertEquals("MSFT", symbols.poll(1, TimeUnit.SECONDS));
        // Make sure it does not cycle and there are no more symbols.
        assertNull("No data expected", symbols.poll(1, TimeUnit.SECONDS));
    }

    @Test
    public void testRescanFileListOnFailure() throws IOException, InterruptedException {
        // create files
        FileConnectorTestUtils.writeTextFile(FILE_1,
            "==STREAM_DATA",
            "=Quote\tEventSymbol\tBidPrice\tAskPrice",
            "Quote\tIBM\t100\t101");
        FileConnectorTestUtils.writeTextFile(FILE_2,
            "==STREAM_DATA",
            "=Quote\tEventSymbol\tBidPrice\tAskPrice",
            "Quote\tTEST\t50\t51");
        // Create queue.
        final boolean[] firstSymbol = {true};
        BlockingQueue<String> symbols = new ArrayBlockingQueue<>(10);
        connector = FileConnectorTestUtils.initFileConnector(provider -> {
            if (firstSymbol[0]) {
                // Delete 2nd file on first symbol reading.
                assertTrue(FILE_2.delete());
                // ... and immediately create 3rd data file.
                try {
                    FileConnectorTestUtils.writeTextFile(FILE_3,
                        "==STREAM_DATA",
                        "=Quote EventSymbol BidPrice AskPrice",
                        "Quote MSFT 50 51");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                firstSymbol[0] = false;
            }
        }, symbols, FILE_PREFIX + "~" + FILE_SUFFIX, TimeFormat.GMT.parse(TIME_1));
        assertEquals("IBM", symbols.poll(10, TimeUnit.SECONDS));
        // Read symbol from 3rd data file.
        assertEquals("MSFT", symbols.poll(1, TimeUnit.SECONDS));
        // Make sure it does not cycle and there are no more symbols.
        assertNull("No data expected", symbols.poll(1, TimeUnit.SECONDS));
    }

    private void writeBinaryFile(File file, String... lines) throws IOException {
        try (StreamOutput out = new StreamOutput(new FileOutputStream(file))) {
            for (String line : lines) {
                // each line is a message
                ByteArrayOutput msg = new ByteArrayOutput();
                for (String s : line.split(" ")) {
                    if (s.startsWith("0x"))
                        msg.write(Integer.decode(s));
                    else
                        msg.writeUTFString(s);
                }
                out.writeCompactInt(msg.getPosition());
                out.write(msg.toByteArray());
            }
        }
    }
}
