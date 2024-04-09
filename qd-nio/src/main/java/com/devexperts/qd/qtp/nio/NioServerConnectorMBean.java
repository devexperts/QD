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
package com.devexperts.qd.qtp.nio;

import com.devexperts.qd.qtp.ServerConnectorMBean;

/**
 * Management interface for {@link NioServerConnector}.
 *
 * @dgen.annotate method {}
 */
public interface NioServerConnectorMBean extends ServerConnectorMBean {
    /**
     * SO_TIMEOUT option value
     */
    public int getSocketTimeout();

    public void setSocketTimeout(int socketTimeout);

    /**
     * Number of reader threads in the pool
     */
    public int getReaderThreads();

    public void setReaderThreads(int readerThreads);

    /**
     * Reader thread pool counters
     */
    public NioPoolCounters getReaderPoolCounters();

    /**
     * Number of writer threads in the pool
     */
    public int getWriterThreads();

    public void setWriterThreads(int writerThreads);

    /**
     * Writer thread pool counters
     */
    public NioPoolCounters getWriterPoolCounters();

    /**
     * Resets reported counters
     */
    public void resetCounters();
}
