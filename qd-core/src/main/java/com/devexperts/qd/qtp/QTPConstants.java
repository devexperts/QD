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
package com.devexperts.qd.qtp;

import com.devexperts.io.ChunkPool;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.TimePeriod;
import com.devexperts.util.TimeUtil;

import java.time.LocalTime;

/**
 * This class contains key buffer and connection constants that affect performance of QTP.
 */
public class QTPConstants {
    private QTPConstants() {} // utility, do not create

    /**
     * Default reconnection delay in QTP. Note, that this default affects all connector and it
     * can be set on per-connector basis (JMX or config line) with "reconnectDelay" property in connector.
     */
    public static final long RECONNECT_DELAY =
        TimePeriod.valueOf(SystemProperties.getProperty("com.devexperts.qd.qtp.reconnectDelay", "10s")).getTime();

    /**
     * Maximum number of bytes to aggregate before processing when reading data from socket. If fewer number of bytes
     * received and no more are available in socket then parsing starts immediately (no additional delay),
     * but when data is coming into a socket continuously, then it is read from socket in chunks
     * of a specific chunk size of a {@link ChunkPool#DEFAULT default} chunk pool
     * (it can be configured via JVM system property "com.devexperts.io.chunkSize" with a default value of 8192),
     * and parsing starts as soon as total number of bytes read exceeds this aggregation size.
     */
    public static final int READ_AGGREGATION_SIZE =
        SystemProperties.getIntProperty("com.devexperts.qd.qtp.readAggregationSize", 8000);

    /**
     * Defines default threshold for the size of the composed packet. When packet size exceeds this threshold, then
     * the packet is finished and becomes available for sending. Thus, actual packets can exceed this threshold up to
     * the size of one record, which can be quite big if symbols are long or record data contains large object values.
     */
    public static final int COMPOSER_THRESHOLD =
        SystemProperties.getIntProperty("com.devexperts.qd.qtp.composerThreshold", 8000);

    /**
     * Defines default size of the composer buffer that is kept in each composer instance. Its default value is 50%
     * larger than the threshold. If composed packet exceeds the size of the buffer, then larger buffer is allocated
     * and becomes garbage when the message is sent.
     */
    public static final int COMPOSER_BUFFER_SIZE =
        SystemProperties.getIntProperty("com.devexperts.qd.qtp.composerBufferSize", (int) (1.5 * COMPOSER_THRESHOLD));

    /**
     * Defines the hard upper limit for packet size in QTP transfers.
     * A packet of greater size will be considered corrupted by the QTP parser and rejected without further analysis.
     * The value shall be big enough to accept any valid QTP packet.
     */
    public static final int MAX_MESSAGE_SIZE =
        SystemProperties.getIntProperty("com.devexperts.qd.qtp.maxMessageSize", 100_000);

    /**
     * Defines the connection recovery time for all client socket connections.
     * This can be overridden in the connection string by means of 'restoreTime' property
     */
    public static final LocalTime CONNECTION_RESTORE_TIME =
        TimeUtil.parseLocalTime(System.getProperty("com.devexperts.qd.qtp.connectionRestoreTime"));

    /**
     * Defines the graceful delay for different socket activities
     */
    public static final long GRACEFUL_DELAY =
        TimePeriod.valueOf(SystemProperties.getProperty("com.devexperts.qd.qtp.gracefulDelay", "5m")).getTime();
}
