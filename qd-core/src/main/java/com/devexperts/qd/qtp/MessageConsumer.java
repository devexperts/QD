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

import com.devexperts.io.BufferedInput;
import com.devexperts.qd.DataIterator;
import com.devexperts.qd.SubscriptionIterator;

/**
 * The <code>MessageConsumer</code> processes incoming QTP messages.
 * It shall be implemented by an agent of some entity to be used by standard QTP connectors.
 * Its methods are invoked by incoming events, usually from threads allocated internally
 * by used QTP connector.
 * <p>
 * Implementations of this inteface shall extend {@link com.devexperts.qd.qtp.MessageConsumerAdapter}.
 */
public interface MessageConsumer {

    // ========== Special Cases and Error Handling ==========

    public void handleCorruptedStream();

    public void handleCorruptedMessage(int messageTypeId);

    public void handleUnknownMessage(int messageTypeId);

    // ========== Incoming Message Processing ==========

    /**
     * Processes incoming protocol descriptor and returns <code>true</code> if this message
     * consumer understands the protocol that remote peer intends to use.
     */
    public void processDescribeProtocol(ProtocolDescriptor desc, boolean logging);

    public void processHeartbeat(HeartbeatPayload heartbeatPayload);

    public void processTickerData(DataIterator iterator);

    public void processTickerAddSubscription(SubscriptionIterator iterator);

    public void processTickerRemoveSubscription(SubscriptionIterator iterator);

    public void processStreamData(DataIterator iterator);

    public void processStreamAddSubscription(SubscriptionIterator iterator);

    public void processStreamRemoveSubscription(SubscriptionIterator iterator);

    public void processHistoryData(DataIterator iterator);

    public void processHistoryAddSubscription(SubscriptionIterator iterator);

    public void processHistoryRemoveSubscription(SubscriptionIterator iterator);

    /**
     * @deprecated Override and use {@link MessageConsumerAdapter#processOtherMessage(int, BufferedInput, int)}
     */
    public void processOtherMessage(int messageTypeId, byte[] bytes, int ofs, int len);

}
