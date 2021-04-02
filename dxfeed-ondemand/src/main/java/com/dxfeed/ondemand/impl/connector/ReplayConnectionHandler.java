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

import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.qtp.AbstractConnectionHandler;
import com.devexperts.qd.qtp.AbstractMessageVisitor;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageConnectorState;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.MessageListener;
import com.devexperts.qd.qtp.MessageProvider;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.stats.QDStats;
import com.dxfeed.ondemand.impl.MarketDataReplay;

import java.util.Date;

class ReplayConnectionHandler extends AbstractConnectionHandler<OnDemandConnector> {
    private final MessageProcessor messageProcessor = new MessageProcessor();
    private final MarketDataReplay replay;
    private MessageAdapter adapter;

    private volatile double time; // fractional time for very low-speed playback
    private volatile double speed;
    private MessageConnectorState connectionState = MessageConnectorState.CONNECTING;

    ReplayConnectionHandler(OnDemandConnector connector) {
        super(connector);
        replay = connector.getMarketDataReplay();
        time = connector.getTime().getTime();
        speed = connector.getSpeed();
    }

    public long getTime() {
        return (long) time;
    }

    public void setSpeed(double speed) {
        this.speed = speed;
    }

    public synchronized MessageConnectorState getConnectionState() {
        return connectionState;
    }

    private void updateConnectionState(MessageConnectorState connectionState) {
        synchronized (this) {
            if (connectionState == this.connectionState)
                return;
            this.connectionState = connectionState;
        }
        connector.notifyMessageConnectorListeners();
    }

    @Override
    protected void doWork() throws Throwable {
        // wait if needed to prevent abuse
        connector.getReconnectHelper().sleepBeforeConnection();
        // configure & start replay
        replay.clearSubscription();
        replay.setToken(connector.resolveToken());
        replay.setTime((long) time);
        replay.start();
        // create and start adapter
        MessageAdapter.Factory factory = MessageConnectors.retrieveMessageAdapterFactory(connector.getFactory());
        this.adapter = factory.createAdapter(connector.getStats().getOrCreate(QDStats.SType.CONNECTIONS));
        this.adapter.setMessageListener(messageProcessor);
        this.adapter.start();
        // loop
        while (!isClosed()) {
            // process added & removed subscription
            messageProcessor.retrieveMessages();
            // wait until data becomes available or subscription changes again
            while (!isClosed() && !messageProcessor.available && isPreBuffering()) {
                if (replay.hasPermanentError())
                    return; // shutdown this handler
                updateConnectionState(MessageConnectorState.CONNECTING);
                sleepTickPeriod();
            }
            // replay data is available for playback
            updateConnectionState(MessageConnectorState.CONNECTED);
            // process data at a specified speed until subscription changes or pre-buffering starts again
            long timeWall = System.currentTimeMillis();
            while (!isClosed() && !messageProcessor.available && !isPreBuffering()) {
                if (replay.hasPermanentError())
                    return; // shutdown this handler
                RecordBuffer buf = replay.getUpdate((long) time);
                processData(buf);
                buf.release();
                connector.updateTime(new Date((long) time));
                sleepTickPeriod();
                long timeWallNew = System.currentTimeMillis();
                time += speed * (timeWallNew - timeWall);
                timeWall = timeWallNew;
            }
        }
    }

    private void sleepTickPeriod() throws InterruptedException {
        Thread.sleep(connector.getTickPeriod().getTime());
    }

    private boolean isPreBuffering() {
        return replay.getAvailableData((long) time) < 1;
    }

    private void processData(RecordBuffer buf) {
        adapter.processTickerData(buf);
        buf.rewind();
        adapter.processStreamData(buf);
        buf.rewind();
        adapter.processHistoryData(buf);
    }

    private void closeAdapter() {
        if (adapter != null) {
            try {
                adapter.close();
            } catch (Throwable t) {
                log.error("Failed to close adapter", t);
            }
            adapter = null;
        }
    }

    @Override
    protected void closeImpl(Throwable reason) {
        replay.stop();
        closeAdapter();
        log.info("Replay stopped");
    }

    public void joinAll() throws InterruptedException {
        join();
        for (Thread thread : replay.getStoppedThreads())
            if (thread != null)
                thread.join();
    }

    class MessageProcessor extends AbstractMessageVisitor implements MessageListener {
        volatile boolean available;

        public void retrieveMessages() {
            available = false;
            while (adapter.retrieveMessages(this)); // spin until all are retrieved
        }

        @Override
        public void messagesAvailable(MessageProvider provider) {
            available = true;
        }

        @Override
        public boolean visitData(DataProvider provider, MessageType message) {
            return provider.retrieveData(DataVisitor.VOID);
        }

        @Override
        public boolean visitSubscription(SubscriptionProvider provider, MessageType message) {
            RecordBuffer sub = RecordBuffer.getInstance(RecordMode.SUBSCRIPTION);
            boolean result = provider.retrieveSubscription(sub);
            if (message.isSubscriptionAdd())
                replay.addSubscription(sub);
            else
                replay.removeSubscription(sub);
            sub.release();
            return result;
        }
    }
}
