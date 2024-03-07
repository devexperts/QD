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
package com.devexperts.qd.qtp.file;

import com.devexperts.connector.proto.ApplicationConnectionFactory;
import com.devexperts.io.StreamCompression;
import com.devexperts.monitoring.Monitored;
import com.devexperts.qd.qtp.AbstractConnectionHandler;
import com.devexperts.qd.qtp.AbstractMessageConnector;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectorState;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.help.MessageConnectorProperty;
import com.devexperts.qd.qtp.help.MessageConnectorSummary;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.transport.stats.EndpointStats;
import com.devexperts.util.LogUtil;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimePeriod;

import java.util.Date;

/**
 * Reads messages from a specified URL or file. By default when reading
 * from some file ".time" file with corresponding name is also read and
 * the messages times are taken from it to mimic delays of original
 * messages.
 * If there is no ".time" file found then all messages are given out
 * at once. It is possible to ignore ".time" files by using boolean option
 * "ignoretime".
 */
@MessageConnectorSummary(
    info = "Connects to a file.",
    addressFormat = "file:<filename>"
)
public class FileConnector extends AbstractMessageConnector implements FileConnectorMBean, FileReaderParams {
    static final long NA_TIME = Long.MIN_VALUE;

    private String address;
    private StreamCompression compression;
    private FileFormat format;
    private long startTime = NA_TIME;
    private long stopTime = NA_TIME;
    private long delayTime = NA_TIME;
    private double speed = 1;
    private TimestampsType time;
    private boolean ignoreTime;
    private boolean cycle;
    private MessageType readAs;
    private boolean schemeKnown;
    private MessageType resyncOn;

    private volatile FileReaderHandler handler;

    /**
     * Creates new file connector.
     *
     * @param factory application connection factory to use
     * @param address address of file to use
     * @throws NullPointerException if {@code factory} or {@code address} is {@code null}
     */
    public FileConnector(ApplicationConnectionFactory factory, String address) {
        super(factory);
        if (address == null)
            throw new NullPointerException();
        QDConfig.setDefaultProperties(this, FileConnectorMBean.class, MessageConnector.class.getName());
        QDConfig.setDefaultProperties(this, FileConnectorMBean.class, FileConnector.class.getName());
        this.address = address;
    }

    @Override
    public synchronized void start() {
        if (handler != null || isClosed())
            return;
        log.info("Starting FileConnector to " + LogUtil.hideCredentials(getAddress()));
        handler = new FileReaderHandler(this);
        handler.start();
        notifyMessageConnectorListeners();
    }

    @Override
    protected synchronized Joinable stopImpl() {
        FileReaderHandler handler = this.handler;
        this.handler = null; // Clear before actual close to avoid recursion.
        if (handler != null) {
            log.info("Stopping FileConnector");
            handler.close();
        }
        return handler;
    }

    @Override
    protected synchronized void handlerClosed(AbstractConnectionHandler handler) {
        if (handler != this.handler)
            return;
        this.handler = null;
    }

    @Override
    public synchronized String getAddress() {
        return address;
    }

    @Override
    public synchronized void setAddress(String address) {
        if (!address.equals(this.address)) {  // also checks for null
            log.info("Setting address=" + LogUtil.hideCredentials(address));
            this.address = address;
            reconfigure();
        }
    }

    @Override
    public synchronized StreamCompression getCompression() {
        return compression;
    }

    @Override
    @MessageConnectorProperty("File compression (one of \"none\", \"gzip\", or \"zip\"), " +
        "autodetect by default from file header")
    public void setCompression(StreamCompression compression) {
        if (!compression.equals(this.compression)) {
            log.info("Setting setCompression=" + compression);
            this.compression = compression;
            reconfigure();
        }
    }

    @Override
    public synchronized FileFormat getFormat() {
        return format;
    }

    @Override
    @MessageConnectorProperty("File format (one of \"binary\", \"text\", \"csv\", or \"blob:<record>:<symbol>\"), " +
        "autodetect by default from file header")
    public synchronized void setFormat(FileFormat format) {
        if (!format.equals(this.format)) {
            log.info("Setting format=" + format);
            this.format = format;
            reconfigure();
        }
    }

    @Override
    public boolean isActive() {
        return handler != null;
    }

    @Override
    public MessageConnectorState getState() {
        FileReaderHandler handler = this.handler;
        return handler != null ? handler.getHandlerState() : MessageConnectorState.DISCONNECTED;
    }

    @Override
    public int getConnectionCount() {
        return getState() == MessageConnectorState.CONNECTED ? 1 : 0;
    }

    @Override
    public EndpointStats retrieveCompleteEndpointStats() {
        EndpointStats stats = super.retrieveCompleteEndpointStats();
        FileReaderHandler handler = this.handler; // Atomic read.
        if (handler != null && handler.getHandlerState() == MessageConnectorState.CONNECTED) {
            stats.addActiveConnectionCount(1);
            stats.addConnectionStats(handler.getConnectionStats());
        }
        return stats;
    }

    private void checkTimeAndIgnoreTime(TimestampsType time, boolean ignoreTime) {
        if (ignoreTime && time != null)
            throw new IllegalArgumentException("Cannot set both ignoreTime and time. Use time=none to ignore time");
    }

    private void checkStartAndStop(long startTime, long stopTime) {
        if (startTime != NA_TIME && stopTime != NA_TIME && stopTime <= startTime)
            throw new IllegalArgumentException("Cannot set stop at or before start");
    }

    private void checkStartAndDelay(long startTime, long delayTime) {
        if (startTime != NA_TIME && delayTime != NA_TIME)
            throw new IllegalArgumentException("Cannot set both start and delay");
    }

    private void checkStopAndDelay(long stopTime, long delayTime) {
        if (stopTime != NA_TIME && delayTime != NA_TIME)
            throw new IllegalArgumentException("Cannot set both stop and delay");
    }

    private void checkSpeedAndDelay(double speed, long delayTime) {
        if (speed != 1 && delayTime != NA_TIME)
            throw new IllegalArgumentException("Cannot set both speed and delay");
    }

    private void checkCycleAndDelay(boolean cycle, long delayTime) {
        if (cycle && delayTime != NA_TIME)
            throw new IllegalArgumentException("Cannot set both cycle and delay");
    }

    @Override
    public synchronized long getStartTime() {
        return startTime;
    }

    @Override
    public synchronized Date getStart() {
        return startTime == NA_TIME ? null : new Date(startTime);
    }

    @Override
    @MessageConnectorProperty("Time to start playing from, use [YYYYMMDD-]HHMMSS[.sss][tz] format")
    public synchronized void setStart(Date time) {
        long startTime = time == null ? NA_TIME : time.getTime();
        if (this.startTime != startTime) {
            checkStartAndStop(startTime, stopTime);
            checkStartAndDelay(startTime, delayTime);
            log.info("Setting start=" + TimeFormat.DEFAULT.format(time));
            this.startTime = startTime;
            reconfigure();
        }
    }

    @Override
    public synchronized long getStopTime() {
        return stopTime;
    }

    @Override
    public synchronized Date getStop() {
        return stopTime == NA_TIME ? null : new Date(stopTime);
    }

    @Override
    @MessageConnectorProperty("Time to stop playing, use [YYYYMMDD-]HHMMSS[.sss][tz] format")
    public synchronized void setStop(Date time) {
        long stopTime = time == null ? NA_TIME : time.getTime();
        if (this.stopTime != stopTime) {
            checkStartAndStop(startTime, stopTime);
            checkStopAndDelay(stopTime, delayTime);
            log.info("Setting stop=" + TimeFormat.DEFAULT.format(time));
            this.stopTime = stopTime;
            reconfigure();
        }
    }

    @Override
    public synchronized long getDelayTime() {
        return delayTime;
    }

    @Override
    @Monitored(name = "delayed", description = "Configured delaying time period")
    public synchronized TimePeriod getDelayed() {
        return delayTime == NA_TIME ? null : TimePeriod.valueOf(delayTime);
    }

    @Override
    @MessageConnectorProperty("Delay relatively to current time")
    public synchronized void setDelayed(TimePeriod delay) {
        long delayTime = delay == null ? NA_TIME : delay.getTime();
        if (this.delayTime != delayTime) {
            checkStartAndDelay(startTime, delayTime);
            checkStopAndDelay(stopTime, delayTime);
            checkSpeedAndDelay(speed, delayTime);
            checkCycleAndDelay(cycle, delayTime);
            log.info("Setting delayed=" + delay);
            this.delayTime = delayTime;
            reconfigure();
        }
    }

    @Override
    public double getSpeed() {
        return speed;
    }

    @Override
    @MessageConnectorProperty("Replay speed vs real time, use \"max\" to read file as fast as possible, defaults to 1")
    public void setSpeed(double speed) {
        if (!(speed > 0)) // catch NaNs too
            throw new IllegalArgumentException("Invalid speed=" + speed);
        if (this.speed != speed) {
            checkSpeedAndDelay(speed, delayTime);
            log.info("Setting speed=" + (speed == Double.MAX_VALUE ? "max" : String.valueOf(speed)));
            this.speed = speed;
            reconfigure();
        }
    }

    @Override
    public synchronized TimestampsType getTime() {
        return time;
    }

    @Override
    @MessageConnectorProperty("Time format (one of \"none\", \"long\", \"text\", \"field\", or \"message\"), autodetect by default")
    public synchronized void setTime(TimestampsType time) {
        if (this.time != time) {
            checkTimeAndIgnoreTime(time, ignoreTime);
            log.info("Setting time=" + time);
            this.time = time;
            reconfigure();
        }
    }

    @Override
    public synchronized boolean isIgnoreTime() {
        return ignoreTime;
    }

    @Override
    @MessageConnectorProperty("Ignores \".time\" files even if they present")
    public synchronized void setIgnoreTime(boolean ignore) {
        if (ignoreTime != ignore) {
            checkTimeAndIgnoreTime(time, ignore);
            log.warn("SETTING DEPRECATED ignoreTime=" + ignore + ". Set speed=max to replay as fast as possible.");
            ignoreTime = ignore;
            reconfigure();
        }
    }

    @Override
    public synchronized boolean isCycle() {
        return cycle;
    }

    @Override
    @MessageConnectorProperty("Enables cycle playback")
    public synchronized void setCycle(boolean cycle) {
        if (this.cycle != cycle) {
            checkCycleAndDelay(cycle, delayTime);
            log.info("Setting cycle=" + cycle);
            this.cycle = cycle;
            reconfigure();
        }
    }

    @Override
    public synchronized MessageType getReadAs() {
        return readAs;
    }

    @Override
    @MessageConnectorProperty("Overrides the type of read messages (one of \"ticker_data\", \"stream_data\", \"history_data\", or \"raw_data\", works for binary tape files only)")
    public synchronized void setReadAs(MessageType readAs) {
        if (this.readAs != readAs) {
            log.info("Setting readAs=" + readAs);
            this.readAs = readAs;
            reconfigure();
        }
    }

    @Override
    public synchronized boolean isSchemeKnown() {
        return schemeKnown;
    }

    @Override
    @MessageConnectorProperty("Enables parsing of files without record descriptions")
    public synchronized void setSchemeKnown(boolean schemeKnown) {
        if (this.schemeKnown != schemeKnown) {
            log.info("Setting schemeKnown=" + schemeKnown);
            this.schemeKnown = schemeKnown;
            reconfigure();
        }
    }

    @Override
    public synchronized  MessageType getResyncOn() {
        return resyncOn;
    }

    @Override
    @MessageConnectorProperty("Message type to resync partial binary stream captured with tcpdump")
    public synchronized void setResyncOn(MessageType resyncOn) {
        if (this.resyncOn != resyncOn) {
            log.info("Setting resyncOn=" + resyncOn);
            this.resyncOn = resyncOn;
            reconfigure();
        }
    }

    @Override
    @Monitored(name = "delay_actual", description = "Actual delay in ms or delay time if not connected yet")
    public long getDelayActual() {
        FileReaderHandler handler = this.handler;
        return handler != null ? handler.getDelayActual() : getDelayTime();
    }
}
