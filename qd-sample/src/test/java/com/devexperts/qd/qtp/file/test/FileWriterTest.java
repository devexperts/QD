/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.qtp.file.test;

import java.io.File;
import java.io.IOException;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.qtp.file.*;
import com.devexperts.qd.test.TestDataProvider;
import com.devexperts.qd.test.TestDataScheme;
import com.devexperts.timetest.TestTimeProvider;
import com.devexperts.util.TimePeriod;
import org.junit.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Ignore
public class FileWriterTest {
	private static final String NAME_PREFIX = "FileWriterTest-tape-";
	private static final String NAME_SUFFIX = ".qds.tmp";

	private static final long SEED = 20131112;
	private static final int RECORD_CNT = 1000;
	private static final int BLOCKS = 1000;
	private static final long TIME_STEP = 10;

	private final DataScheme scheme = new TestDataScheme(20131112);

	private FileWriterImpl fileWriter;

	@After
	public void tearDown() throws Exception {
		TestTimeProvider.reset();
		if (fileWriter != null) {
			fileWriter.close();
		}

		File[] files = getDataFiles();
		if (files != null)
			for (File file : files)
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
		long time = System.currentTimeMillis();
		HeartbeatPayload heartbeatPayload = new HeartbeatPayload();
		for (int i = 0; i < BLOCKS; i++) {
			heartbeatPayload.setTimeMillis(time + i * TIME_STEP);
			fileWriter.visitHeartbeat(heartbeatPayload);
			while (fileWriter.visitData(provider, MessageType.STREAM_DATA)) {
				// visit all data from provider
			}
		}
		fileWriter.close();
	}

	// [QD-771] Tools: FileWriter shall release completed files as time passes.
	@Test
	public void testFilesAreReleasedAsTimePasses() throws IOException, InterruptedException {
		TestTimeProvider.start();
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
		TestTimeProvider.waitUntilThreadsAreFrozen(1000);
		// Close first file by timeout.
		for (int i = 0; i <= Math.round(1d * split.getTime() * FileConstants.MAX_OPEN_FACTOR / FileConstants.MAX_BUFFER_TIME); i++) {
			TestTimeProvider.increaseTime(FileConstants.MAX_BUFFER_TIME);
			TestTimeProvider.waitUntilThreadsAreFrozen(1000);
		}
		// Write data, should be stored to the second file.
		while (fileWriter.visitData(provider, MessageType.STREAM_DATA)) {
			// Visit all data from provider.
		}
		// Close for flushing.
		fileWriter.close();
		// Test that we created 2 files.
		File[] dataFiles = getDataFiles();
		assertNotNull(dataFiles);
		assertEquals(2, dataFiles.length);
	}
}
