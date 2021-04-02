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
package com.devexperts.qd.qtp;

import com.devexperts.qd.DataProvider;
import com.devexperts.qd.SubscriptionProvider;

/**
 * This class collapses various visitXXXData and visitXXXSubscription methods of
 * {@link MessageVisitor} interface into just two methods:
 * {@link #visitData} and {@link #visitSubscription}.
 *
 * <p>All implementations of {@link MessageVisitor} interface shall extends this class.
 */
public abstract class AbstractMessageVisitor implements MessageVisitor {
    public void visitDescribeProtocol(ProtocolDescriptor descriptor) {}

    public void visitHeartbeat(HeartbeatPayload heartbeatPayload) {}

    /**
     * This method consumes available data for data message from the given data provider.
     * @return <code>false</code> if it had retrieved all available data and no data left,
     *         <code>true</code> if more data remains to be consumed.
     */
    public abstract boolean visitData(DataProvider provider, MessageType message);

    /**
     * This method consumes available subscription for subscription message from the given subscription provider.
     * @return <code>false</code> if it had retrieved all available subscription and nothing left,
     *         <code>true</code> if more subscription remains to be consumed.
     */
    public abstract boolean visitSubscription(SubscriptionProvider provider, MessageType message);

    public final boolean visitTickerData(DataProvider provider) {
        return visitData(provider, MessageType.TICKER_DATA);
    }

    public final boolean visitTickerAddSubscription(SubscriptionProvider provider) {
        return visitSubscription(provider, MessageType.TICKER_ADD_SUBSCRIPTION);
    }

    public final boolean visitTickerRemoveSubscription(SubscriptionProvider provider) {
        return visitSubscription(provider, MessageType.TICKER_REMOVE_SUBSCRIPTION);
    }

    public final boolean visitStreamData(DataProvider provider) {
        return visitData(provider, MessageType.STREAM_DATA);
    }

    public final boolean visitStreamAddSubscription(SubscriptionProvider provider) {
        return visitSubscription(provider, MessageType.STREAM_ADD_SUBSCRIPTION);
    }

    public final boolean visitStreamRemoveSubscription(SubscriptionProvider provider) {
        return visitSubscription(provider, MessageType.STREAM_REMOVE_SUBSCRIPTION);
    }

    public final boolean visitHistoryData(DataProvider provider) {
        return visitData(provider, MessageType.HISTORY_DATA);
    }

    public final boolean visitHistoryAddSubscription(SubscriptionProvider provider) {
        return visitSubscription(provider, MessageType.HISTORY_ADD_SUBSCRIPTION);
    }

    public final boolean visitHistoryRemoveSubscription(SubscriptionProvider provider) {
        return visitSubscription(provider, MessageType.HISTORY_REMOVE_SUBSCRIPTION);
    }

    /**
     * This method consumes other message type. This implementation always return {@code true}.
     */
    public boolean visitOtherMessage(int messageType, byte[] messageBytes, int offset, int length) {
        return true;
    }
}
