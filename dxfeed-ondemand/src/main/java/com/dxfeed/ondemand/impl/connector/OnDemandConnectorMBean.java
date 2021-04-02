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
package com.dxfeed.ondemand.impl.connector;

import com.devexperts.qd.qtp.MessageConnectorMBean;
import com.devexperts.util.TimePeriod;

import java.util.Date;

/**
 * @dgen.annotate method {}
 */
public interface OnDemandConnectorMBean extends MessageConnectorMBean {
    /**
     * Tick period
     */
    public TimePeriod getTickPeriod();

    public void setTickPeriod(TimePeriod aggregationPeriod);

    /**
     * Cache limit size in bytes
     */
    public long getCacheLimit();

    public void setCacheLimit(long amount);

    /**
     * File cache limit size in bytes
     */
    public long getFileCacheLimit();

    public void setFileCacheLimit(long amount);

    /**
     * Cache file directory
     */
    public String getFileCachePath();

    public void setFileCachePath(String cacheFileDir);

    /**
     * Replay time
     */
    public Date getTime();

    public void setTime(Date time);

    /**
     * Replay speed
     */
    public double getSpeed();

    public void setSpeed(double speed);
}
