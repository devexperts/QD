/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.file.test;

import java.io.File;
import java.io.IOException;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.qtp.file.*;
import com.devexperts.qd.test.TestDataProvider;
import com.devexperts.qd.test.TestDataScheme;
import com.devexperts.util.TimePeriod;
import org.junit.*;

import static org.junit.Assert.assertEquals;

public class FileWriterTest {
    private static final String NAME_PREFIX = "FileWriterTest-tape-";
    private static final String NAME_SUFFIX = ".qds.tmp";

    private static final long SEED = 20131112;
    private static final int RECORD_CNT = 10;
    private static final int A_LOT_OF_FILES = 10;

    private final DataScheme scheme = new TestDataScheme(20131112);

    private FileWriterImpl fileWriter;

    @After
    public void tearDown() throws Exception {
        if (fileWriter != null) {
            fileWriter.close();
        }

        File[] files = getDataFiles();
        if (files != null)
            for (File file : files)
                //noinspection ResultOfMethodCallIgnored
                file.delete();
    }

    private File[] getDataFiles() {
        return new File(".").listFiles((dir, name) -> name.startsWith(NAME_PREFIX) && name.endsWith(NAME_SUFFIX));
    }

    @Test
    public void testWriteALotOfFiles() throws IOException {
        FileWriterParams.Default params = new FileWriterParams.Default();
        params.setFormat(FileFormat.TEXT);
        params.setSplit(TimePeriod.valueOf("1s"));
        fileWriter = new FileWriterImpl(NAME_PREFIX + "~" + NAME_SUFFIX, scheme, params).open();
        fileWriter.addSendMessageType(MessageType.STREAM_DATA);

        TestDataProvider provider = new TestDataProvider(scheme, SEED, RECORD_CNT, false);
        HeartbeatPayload heartbeatPayload = new HeartbeatPayload();
        // Try to create A_LOT_OF_FILES with 10 blocks each (first and last ones are shorter to avoid overflows)
        long time = System.currentTimeMillis() / params.getSplit().getTime() * params.getSplit().getTime();
        for (int i = 1; i < A_LOT_OF_FILES * 10 - 2; i++) {
            heartbeatPayload.setTimeMillis(time + i * params.getSplit().getTime() / 10);
            fileWriter.visitHeartbeat(heartbeatPayload);
            fileWriter.visitData(provider, MessageType.STREAM_DATA);
        }
        fileWriter.close();

        // Test that we have created A_LOT_OF_FILES files.
        assertEquals(A_LOT_OF_FILES, getDataFiles().length);
    }

    // [QD-771] Tools: FileWriter shall release completed files as time passes.
    @Test
    public void testFilesAreReleasedAsTimePasses() throws IOException, InterruptedException {
        // Create split
        TimePeriod split = TimePeriod.valueOf("1s");
        // Create file writer
        FileWriterParams.Default params = new FileWriterParams.Default();
        params.setFormat(FileFormat.TEXT);
        params.setSplit(split);
        fileWriter = new FileWriterImpl(NAME_PREFIX + "~" + NAME_SUFFIX, scheme, params).open();
        fileWriter.addSendMessageType(MessageType.STREAM_DATA);

        // Visit some data and write records to ".data" file.
        TestDataProvider provider = new TestDataProvider(scheme, SEED, RECORD_CNT, false);
        HeartbeatPayload heartbeatPayload = new HeartbeatPayload();
        heartbeatPayload.setTimeMillis(System.currentTimeMillis());
        fileWriter.visitHeartbeat(heartbeatPayload);
        fileWriter.visitData(provider, MessageType.STREAM_DATA);
        // Close first file by timeout.
        Thread.sleep(split.getTime() * FileConstants.MAX_OPEN_FACTOR + FileConstants.MAX_BUFFER_TIME);
        // Write data, should be stored to the second file.
        fileWriter.visitData(provider, MessageType.STREAM_DATA);
        // Close for flushing.
        fileWriter.close();

        // Test that we created 2 files.
        assertEquals(2, getDataFiles().length);
    }
}
