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

import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.MessageConnector;
import com.devexperts.qd.qtp.MessageConnectors;
import com.devexperts.qd.qtp.QDEndpoint;

import java.util.List;

/**
 * Producer side of NetTest tool.
 * It connects each of its collector to an outgoing address on agent-side and
 * to a {@link NetTestProducerDistributorThread} (which continually generates
 * random records) on distributor-side.
 *
 * @see NetTestProducerDistributorThread
 * @see NetTestSide
 */
class NetTestProducerSide extends NetTestSide {

    NetTestProducerSide(NetTestConfig config) {
        super(config);
    }

    @Override
    protected void createDistributor(QDEndpoint endpoint, int index) {
        NetTestProducerDistributorThread distributorThread = new NetTestProducerDistributorThread(index, this, endpoint);
        distributorThread.start();
        threads.add(distributorThread);
    }

    @Override
    protected void createAgent(QDEndpoint endpoint, int index) {
        List<MessageConnector> connectors = MessageConnectors.createMessageConnectors(
            new AgentAdapter.Factory(endpoint, null),
            config.address, endpoint.getRootStats()
        );
        endpoint.addConnectors(connectors);
        this.connectors.addAll(connectors);
    }

}
