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
package com.devexperts.qd.tools;

import com.devexperts.qd.qtp.DistributorAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;

import java.util.List;

/**
 * Consumer side of NetTest tool.
 * It connects each of its collector to an ingoing address on distributor-side and
 * to a {@link NetTestConsumerAgentThread} (which counts received records number)
 * on agent-side.
 *
 * @see NetTestConsumerAgentThread
 * @see NetTestSide
 */
class NetTestConsumerSide extends NetTestSide {

    NetTestConsumerSide(NetTestConfig config) {
        super(config);
    }

    @Override
    protected void createDistributor(QDEndpoint endpoint, int index) {
        List<MessageConnector> connectors = MessageConnectors.createMessageConnectors(
            new DistributorAdapter.Factory(endpoint, null),
            config.address, endpoint.getRootStats()
        );
        endpoint.addConnectors(connectors);
        this.connectors.addAll(connectors);
    }

    @Override
    protected void createAgent(QDEndpoint endpoint, int index) {
        NetTestConsumerAgentThread agentThread = new NetTestConsumerAgentThread(index, this, endpoint);
        agentThread.start();
        threads.add(agentThread);
    }

}
