/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.http;

import com.devexperts.qd.qtp.MessageConnectorMBean;

/**
 * @dgen.annotate method {}
 */
public interface HttpConnectorMBean extends MessageConnectorMBean {
    public void setAddress(String address);

    /**
     * Number of times to use fetchDelay after sending piece of data
     */
    public int getFetchCount();

    public void setFetchCount(int fetchCount);

    /**
     * Frequency (in ms) of updating data when data was sent recently
     *
     * Returns "fetch" delay that is used between subsequent POST requests if any
     * data was sent upstream.
     */
    public long getFetchDelay();

    /**
     * Sets "fetch" delay. See {@link #getFetchDelay()}.
     *
     * @param fetchDelay see {@link #getFetchDelay()}
     */
    public void setFetchDelay(long fetchDelay);

    /**
     * Frequency (in ms) of checking if new data is available.
     *
     * Returns "update" delay that is used between subsequent POST requests by default
     */
    public long getUpdateDelay();

    /**
     * Sets "update" delay. See {@link #getUpdateDelay()}.
     *
     * @param updateDelay see {@link #getUpdateDelay()}
     */
    public void setUpdateDelay(long updateDelay);

    /**
     * HTTP proxy host name
     */
    public String getProxyHost();

    public void setProxyHost(String proxyHost);

    /**
     * HTTP proxy port
     */
    public int getProxyPort();

    public void setProxyPort(int proxyPort);

    /**
     * True in file mode (GET request)
     */
    public boolean isFile();

    public void setFile(boolean file);
}
