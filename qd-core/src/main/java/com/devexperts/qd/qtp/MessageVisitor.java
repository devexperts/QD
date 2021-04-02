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
 * The <code>MessageVisitor</code> defines protocol of serial access to messages
 * using Visitor pattern. It allows message provider with complicated storage
 * effectively give away messages to external consumer.
 * <p>
 * All visiting methods must return <code>false</code> if they have retrieved
 * all intended data and <code>true</code> otherwise. The later usually happens
 * because the visitor has no more capacity to retrieve data.
 * <p>
 * Because visited data is being sent in a serial order, it is a good idea
 * to consult data priorities before deciding the order of data visiting.
 * <p>
 * Implementations of this interface shall extend {@link AbstractMessageVisitor}.
 */
public interface MessageVisitor {
    public void visitDescribeProtocol(ProtocolDescriptor descriptor);

    public void visitHeartbeat(HeartbeatPayload heartbeatPayload);

    /**
     * This method consumes available data for data message from the given data provider.
     * @return <code>false</code> if it had retrieved all available data and no data left,
     *         <code>true</code> if more data remains to be consumed.
     */
    public boolean visitData(DataProvider provider, MessageType message);

    /**
     * This method consumes available subscription for subscription message from the given subscription provider.
     * @return <code>false</code> if it had retrieved all available subscription and nothing left,
     *         <code>true</code> if more subscription remains to be consumed/
     */
    public boolean visitSubscription(SubscriptionProvider provider, MessageType message);

    /**
     * This method consumes available data for {@link MessageType#TICKER_DATA} message from
     * the given data provider.
     * @return <code>false</code> if it had retrieved all available data and no data left,
     * <code>true</code> if more data remains to be consumed
     * @deprecated use {@link #visitData(DataProvider, MessageType) visitData(DataProvider provider, MessageType MessageType.TICKER_DATA)} instead
     */
    @Deprecated
    public boolean visitTickerData(DataProvider provider);

    /**
     * This method consumes available subscription for {@link MessageType#TICKER_ADD_SUBSCRIPTION} message from
     * the given subscription provider.
     * @return <code>false</code> if it had retrieved all available subscription and nothing left,
     * <code>true</code> if more subscription remains to be consumed
     * @deprecated use {@link #visitSubscription(SubscriptionProvider, MessageType) visitSubscription(SubscriptionProvider provider, MessageType MessageType.TICKER_ADD_SUBSCRIPTION)} instead
     */
    @Deprecated
    public boolean visitTickerAddSubscription(SubscriptionProvider provider);

    /**
     * This method consumes available subscription for {@link MessageType#TICKER_REMOVE_SUBSCRIPTION} message from
     * the given subscription provider.
     * @return <code>false</code> if it had retrieved all available subscription and nothing left,
     * <code>true</code> if more subscription remains to be consumed
     * @deprecated use {@link #visitSubscription(SubscriptionProvider, MessageType) visitSubscription(SubscriptionProvider provider, MessageType MessageType.TICKER_REMOVE_SUBSCRIPTION)} instead
     */
    @Deprecated
    public boolean visitTickerRemoveSubscription(SubscriptionProvider provider);

    /**
     * This method consumes available data for {@link MessageType#STREAM_DATA} message from
     * the given data provider.
     * @return <code>false</code> if it had retrieved all available data and no data left,
     * <code>true</code> if more data remains to be consumed
     * @deprecated use {@link #visitData(DataProvider, MessageType) visitData(DataProvider provider, MessageType MessageType.STREAM_DATA)} instead
     */
    @Deprecated
    public boolean visitStreamData(DataProvider provider);

    /**
     * This method consumes available subscription for {@link MessageType#STREAM_ADD_SUBSCRIPTION} message from
     * the given subscription provider.
     * @return <code>false</code> if it had retrieved all available subscription and nothing left,
     * <code>true</code> if more subscription remains to be consumed
     * @deprecated use {@link #visitSubscription(SubscriptionProvider, MessageType) visitSubscription(SubscriptionProvider provider, MessageType MessageType.STREAM_ADD_SUBSCRIPTION)} instead
     */
    @Deprecated
    public boolean visitStreamAddSubscription(SubscriptionProvider provider);

    /**
     * This method consumes available subscription for {@link MessageType#STREAM_REMOVE_SUBSCRIPTION} message from
     * the given subscription provider.
     * @return <code>false</code> if it had retrieved all available subscription and nothing left,
     * <code>true</code> if more subscription remains to be consumed
     * @deprecated use {@link #visitSubscription(SubscriptionProvider, MessageType) visitSubscription(SubscriptionProvider provider, MessageType MessageType.STREAM_REMOVE_SUBSCRIPTION)} instead
     */
    @Deprecated
    public boolean visitStreamRemoveSubscription(SubscriptionProvider provider);

    /**
     * This method consumes available data for {@link MessageType#HISTORY_DATA} message from
     * the given data provider.
     * @return <code>false</code> if it had retrieved all available data and no data left,
     * <code>true</code> if more data remains to be consumed
     * @deprecated use {@link #visitData(DataProvider, MessageType) visitData(DataProvider provider, MessageType MessageType.HISTORY_DATA)} instead
     */
    @Deprecated
    public boolean visitHistoryData(DataProvider provider);

    /**
     * This method consumes available subscription for {@link MessageType#HISTORY_ADD_SUBSCRIPTION} message from
     * the given subscription provider.
     * @return <code>false</code> if it had retrieved all available subscription and nothing left,
     * <code>true</code> if more subscription remains to be consumed
     * @deprecated use {@link #visitSubscription(SubscriptionProvider, MessageType) visitSubscription(SubscriptionProvider provider, MessageType MessageType.HISTORY_ADD_SUBSCRIPTION)} instead
     */
    @Deprecated
    public boolean visitHistoryAddSubscription(SubscriptionProvider provider);

    /**
     * This method consumes available subscription for {@link MessageType#HISTORY_REMOVE_SUBSCRIPTION} message from
     * the given subscription provider.
     * @return <code>false</code> if it had retrieved all available subscription and nothing left,
     * <code>true</code> if more subscription remains to be consumed
     * @deprecated use {@link #visitSubscription(SubscriptionProvider, MessageType) visitSubscription(SubscriptionProvider provider, MessageType MessageType.HISTORY_REMOVE_SUBSCRIPTION)} instead
     */
    @Deprecated
    public boolean visitHistoryRemoveSubscription(SubscriptionProvider provider);

    /**
     * This method represents an extension point for QD API in order it could be
     * extended to handle any auxiliary messages transferred via QD.
     *
     * @param messageType integer number representing a type of the message.
     * @param messageBytes array containing message data.
     * @param offset position of the first byte of message data in {@code messageBytes} array.
     * @param length number of bytes starting from {@code offset} in {@code messageBytes} related to this message.
     * @return <tt>true</tt> if the whole message was not processed because the visitor is full
     *         and <tt>false</tt> if the message was successfully processed.
     */
    public boolean visitOtherMessage(int messageType, byte[] messageBytes, int offset, int length);
}
