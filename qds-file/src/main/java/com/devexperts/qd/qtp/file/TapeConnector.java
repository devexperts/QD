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
import com.devexperts.util.TimePeriod;

@MessageConnectorSummary(
    info = "Writes data to tape files.",
    addressFormat = "tape:<filename>"
)
public class TapeConnector extends AbstractMessageConnector implements TapeConnectorMBean, FileWriterParams {
    private final String address;

    private TimePeriod split;
    private StreamCompression compression;
    private FileFormat format;
    private TimestampsType time;
    private MessageType saveAs = MessageType.RAW_DATA;
    private TimePeriod storageTime = TimePeriod.UNLIMITED;
    private long storageSize = UNLIMITED_SIZE;
    private String opt;
    private String tmpDir;

    private volatile FileWriterHandler handler;

    protected TapeConnector(ApplicationConnectionFactory factory, String address) {
        super(factory);
        if (address == null)
            throw new NullPointerException();
        QDConfig.setDefaultProperties(this, TapeConnectorMBean.class, MessageConnector.class.getName());
        QDConfig.setDefaultProperties(this, TapeConnectorMBean.class, TapeConnector.class.getName());
        this.address = address;
    }

    @Override
    public synchronized String getAddress() {
        return address;
    }

    @Override
    public synchronized void start() {
        if (isActive() || isClosed())
            return;
        log.info("Starting TapeConnector to " + LogUtil.hideCredentials(getAddress()));
        handler = new FileWriterHandler(this);
        handler.init();
        handler.start();
        notifyMessageConnectorListeners();
    }

    @Override
    protected synchronized void handlerClosed(AbstractConnectionHandler handler) {
        if (handler != this.handler)
            return;
        this.handler = null;
    }

    @Override
    public boolean isActive() {
        return handler != null;
    }

    @Override
    public MessageConnectorState getState() {
        FileWriterHandler handler = this.handler;
        return handler != null ? handler.getHandlerState() : MessageConnectorState.DISCONNECTED;
    }

    @Override
    public int getConnectionCount() {
        return getState() == MessageConnectorState.CONNECTED ? 1 : 0;
    }

    @Override
    public EndpointStats retrieveCompleteEndpointStats() {
        EndpointStats stats = super.retrieveCompleteEndpointStats();
        FileWriterHandler handler = this.handler; // Atomic read.
        if (handler != null && handler.getHandlerState() == MessageConnectorState.CONNECTED) {
            stats.addActiveConnectionCount(1);
            stats.addConnectionStats(handler.getConnectionStats());
        }
        return stats;
    }

    @Override
    public void awaitProcessed() throws InterruptedException {
        FileWriterHandler handler = this.handler;
        if (handler != null)
            handler.awaitProcessed();
    }

    @Override
    protected Joinable stopImpl() {
        if (!isActive())
            return null;
        FileWriterHandler handler = this.handler;
        this.handler = null; // Clear before actual close to avoid recursion.
        if (handler != null) {
            log.info("Stopping TapeConnector");
            handler.close();
        }
        return handler;
    }

    // === PARAMETERS ===

    @Override
    public synchronized TimePeriod getSplit() {
        return split;
    }

    @Override
    @MessageConnectorProperty("Time period determining how often must new files be created while creating " +
        "multiple files with timestamped names. When this parameter is defined a special '~' marker in file name must be used")
    public synchronized void setSplit(TimePeriod split) {
        if (!split.equals(this.split)) {
            log.info("Setting split=" + split);
            this.split = split;
            reconfigure();
        }
    }

    @Override
    public synchronized StreamCompression getCompression() {
        return compression;
    }

    @Override
    @MessageConnectorProperty("File compression (one of \"none\", \"gzip\", or \"zip\")")
    public synchronized void setCompression(StreamCompression compression) {
        if (!compression.equals(this.compression)) {
            log.info("Setting compression=" + compression);
            this.compression = compression;
            reconfigure();
        }
    }

    @Override
    public synchronized FileFormat getFormat() {
        return format;
    }

    @Override
    @MessageConnectorProperty("Format of stored data. Its value can be one of \"text\" (see \"Help Text format\"), " +
        "\"binary\" or \"blob:<record>:<symbol>\" (binary format is used by default)")
    public synchronized void setFormat(FileFormat format) {
        if (!format.equals(this.format)) {
            log.info("Setting format=" + format);
            this.format = format;
            reconfigure();
        }
    }

    @Override
    public synchronized TimestampsType getTime() {
        return time;
    }

    @Override
    @MessageConnectorProperty("Time format (one of \"none\", \"long\", \"text\", or \"event\")")
    public synchronized void setTime(TimestampsType time) {
        if (this.time != time) {
            log.info("Setting time=" + time);
            this.time = time;
            reconfigure();
        }
    }

    @Override
    public synchronized MessageType getSaveAs() {
        return saveAs;
    }

    @Override
    @MessageConnectorProperty("Overrides the type of stored messages. Data messages can be stored as " +
        "ticker_data\", \"stream_data\", \"history_data\", or \"raw_data\"")
    public synchronized void setSaveAs(MessageType saveAs) {
        if (this.saveAs != saveAs) {
            log.info("Setting saveAs=" + saveAs);
            this.saveAs = saveAs;
            reconfigure();
        }
    }

    @Override
    public synchronized TimePeriod getStorageTime() {
        return storageTime;
    }

    @Override
    @MessageConnectorProperty("Enables deleting all taped files which " +
        "have timestamps less than current time minus \"storagetime\" value")
    public synchronized void setStorageTime(TimePeriod storageTime) {
        if (!storageTime.equals(this.storageTime)) {
            log.info("Setting storageTime=" + storageTime);
            this.storageTime = storageTime;
            reconfigure();
        }
    }

    @Override
    public synchronized long getStorageSize() {
        return storageSize;
    }

    @Override
    @MessageConnectorProperty("Enables deleting old taped files when " +
        "total size of all existing taped files is greater than \"storagesize\" value")
    public synchronized void setStorageSize(long storageSize) {
        if (this.storageSize != storageSize) {
            log.info("Setting storageSize=" + storageSize);
            this.storageSize = storageSize;
            reconfigure();
        }
    }

    @Override
    public synchronized String getOpt() {
        return opt;
    }

    @Override
    @MessageConnectorProperty("String set of protocol options")
    public synchronized void setOpt(String opt) {
        if (!opt.equals(this.opt)) {
            log.info("Setting opt=" + opt);
            this.opt = opt;
            reconfigure();
        }
    }

    @Override
    public synchronized String getTmpDir() {
        return tmpDir;
    }

    @Override
    @MessageConnectorProperty("Temporary directory for tape files processing")
    public synchronized void setTmpDir(String tmpDir) {
        if (!tmpDir.equals(this.tmpDir)) {
            log.info("Setting tmpDir=" + tmpDir);
            this.tmpDir = tmpDir;
            reconfigure();
        }
    }
}
