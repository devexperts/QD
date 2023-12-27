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
package com.devexperts.qd.dxlink.websocket.application;

import com.devexperts.connector.proto.ApplicationConnection;
import com.devexperts.connector.proto.TransportConnection;
import com.devexperts.io.ChunkList;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.MessageListener;
import com.devexperts.qd.qtp.MessageProvider;
import com.devexperts.qd.stats.QDStats;
import io.netty.buffer.ByteBuf;

import java.util.List;

/**
 * DxLink protocol implementing application connection.
 */
public class DxLinkWebSocketApplicationConnection
    extends ApplicationConnection<DxLinkWebSocketApplicationConnectionFactory>
    implements MessageListener, MessageAdapter.CloseListener
{
    private final MessageAdapter adapter;
    private final DxLinkWebSocketQTPParser parser;
    private final DxLinkWebSocketQTPComposer composer;
    private final HeartbeatProcessor heartbeatProcessor;
    private volatile long nextHeartbeatTime;
    private volatile long nextDisconnectTime;

    DxLinkWebSocketApplicationConnection(MessageAdapter adapter,
        DxLinkWebSocketApplicationConnectionFactory factory, TransportConnection transportConnection,
        DxLinkWebSocketQTPParser parser, DxLinkWebSocketQTPComposer composer, HeartbeatProcessor heartbeatProcessor)
    {
        super(factory, transportConnection);
        this.adapter = adapter;
        adapter.setMessageListener(this);
        adapter.setCloseListener(this);
        this.heartbeatProcessor = heartbeatProcessor;
        this.parser = parser;
        this.composer = composer;
        this.nextHeartbeatTime = this.heartbeatProcessor.calculateNextHeartbeatTime();
        this.nextDisconnectTime = this.heartbeatProcessor.calculateNextDisconnectTime();
    }

    @Override
    protected void startImpl() {
        QDStats stats = transportConnection.variables().get(MessageConnectors.STATS_KEY);
        if (stats != null) {
            stats.addMBean(QDStats.SType.CONNECTION.getName(), adapter);
            composer.setStats(stats);
            parser.setStats(stats);
        }
        adapter.start();
    }

    @Override
    protected void closeImpl() {
        adapter.close();
    }

    @Override
    public long examine(long currentTime) {
        if (currentTime >= nextDisconnectTime)
            throw new RuntimeException(adapter + " heartbeat timeout exceeded: disconnecting");
        long nextRetrieveTime = Math.min(adapter.nextRetrieveTime(currentTime), nextHeartbeatTime);
        if (currentTime >= nextRetrieveTime) {
            notifyChunksAvailable();
            nextRetrieveTime = currentTime + 10;
        }
        return Math.min(nextRetrieveTime, nextDisconnectTime);
    }

    @Override
    public ChunkList retrieveChunks(Object owner) {
        throw new IllegalStateException();
    }

    public List<ByteBuf> retrieveMessages() {
        if (composer.composeMessage(adapter))
            notifyChunksAvailable();
        if (System.currentTimeMillis() >= nextHeartbeatTime) {
            nextHeartbeatTime = heartbeatProcessor.calculateNextHeartbeatTime();
            composer.composeKeepalive();
        }
        return composer.retrieveMessages();
    }

    @Override
    public boolean processChunks(ChunkList chunks, Object owner) {
        throw new IllegalStateException();
    }

    public void processMessage(String message) {
        // We first parse the message and then calculate the disconnect time from the server,
        // because we use the new keepalive timeout after receiving the setup from the server
        parser.parse(message, adapter);
        nextDisconnectTime = heartbeatProcessor.calculateNextDisconnectTime();
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
