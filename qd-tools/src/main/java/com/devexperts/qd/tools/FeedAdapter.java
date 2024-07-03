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
package com.devexperts.qd.tools;

import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.SubscriptionVisitor;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordConsumer;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.MessageVisitor;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.stats.QDStats;

class FeedAdapter extends DistributorAdapter {
    public static class Factory extends DistributorAdapter.Factory {
        private final boolean raw;
        private final boolean subscribe;
        private final FeedDelayer delayer;

        Factory(QDEndpoint endpoint, QDFilter filter, boolean raw, boolean subscribe, FeedDelayer delayer) {
            super(endpoint, filter);
            this.raw = raw;
            this.subscribe = subscribe;
            this.delayer = delayer;
        }

        @Override
        public MessageAdapter createAdapter(QDStats stats) {
            return new FeedAdapter(endpoint, ticker, stream, history, getFilter(), getStripe(), stats,
                raw, subscribe, delayer);
        }
    }

    private final QDFilter filter;
    private final RecordBuffer sub = new RecordBuffer(RecordMode.SUBSCRIPTION); // just for initial subscription
    private final boolean subscribe;
    private final FeedDelayer delayer;

    FeedAdapter(QDEndpoint endpoint, QDTicker ticker, QDStream stream, QDHistory history,
        QDFilter filter, QDFilter stripe, QDStats stats, boolean raw, boolean subscribe, FeedDelayer delayer)
    {
        super(endpoint, ticker, stream, history, filter, stripe, stats, null);
        this.filter = filter;
        this.subscribe = subscribe;
        if (!raw) {
            DataScheme scheme = getScheme();
            int cipher = scheme.getCodec().getWildcardCipher();
            for (int i = 0, n = scheme.getRecordCount(); i < n; i++) {
                if (filter == null || filter.acceptRecord(scheme.getRecord(i), cipher, null))
                    sub.add(scheme.getRecord(i), cipher, null);
            }
            addMask(getMessageMask(MessageType.STREAM_ADD_SUBSCRIPTION)); // will send wildcard subscription
        }
        this.delayer = delayer;
        if (delayer != null) {
            delayer.setDataConsumer(new RecordConsumer() {
                public void process(RecordSource source) {
                    processOutgoing(source);
                }
            });
        }
    }

    @Override
    protected boolean visitSubscription(MessageVisitor visitor, SubscriptionProvider provider, MessageType message) {
        // subscribe to internally generated subscription
        if (message == MessageType.STREAM_ADD_SUBSCRIPTION && !sub.isEmpty())
            if (visitor.visitSubscription(sub, MessageType.STREAM_ADD_SUBSCRIPTION))
                return true; // have more data
        // convert all subscription to stream subscription
        return super.visitSubscription(visitor, provider, MessageType.STREAM_ADD_SUBSCRIPTION);
    }

    @Override
    protected void subscriptionChanged(SubscriptionProvider provider, MessageType message) {
        // forward add subscription upstream only if "subscribe" is set
        // all remove subscription is always ignored
        if (subscribe && message.isSubscriptionAdd()) {
            addMask(1L << message.getId());
        } else {
            provider.retrieveSubscription(SubscriptionVisitor.VOID);
        }
    }

    private final RecordBuffer buf = new RecordBuffer();

    private void processIncoming(DataIterator iterator) {
        synchronized (buf) {
            buf.clear();
            // Note -- we filter all incoming data, since we get connected to RAW ports in general which send just everything
            buf.processData(iterator, filter);
            if (delayer != null) {
                delayer.process(buf);
            } else {
                processOutgoing(buf);
            }
            buf.clear();
        }
    }

    private void processOutgoing(RecordSource source) {
        long position = source.getPosition();
        super.processTickerData(source);
        source.setPosition(position);
        super.processStreamData(source);
        source.setPosition(position);
        super.processHistoryData(source);
    }

    @Override
    public void processTickerData(DataIterator iterator) {
        processIncoming(iterator);
    }

    @Override
    public void processStreamData(DataIterator iterator) {
        processIncoming(iterator);
    }

    @Override
    public void processHistoryData(DataIterator iterator) {
        processIncoming(iterator);
    }
}
