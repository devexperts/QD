/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.file.test;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.qtp.FileConstants;
import com.devexperts.qd.qtp.HeartbeatPayload;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.file.FileFormat;
import com.devexperts.qd.qtp.file.FileWriterImpl;
import com.devexperts.qd.qtp.file.FileWriterParams;
import com.devexperts.qd.qtp.file.TimestampsType;
import com.devexperts.qd.test.TestDataProvider;
import com.devexperts.qd.test.TestDataScheme;
import com.devexperts.util.TimePeriod;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class FileWriterTest {
    private static final String NAME_PREFIX = "FileWriterTest-tape-";
    private static final String NAME_SUFFIX = ".qds.data";
    private static final String TIME_SUFFIX = ".qds.time";
    private static final String TEMP_DIR_NAME = "tmp";

    private static final long SEED = 20131112;
    private static final int RECORD_CNT = 10;
    private static final int A_LOT_OF_FILES = 10;

    private final DataScheme scheme = new TestDataScheme(20131112);

    private FileWriterImpl fileWriter;

    private final boolean useTmpDir;

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Parameterized.Parameters(name = "Use tmpDir = {0}")
    public static Boolean[] params() {
        return new Boolean[] { false, true };
    }

    public FileWriterTest(boolean useTmpDir) {
        this.useTmpDir = useTmpDir;
    }

    @After
    public void tearDown() throws Exception {
        if (fileWriter != null) {
            fileWriter.close();
        }
    }

    private File[] getDataFiles(File folder, String suffix) {
        return folder.listFiles((dir, name) -> name.startsWith(NAME_PREFIX) && name.endsWith(suffix));
    }

    @Test
    public void testSingleFile() throws IOException {
        File dir = tempFolder.getRoot();
        FileWriterParams.Default params = new FileWriterParams.Default();
        params.setFormat(FileFormat.TEXT);
        params.setTime(TimestampsType.TEXT);
        if (useTmpDir) {
            params.setTmpDir(dir + "/" + TEMP_DIR_NAME);
        }
        String nameSuffix = "_qds";
        String timeSuffix = ".time";
        fileWriter = new FileWriterImpl(dir + "/" + NAME_PREFIX + nameSuffix, scheme, params).open();
        fileWriter.addSendMessageType(MessageType.STREAM_DATA);
        TestDataProvider provider = new TestDataProvider(scheme, SEED, RECORD_CNT, false);
        fileWriter.visitData(provider, MessageType.STREAM_DATA);
        fileWriter.close();

        checkTestResult(dir, nameSuffix, 1);
        checkTestResult(dir, timeSuffix, 1);
    }

    @Test
    public void testWriteALotOfFiles() throws IOException {
        File dir = tempFolder.getRoot();
        FileWriterParams.Default params = new FileWriterParams.Default();
        params.setFormat(FileFormat.TEXT);
        params.setSplit(TimePeriod.valueOf("1s"));
        params.setTime(TimestampsType.TEXT);
        if (useTmpDir) {
            params.setTmpDir(dir + "/" + TEMP_DIR_NAME);
        }
        fileWriter = new FileWriterImpl(dir + "/" + NAME_PREFIX + "~" + NAME_SUFFIX, scheme, params).open();
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
        checkTestResult(dir, NAME_SUFFIX, A_LOT_OF_FILES);
        checkTestResult(dir, TIME_SUFFIX, A_LOT_OF_FILES);
    }

    // [QD-771] Tools: FileWriter shall release completed files as time passes.
    @Test
    public void testFilesAreReleasedAsTimePasses()
        throws InterruptedException, NoSuchFieldException, IllegalAccessException
    {
        // Create split
        TimePeriod split = TimePeriod.valueOf("1s");
        // Create file writer
        FileWriterParams.Default params = new FileWriterParams.Default();
        params.setFormat(FileFormat.TEXT);
        params.setSplit(split);
        if (useTmpDir) {
            params.setTmpDir(tempFolder.getRoot() + "/" + TEMP_DIR_NAME);
        }
        fileWriter = new FileWriterImpl(
            tempFolder.getRoot() + "/" + NAME_PREFIX + "~" + NAME_SUFFIX, scheme, params).open();
        fileWriter.addSendMessageType(MessageType.STREAM_DATA);

        // Visit some data and write records to ".data" file.
        TestDataProvider provider = new TestDataProvider(scheme, SEED, RECORD_CNT, false);
        HeartbeatPayload heartbeatPayload = new HeartbeatPayload();
        heartbeatPayload.setTimeMillis(System.currentTimeMillis());
        fileWriter.visitHeartbeat(heartbeatPayload);
        fileWriter.visitData(provider, MessageType.STREAM_DATA);
        //easiest way to check that file was closed is to rely on implementation details and check that
        //private field 'dataOut' is set to null (according to current logic)
        Field dataOut = fileWriter.getClass().getDeclaredField("dataOut");
        dataOut.setAccessible(true);
        // await that file was closed by timeout. It's hard to predict actual wait time due to
        // FileWriterImpl.FlushThread logic + and environment load
        long waitTime = split.getTime() * FileConstants.MAX_OPEN_FACTOR + 3 * FileConstants.MAX_BUFFER_TIME + 10000;
        long failureTime = System.currentTimeMillis() + waitTime;
        while (System.currentTimeMillis() < failureTime && dataOut.get(fileWriter) != null) {
            Thread.sleep(100);
        }
        if (dataOut.get(fileWriter) != null)
            fail("File with split period " + split + " wasn't closed in " + waitTime / 1000 + "s");
    }

    @Test
    public void testStorageLimit() throws InterruptedException, IOException {
        File dir = tempFolder.getRoot();
        FileWriterParams.Default params = new FileWriterParams.Default();
        params.setFormat(FileFormat.TEXT);
        params.setSplit(TimePeriod.valueOf("1s"));
        params.setTime(TimestampsType.TEXT);
        if (useTmpDir) {
            params.setTmpDir(dir + "/" + TEMP_DIR_NAME);
        }
        params.setStorageTime(TimePeriod.valueOf("1s"));
        fileWriter = new FileWriterImpl(dir + "/" + NAME_PREFIX + "~" + NAME_SUFFIX, scheme, params).open();
        fileWriter.addSendMessageType(MessageType.STREAM_DATA);
        TestDataProvider provider = new TestDataProvider(scheme, SEED, RECORD_CNT, false);
        for (int i = 0; i < 5; i++) {
            if (i > 0) {
                Thread.sleep(1000);
            }
            fileWriter.visitData(provider, MessageType.STREAM_DATA);
        }
        fileWriter.close();

        // storageTime limit is "soft" and checked against current time during operation.
        // In case of unhappy delay some files on the edge may be considered as too old and deleted.
        checkTestResult(dir, NAME_SUFFIX, 1, 2);
        checkTestResult(dir, TIME_SUFFIX, 1, 2);
    }

    private void checkTestResult(File dir, String suffix, int... expectedFiles) throws IOException {
        File[] files = getDataFiles(dir, suffix);
        assertTrue(Arrays.stream(expectedFiles).anyMatch(value -> value == files.length));
        for (File file: files) {
            assertTrue("Empty file: " + file.getAbsolutePath(), Files.size(file.toPath()) > 0);
        }
    }
}
