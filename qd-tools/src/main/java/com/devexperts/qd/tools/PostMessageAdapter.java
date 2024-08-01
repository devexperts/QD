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

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageListener;
import com.devexperts.qd.qtp.MessageProvider;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.MessageVisitor;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.stats.QDStats;

class PostMessageAdapter extends MessageAdapter implements MessageListener {

    private static final Logging log = Logging.getLogging(PostMessageAdapter.class);

    private final DataScheme scheme;
    private final PostMessageQueue.Subscriber subscriber;
    private boolean receivedProtocolDescriptor;

    PostMessageAdapter(QDStats stats, QDEndpoint endpoint, PostMessageQueue.Subscriber subscriber) {
        super(endpoint, stats);
        this.scheme = endpoint.getScheme();
        this.subscriber = subscriber;
        useDescribeProtocol();
        subscriber.setMessageListener(this);
    }

    @Override
    public void prepareProtocolDescriptor(ProtocolDescriptor desc) {
        super.prepareProtocolDescriptor(desc);
        // will send all kinds of data messages
        for (MessageType type : MessageType.values())
            if (type.isData())
                desc.addSend(desc.newMessageDescriptor(type));
    }

    @Override
    public boolean isProtocolDescriptorCompatible(ProtocolDescriptor desc) {
        // is compatible is can receive any kind of data message
        for (MessageType type : MessageType.values())
            if (type.isData() && desc.canReceive(type))
                return true;
        return false;
    }

    @Override
    public void processDescribeProtocol(ProtocolDescriptor desc, boolean logDescriptor) {
        super.processDescribeProtocol(desc, logDescriptor);
        receivedProtocolDescriptor = true;
        notifyListener();
    }

    @Override
    public void messagesAvailable(MessageProvider provider) {
        notifyListener();
    }

    @Override
    public boolean retrieveMessages(MessageVisitor visitor) {
        super.retrieveMessages(visitor);
        long mask = retrieveMask();
        if (hasMessageMask(mask, MessageType.DESCRIBE_PROTOCOL))
            mask = retrieveDescribeProtocolMessage(visitor, mask);
        addMask(mask);
        if (!receivedProtocolDescriptor)
            return false;
        return subscriber.retrieveMessages(visitor);
    }

    @Override
    protected void processSubscription(SubscriptionIterator iterator, MessageType message) {
        // Just in case we have a legacy QD on the other side that sends us subscription anyway
        int c = 0;
        while (iterator.nextRecord() != null)
            c++;
        log.warn("Ignored " + c + " " + message + " messages");
    }

    @Override
    public DataScheme getScheme() {
        return scheme;
    }

    static class Factory extends MessageAdapter.AbstractFactory {
        private final PostMessageQueue queue;

        Factory(QDEndpoint endpoint, PostMessageQueue queue) {
            super(endpoint, null);
            this.queue = queue;
        }

        @Override
        public MessageAdapter createAdapter(QDStats stats) {
            return new PostMessageAdapter(stats, endpoint, queue.newSubscriber(getFilter()));
        }

        @Override
        public String toString() {
            return "Post";
        }
    }

    @Override
    public String toString() {
        return "Post";
    }
}
