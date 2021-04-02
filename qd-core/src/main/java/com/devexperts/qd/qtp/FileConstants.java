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
package com.devexperts.qd.qtp;

import com.devexperts.io.ChunkPool;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;

/**
 * Common constants for working with QTP files.
 */
public class FileConstants {
    private FileConstants() {} // do not create

    public static final ChunkPool CHUNK_POOL = new ChunkPool("com.devexperts.qd.qtp.file",
        3, 512, 128, 65536, 1024);

    /**
     * Chunk size for files.
     * It is configured with "com.devexperts.qd.qtp.file.chunkSize" system property (65536 by default).
     */
    public static final int CHUNK_SIZE = CHUNK_POOL.getChunkSize();

    /**
     * Max time to keep buffered data in memory before physically flushing it to file.
     */
    public static final long MAX_BUFFER_TIME = TimePeriod.valueOf(
        SystemProperties.getProperty("com.devexperts.qd.qtp.file.maxBufferTime", "1s")).getTime();

    /**
     * Maximum open factor for tape files.
     * If file is opened during equal to or more than {@code splitTime * MAX_OPEN_FACTOR} then it will be closed.
     */
    public static final long MAX_OPEN_FACTOR =
        SystemProperties.getIntProperty("com.devexperts.qd.qtp.file.maxOpenFactor", 2);

}
