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
package com.devexperts.qd.qtp;

import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.io.ChunkList;
import com.devexperts.io.ChunkPool;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.TimeMarkUtil;
import com.devexperts.util.SystemProperties;

/**
 * QTP protocol implementing application connection.
 */
class MessageAdapterConnection extends ApplicationConnection<MessageAdapterConnectionFactory>
    implements MessageListener, MessageAdapter.CloseListener
{
    private static final int BYTES_TO_HEARTBEAT =
        SystemProperties.getIntProperty(MessageAdapterConnection.class, "bytesToHeartbeat", 32768);

    private static final int DELTA_MARK_UNKNOWN = Integer.MAX_VALUE;

    private static final Logging log = Logging.getLogging(MessageAdapterConnection.class);

    private final MessageAdapter adapter;
    private final ConnectionQTPComposer composer;
    private final ConnectionQTPParser parser;

    private QDStats stats = QDStats.VOID;

    private volatile long nextHeartbeatTime;
    private volatile long heartbeatDisconnectTime;

    private long heartbeatPeriod;
    private long bytesToNextHeartbeat;
    private HeartbeatPayload heartbeatPayloadOut = new HeartbeatPayload();

    private volatile int lastDeltaMark = DELTA_MARK_UNKNOWN;
    private int connectionRttMark; // assume rtt = 0 until we know it
    private int incomingLagMark; // includes connection rtt (sic!)

    MessageAdapterConnection(MessageAdapter adapter, MessageAdapterConnectionFactory factory, TransportConnection transportConnection) {
        super(factory, transportConnection);
        this.adapter = adapter;
        final DataScheme scheme = adapter.getScheme();
        composer = new ConnectionQTPComposer(scheme, this);
        parser = new ConnectionQTPParser(scheme, this);
        parser.setMixedSubscription(adapter.supportsMixedSubscription());
        parser.setFieldReplacers(adapter.getFieldReplacer());

        heartbeatPeriod = factory.getInitialHeartbeatPeriod().getTime();
        heartbeatDisconnectTime = System.currentTimeMillis() + factory.getHeartbeatTimeout().getTime();
        adapter.setMessageListener(this);
        adapter.setCloseListener(this);
        adapter.useDescribeProtocol();
    }

    ChunkPool getChunkPool() {
        return factory.getChunkPool();
    }

    @Override
    protected void startImpl() {
        QDStats stats = transportConnection.variables().get(MessageConnectors.STATS_KEY);
        if (stats != null) {
            stats.addMBean(QDStats.SType.CONNECTION.getName(), adapter);
            composer.setStats(stats);
            parser.setStats(stats);
            this.stats = stats;
        }
        adapter.start();
    }

    @Override
    protected void closeImpl() {
        adapter.close();
    }

    @Override
    public long examine(long currentTime) {
        if (currentTime >= heartbeatDisconnectTime) {
            log.info(adapter +  " heartbeat timeout exceeded: disconnecting");
            close();
        }
        long nextRetrieveTime = Math.min(adapter.nextRetrieveTime(currentTime), nextHeartbeatTime);
        if (currentTime >= nextRetrieveTime) {
            notifyChunksAvailable();
            nextRetrieveTime = currentTime + 10;
        }
        return Math.min(nextRetrieveTime, heartbeatDisconnectTime);
    }

    @Override
    public ChunkList retrieveChunks(Object owner) {
        int composingTimeMark = TimeMarkUtil.currentTimeMark();
        composer.setComposingTimeMark(composingTimeMark);
        if (composer.compose(adapter))
            notifyChunksAvailable();
        long payloadBytes = composer.getProcessed();
        long currentTimeMillis = System.currentTimeMillis();
        int currentTimeMark = TimeMarkUtil.currentTimeMark();
        composer.addComposingLag(TimeMarkUtil.signedDeltaMark(currentTimeMark - composingTimeMark), stats);
        if (currentTimeMillis >= nextHeartbeatTime || bytesToNextHeartbeat <= payloadBytes) {
            // create heartbeat to send before this message (sic!)
            // take payload chunks written so far, first (they can be null)
            ChunkList chunks = composer.getOutput(this);
            // compose heartbeat
            createOutgoingHeartbeat(currentTimeMillis, currentTimeMark, composer.getTotalAverageLagAndClear());
            // add original chunks after heartbeat
            if (chunks != null)
                composer.writeAllFromChunkList(chunks, this);
        }
        bytesToNextHeartbeat -= payloadBytes;
        return composer.getOutput(owner);
    }

    @Override
    public boolean processChunks(ChunkList chunks, Object owner) {
        parser.addChunks(chunks, owner);
        parser.setCurrentTimeMark(computeTimeMark(TimeMarkUtil.currentTimeMark()));
        parser.parse(adapter); // will call processIncomingHeartbeat as soon as they are encountered
        heartbeatDisconnectTime = System.currentTimeMillis() + factory.getHeartbeatTimeout().getTime();
        return true;
    }

    private void createOutgoingHeartbeat(long currentTimeMillis, int currentTimeMark, int lagMark) {
        heartbeatPayloadOut.setTimeMillis(currentTimeMillis);
        heartbeatPayloadOut.setTimeMark(currentTimeMark);
        heartbeatPayloadOut.setLagMark(lagMark);
        int deltaMark = this.lastDeltaMark; // atomic volatile read
        if (deltaMark != DELTA_MARK_UNKNOWN)
            heartbeatPayloadOut.setDeltaMark(deltaMark);
        composer.composeHeartbeatMessage(heartbeatPayloadOut);
        nextHeartbeatTime = currentTimeMillis + heartbeatPeriod;
        bytesToNextHeartbeat = BYTES_TO_HEARTBEAT;
        heartbeatPeriod = Math.min(heartbeatPeriod * 2, factory.getHeartbeatPeriod().getTime());
    }

    // is called from ConnectionQTPComposer
    public BinaryRecordDesc getRequestedRecordDesc(DataRecord record) {
        return parser.getRequestedRecordDesc(record);
    }

    // is called from ConnectionQTPParser/Composer
    int getConnectionRttMark() {
        return connectionRttMark;
    }

    // is called from ConnectionQTPParser
    int getIncomingLagMark() {
        return incomingLagMark;
    }

    // is called from ConnectionQTPParser
    void processIncomingDescribeProtocol(ProtocolDescriptor desc) {
        // MIND THE BUG: [QD-808] Event flags are not sent immediately after connection establishment (random effect)
        // Must not rely on optSet in adapter (it is still not set here)
        composer.setOptSet(ProtocolOption.parseProtocolOptions(desc.getProperty(ProtocolDescriptor.OPT_PROPERTY)));
        String version = desc.getProperty(ProtocolDescriptor.VERSION_PROPERTY);
        if (version != null && version.startsWith("QDS-3.")) {
            int n = "QDS-3.".length();
            while (n < version.length() && version.charAt(n) >= '0' && version.charAt(n) <= '9')
                n++;
            try {
                composer.wideDecimalSupported = Long.parseLong(version.substring("QDS-3.".length(), n)) >= 263;
            } catch (NumberFormatException e) {
                composer.wideDecimalSupported = true;
            }
        } else {
            composer.wideDecimalSupported = true;
        }
    }

    // is called from ConnectionQTPParser
    void processIncomingHeartbeat(HeartbeatPayload heartbeatPayloadIn) {
        if (heartbeatPayloadIn == null)
            return;
        int currentTimeMark = TimeMarkUtil.currentTimeMark();
        if (heartbeatPayloadIn.hasTimeMark()) {
            lastDeltaMark = TimeMarkUtil.signedDeltaMark(currentTimeMark - heartbeatPayloadIn.getTimeMark());
            if (heartbeatPayloadIn.hasDeltaMark()) {
                // there's a benign data race by design, as connectionRttMark may be concurrently read
                connectionRttMark = TimeMarkUtil.signedDeltaMark(lastDeltaMark + heartbeatPayloadIn.getDeltaMark());
            }
        }
        // update parser's time mark
        incomingLagMark = heartbeatPayloadIn.getLagMark() + connectionRttMark;
        parser.setCurrentTimeMark(computeTimeMark(currentTimeMark));
    }

    private int computeTimeMark(int currentTimeMark) {
        int mark = (currentTimeMark - incomingLagMark) & TimeMarkUtil.TIME_MARK_MASK;
        if (mark == 0)
            mark = 1; // make sure it is non-zero to distinguish it from N/A time mark
        return mark;
    }

    @Override
    public void messagesAvailable(MessageProvider provider) {
        notifyChunksAvailable();
    }

    @Override
    public void adapterClosed(MessageAdapter adapter) {
        if (adapter.isMarkedForImmediateRestart())
            markForImmediateRestart();
        close();
    }
}
