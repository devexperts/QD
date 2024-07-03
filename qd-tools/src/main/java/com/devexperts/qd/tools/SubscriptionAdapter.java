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

import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.MessageAdapter;
import com.devexperts.qd.qtp.MessageListener;
import com.devexperts.qd.qtp.MessageType;
import com.devexperts.qd.qtp.MessageVisitor;
import com.devexperts.qd.qtp.ProtocolDescriptor;
import com.devexperts.qd.qtp.QDEndpoint;
import com.devexperts.qd.stats.QDStats;

/**
 * Specialized {@link AgentAdapter AgentAdapter} that receives subscription from uplink, but does not
 * deliver any data back. Data is delivered to a separately specified {@link ConnectionProcessor}.
 */
class SubscriptionAdapter extends AgentAdapter {

    public static class Factory extends AgentAdapter.Factory {
        private final MessageListener listener;
        private final MessageVisitor dataVisitor;

        public Factory(QDEndpoint endpoint, QDFilter filter, MessageListener listener, MessageVisitor dataVisitor) {
            super(endpoint, filter);
            this.listener = listener;
            this.dataVisitor = dataVisitor;
        }

        @Override
        public MessageAdapter createAdapter(QDStats stats) {
            return new SubscriptionAdapter(endpoint, ticker, stream, history,
                getFilter(), getStripe(), stats, listener, dataVisitor);
        }
    }

    private final MessageVisitor dataVisitor;
    private final MessageListener listener;

    SubscriptionAdapter(QDEndpoint endpoint, QDTicker ticker, QDStream stream, QDHistory history,
        QDFilter filter, QDFilter stripe, QDStats stats, MessageListener listener, MessageVisitor dataVisitor)
    {
        super(endpoint, ticker, stream, history, filter, stripe, stats);
        this.dataVisitor = dataVisitor;
        this.listener = listener;
    }

    @Override
    protected QDAgent.Builder createAgentBuilder(
        QDCollector collector, SubscriptionFilter filter, String keyProperties)
    {
        return super.createAgentBuilder(collector, filter, keyProperties).withVoidRecordListener(dataVisitor == null);
    }

    @Override
    protected void notifyListener() {
        super.notifyListener();
        if (listener != null)
            listener.messagesAvailable(this);
    }

    @Override
    public boolean retrieveMessages(MessageVisitor visitor) {
        if (visitor == dataVisitor) {
            //noinspection StatementWithEmptyBody
            while (super.retrieveDataMessages(visitor)) { // get data messages to dataVisitor
                // process until everything is processed
            }
            return false;
        }
        return super.retrieveMessages(visitor);
    }

    @Override
    protected boolean retrieveDataMessages(MessageVisitor visitor) {
        return false; // do not retrieve data messages to standard consumers (socket writer thread)
    }

    @Override
    public boolean isProtocolDescriptorCompatible(ProtocolDescriptor desc) {
        for (QDContract contract : getEndpoint().getContracts())
            if (desc.canSend(MessageType.forAddSubscription(contract)))
                return true;
        return false;
    }
}
