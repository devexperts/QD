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
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.stats.QDStats;

public interface FileReaderParams {
    public static final double MAX_SPEED = Double.MAX_VALUE;

    public String getUser();
    public String getPassword();
    public long getStartTime();
    public long getStopTime();
    public long getDelayTime();
    public StreamCompression getCompression();
    public FileFormat getFormat();
    /** @deprecated Use {@link #getTime()} */
    boolean isIgnoreTime();
    public TimestampsType getTime();
    public double getSpeed();
    public boolean isCycle();
    public MessageType getReadAs();
    public boolean isSchemeKnown();
    public MessageType getResyncOn();
    public QDStats getStats();
    public String getFieldReplacer();

    public static class Default implements FileReaderParams {
        private String user = "";
        private String password = "";
        private long startTime = FileConnector.NA_TIME;
        private long stopTime = FileConnector.NA_TIME;
        private long delayTime = FileConnector.NA_TIME;
        private StreamCompression compression;
        private FileFormat format;
        private boolean ignoreTime;
        private TimestampsType time;
        private double speed = 1;
        private boolean cycle;
        private MessageType readAs;
        private boolean schemeKnown;
        private MessageType resyncOn;
        private QDStats stats = QDStats.VOID;
        private String fieldReplacer;

        @Override
        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        @Override
        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        @Override
        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        @Override
        public long getStopTime() {
            return stopTime;
        }

        public void setStopTime(long stopTime) {
            this.stopTime = stopTime;
        }

        @Override
        public long getDelayTime() {
            return delayTime;
        }

        public void setDelayTime(long delayTime) {
            this.delayTime = delayTime;
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
        public boolean isIgnoreTime() {
            return ignoreTime;
        }

        public void setIgnoreTime(boolean ignoreTime) {
            this.ignoreTime = ignoreTime;
        }

        @Override
        public TimestampsType getTime() {
            return time;
        }

        public void setTime(TimestampsType time) {
            this.time = time;
        }

        @Override
        public double getSpeed() {
            return speed;
        }

        public void setSpeed(double speed) {
            this.speed = speed;
        }

        @Override
        public boolean isCycle() {
            return cycle;
        }

        public void setCycle(boolean cycle) {
            this.cycle = cycle;
        }

        @Override
        public MessageType getReadAs() {
            return readAs;
        }

        public void setReadAs(MessageType readAs) {
            this.readAs = readAs;
        }

        @Override
        public boolean isSchemeKnown() {
            return schemeKnown;
        }

        public void setSchemeKnown(boolean schemeKnown) {
            this.schemeKnown = schemeKnown;
        }

        @Override
        public MessageType getResyncOn() {
            return resyncOn;
        }

        public void setResyncOn(MessageType resyncOn) {
            this.resyncOn = resyncOn;
        }

        @Override
        public QDStats getStats() {
            return stats;
        }

        public void setStats(QDStats stats) {
            this.stats = stats;
        }

        @Override
        public String getFieldReplacer() {
            return fieldReplacer;
        }

        public void setFieldReplacer(String fieldReplacer) {
            this.fieldReplacer = fieldReplacer;
        }
    }
}
