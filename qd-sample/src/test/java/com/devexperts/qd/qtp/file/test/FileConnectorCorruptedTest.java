/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.qtp.file.test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.*;

import com.devexperts.io.ByteArrayOutput;
import com.devexperts.io.StreamOutput;
import com.devexperts.qd.*;
import com.devexperts.qd.kit.*;
import com.devexperts.qd.ng.*;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.file.FileConnector;
import com.devexperts.qd.qtp.file.FileReaderParams;
import junit.framework.Assert;
import junit.framework.TestCase;

public class FileConnectorCorruptedTest extends TestCase {
    private static final String TIME_1 = "20140601-120000-0400";
    private static final String TIME_2 = "20140601-130000-0400";
    private static final String TIME_3 = "20140601-140000-0400";

    private static final String FILE_PREFIX = "FileConnectorCorruptedTest-tape-";
    private static final String FILE_SUFFIX = ".qds.tmp";

    private static final File FILE_1 = new File(FILE_PREFIX + TIME_1 + FILE_SUFFIX);
    private static final File FILE_2 = new File(FILE_PREFIX + TIME_2 + FILE_SUFFIX);
    private static final File FILE_3 = new File(FILE_PREFIX + TIME_3 + FILE_SUFFIX);

    private static final DataRecord RECORD = new DefaultRecord(0, "Quote", false,
        new DataIntField[] {
            new CompactIntField(0, "Quote.Bid.Price"),
            new CompactIntField(1, "Quote.Ask.Price"),
            new CompactIntField(2, "Quote.Bid.Size"),
            new CompactIntField(3, "Quote.Ask.Size"),
        }, new DataObjField[0]);

    private static final DataScheme SCHEME = new DefaultScheme(new PentaCodec(), RECORD);

    private FileConnector connector;

    @Override
    protected void tearDown() throws Exception {
        if (connector != null)
            connector.stop();
        FILE_1.delete();
        FILE_2.delete();
        FILE_3.delete();
    }

    public void testSkipCorruptedTextFileAndGoNext() throws IOException, InterruptedException {
        // Create data files.
        writeTextFile(FILE_1,
            "==STREAM_DATA",
            "=Quote\tEventSymbol\tBidPrice\tAskPrice",
            "Quote\tIBM\t100\t101",
            "=Corrupted",               // <-- Corrupted records description (corrupted record #2)
            "Quote\tTEST\t100\t101");
        writeTextFile(FILE_2,
            "==STREAM_DATA",
            "=Quote\tEventSymbol\tBidPrice\tAskPrice",
            "Quote\tMSFT\t50\t51");
        BlockingQueue<String> symbols = readSymbols(null);
        // Wait and check for all received symbols.
        assertEquals("IBM", symbols.poll(1, TimeUnit.SECONDS));
        assertEquals("MSFT", symbols.poll(1, TimeUnit.SECONDS));
        // Make sure it does not cycle and there are no more symbols.
        assertEquals(null, symbols.poll(1, TimeUnit.SECONDS));
    }

    public void testSkipCorruptedBinaryFileAndGoNext() throws IOException, InterruptedException {
        // Create data files.
        writeBinaryFile(FILE_1,
            "0x02 0x00 Quote 0x02 BidPrice 0x08 AskPrice 0x08", // describe Quote record
            "0x0f 0xfc IBM 0x00 0x17 0x86 0x59", // stream data from IBM with bid=100 ask=101
            "0x0f 0xfc TEST 0x00 0x17 0x86 0x59 0x00" // corrupted stream data for TEST with bid=100 ask=101 (extra 0x00 byte in message)
        );
        writeBinaryFile(FILE_2,
            "0x02 0x00 Quote 0x02 BidPrice 0x08 AskPrice 0x08", // describe Quote record
            "0x0f 0xfc MSFT 0x00 0x80 0x58 0x83 0x39" // stream data from MSFT with bid=50 ask=51
        );
        BlockingQueue<String> symbols = readSymbols(null);
        // Wait and check for all received symbols.
        assertEquals("IBM", symbols.poll(1, TimeUnit.SECONDS));
        assertEquals("MSFT", symbols.poll(1, TimeUnit.SECONDS));
        // Make sure it does not cycle and there are no more symbols.
        assertEquals(null, symbols.poll(1, TimeUnit.SECONDS));
    }

    public void testRescanFileListOnFailure() throws IOException, InterruptedException {
        // create files
        writeTextFile(FILE_1,
            "==STREAM_DATA",
            "=Quote\tEventSymbol\tBidPrice\tAskPrice",
            "Quote\tIBM\t100\t101");
        writeTextFile(FILE_2,
            "==STREAM_DATA",
            "=Quote\tEventSymbol\tBidPrice\tAskPrice",
            "Quote\tTEST\t50\t51");
        // Create queue.
        final boolean[] firstSymbol = {true};
        BlockingQueue<String> symbols = readSymbols(provider -> {
            // Delete 2nd file on first symbol reading.
            if (firstSymbol[0]) {
                Assert.assertTrue(FILE_2.delete());
                firstSymbol[0] = false;
            }
        });
        assertEquals("IBM", symbols.poll(1, TimeUnit.SECONDS));
        // Create 3rd data file.
        writeTextFile(FILE_3,
            "==STREAM_DATA",
            "=Quote\tEventSymbol\tBidPrice\tAskPrice",
            "Quote\tMSFT\t50\t51");
        // Read symbol from 3rd data file.
        assertEquals("MSFT", symbols.poll(1, TimeUnit.SECONDS));
        // Make sure it does not cycle and there are no more symbols.
        assertEquals(null, symbols.poll(1, TimeUnit.SECONDS));
    }

    private BlockingQueue<String> readSymbols(final RecordListener recordListener) {
        // create stream
        QDStream stream = QDFactory.getDefaultFactory().createStream(SCHEME);
        stream.setEnableWildcards(true);
        // create and subscribe agent
        QDAgent agent = stream.agentBuilder().build();
        RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
        sub.add(RECORD, SCHEME.getCodec().getWildcardCipher(), null);
        agent.setSubscription(sub);
        // set agent listener
        final ArrayBlockingQueue<String> receivedSymbols = new ArrayBlockingQueue<>(10);
        agent.setRecordListener(provider -> {
            // Call external listener
            if (recordListener != null)
                recordListener.recordsAvailable(provider);

            provider.retrieve(new AbstractRecordSink() {
                @Override
                public void append(RecordCursor cursor) {
                    assert cursor.getRecord() == RECORD;
                    receivedSymbols.offer(cursor.getDecodedSymbol());
                }
            });
        });
        // create and start file connector
        String address = FILE_PREFIX + "~" + FILE_SUFFIX ;
        connector = new FileConnector(
            MessageConnectors.applicationConnectionFactory(new DistributorAdapter.Factory(stream)), address);
        connector.setSpeed(FileReaderParams.MAX_SPEED);
        connector.start();
        return receivedSymbols;
    }

    private void writeTextFile(File file, String... lines) throws IOException {
        Files.write(file.toPath(), Arrays.asList(lines), StandardCharsets.UTF_8);
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
