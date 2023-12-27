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
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.util.TimePeriod;

public interface FileWriterParams {
    long UNLIMITED_TIME = Long.MAX_VALUE;
    long UNLIMITED_SIZE = Long.MAX_VALUE;

    TimePeriod getSplit();
    StreamCompression getCompression();
    FileFormat getFormat();
    TimestampsType getTime();
    MessageType getSaveAs();
    TimePeriod getStorageTime();
    long getStorageSize();
    String getOpt();
    String getTmpDir();

    // setXXX methods are called via QDConfig#setProperties(...)
    public class Default implements FileWriterParams {

        private TimePeriod split;
        private StreamCompression compression;
        private FileFormat format;
        private TimestampsType time;
        private MessageType saveAs;
        private TimePeriod storageTime = TimePeriod.UNLIMITED;
        private long storageSize = UNLIMITED_SIZE;
        private String opt;
        private String tmpDir;

        @Override
        public TimePeriod getSplit() {
            return split;
        }

        public void setSplit(TimePeriod split) {
            this.split = split;
        }

        @Override
        public StreamCompression getCompression() {
            return compression;
        }

        public void setCompression(StreamCompression compression) {
            this.compression = compression;
        }

        @Override
        public FileFormat getFormat() {
            return format;
        }

        public void setFormat(FileFormat format) {
            this.format = format;
        }

        @Override
        public TimestampsType getTime() {
            return time;
        }

        public void setTime(TimestampsType time) {
            this.time = time;
        }

        @Override
        public MessageType getSaveAs() {
            return saveAs;
        }

        public void setSaveAs(MessageType saveAs) {
            this.saveAs = saveAs;
        }

        @Override
        public TimePeriod getStorageTime() {
            return storageTime;
        }

        public void setStorageTime(TimePeriod storageTime) {
            this.storageTime = storageTime;
        }

        @Override
        public long getStorageSize() {
            return storageSize;
        }

        public void setStorageSize(long storageSize) {
            this.storageSize = storageSize;
        }

        @Override
        public String getOpt() {
            return opt;
        }

        public void setOpt(String opt) {
            this.opt = opt;
        }

        @Override
        public String getTmpDir() {
            return tmpDir;
        }

        public void setTmpDir(String tmpDir) {
            this.tmpDir = tmpDir;
        }
    }
}
