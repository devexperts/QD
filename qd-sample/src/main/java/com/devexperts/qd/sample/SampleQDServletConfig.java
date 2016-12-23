/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.qd.sample;

import java.util.EnumSet;

import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.qtp.*;
import com.devexperts.qd.qtp.http.QDServletConfig;
import com.devexperts.qd.stats.QDStats;

public class SampleQDServletConfig extends QDServletConfig {
    private final QDEndpoint endpoint;

    {
        DataScheme scheme = SampleScheme.getInstance();
        endpoint = QDEndpoint.newBuilder()
            .withName("httpServer")
            .withScheme(scheme)
            .withContracts(EnumSet.of(QDContract.TICKER, QDContract.STREAM, QDContract.HISTORY))
            .withProperties(Sample.getMonitoringProps(1237))
            .build();
        endpoint.getStream().setEnableWildcards(true);

        Thread generator = new SampleGeneratorThread(endpoint);
        generator.setDaemon(true);
        generator.start();
    }

    @Override
    public MessageAdapter.Factory getMessageAdapterFactory() {
        return new AgentAdapter.Factory(endpoint, null);
    }

    @Override
    public QDStats getStats() {
        return endpoint.getRootStats().create(QDStats.SType.QD_SERVLET);
    }
}
