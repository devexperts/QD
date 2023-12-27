/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
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

/**
 * @dgen.annotate method {}
 */
public interface TapeConnectorMBean extends MessageConnectorMBean, FileWriterParams {

    /**
     * Time period for creating tape files
     */
    @Override
    TimePeriod getSplit();
    void setSplit(TimePeriod split);

    /**
     * Compression format
     */
    @Override
    StreamCompression getCompression();
    void setCompression(StreamCompression compression);

    /**
     * File format
     */
    @Override
    FileFormat getFormat();
    void setFormat(FileFormat format);

    /**
     * Time format
     */
    @Override
    TimestampsType getTime();
    void setTime(TimestampsType time);

    /**
     * Write all message as of the specified message type
     */
    @Override
    MessageType getSaveAs();
    void setSaveAs(MessageType saveAs);

    /**
     * Maximum difference between file's timestamps
     */
    @Override
    TimePeriod getStorageTime();
    void setStorageTime(TimePeriod storageTime);

    /**
     * Maximum size of all created tape files
     */
    @Override
    long getStorageSize();
    void setStorageSize(long storageSize);

    /**
     * Set of protocol options
     */
    @Override
    String getOpt();
    void setOpt(String opt);

    /**
     * Set of tmp dir path
     */
    @Override
    String getTmpDir();
    void setTmpDir(String tmpDir);
}
