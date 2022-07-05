/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.file;

import com.devexperts.io.StreamCompression;
import com.devexperts.qd.qtp.MessageConnectorMBean;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.util.TimePeriod;

import java.util.Date;

/**
 * @dgen.annotate method {}
 */
public interface FileConnectorMBean extends MessageConnectorMBean {
    public void setAddress(String address);

    /**
     * File compression
     */
    public StreamCompression getCompression();

    void setCompression(StreamCompression compression);

    /**
     * File format
     */
    public FileFormat getFormat();

    public void setFormat(FileFormat type);

    /**
     * Start timestamp
     */
    public Date getStart();

    public void setStart(Date time);

    /**
     * Stop timestamp
     */
    public Date getStop();

    public void setStop(Date time);

    /**
     * Delaying time period
     */
    public TimePeriod getDelayed();

    public void setDelayed(TimePeriod delay);

    /**
     * Replay speed vs real time
     */
    public double getSpeed();

    public void setSpeed(double speed);

    /**
     * Time format
     */
    public TimestampsType getTime();

    public void setTime(TimestampsType time);

    /**
     * true when file time is ignored
     */
    @Deprecated
    public boolean isIgnoreTime();

    @Deprecated
    public void setIgnoreTime(boolean ignore);

    /**
     * true when repeatedly cycling through file
     */
    public boolean isCycle();

    public void setCycle(boolean cycle);

    /**
     * Read all message as of the specified message type
     */
    public MessageType getReadAs();

    public void setReadAs(MessageType readAs);

    /**
     * true when files without record descriptions can be parsed
     */
    public boolean isSchemeKnown();

    public void setSchemeKnown(boolean schemeKnown);

    /**
     * message type to resync partial binary stream captured with tcpdump
     */
    public MessageType getResyncOn();

    public void setResyncOn(MessageType resyncOn);

    /**
     * Actual delay in milliseconds
     */
    public long getDelayActual();
}
