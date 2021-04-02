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
package com.devexperts.qd.qtp.auth;

import com.devexperts.auth.AuthSession;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.qtp.AgentAdapter;
import com.devexperts.qd.qtp.AgentAdapterChannels;
import com.devexperts.qd.qtp.ChannelDescription;
import com.devexperts.qd.qtp.ChannelShaper;
import com.devexperts.util.TypedKey;

public class BasicChannelShaperFactory implements ChannelShapersFactory {

    public static final BasicChannelShaperFactory INSTANCE = new BasicChannelShaperFactory();

    public static final ChannelDescription[] ALL_DATA = {
        new ChannelDescription(QDContract.TICKER.toString()),
        new ChannelDescription(QDContract.HISTORY.toString()),
        new ChannelDescription(QDContract.STREAM.toString())
    };

    public static final TypedKey<ChannelDescription[]> CHANNEL_CONFIGURATION_KEY =
        new TypedKey<>();

    public static final TypedKey<AgentAdapter.Factory> FACTORY_KEY = new TypedKey<>();

    private BasicChannelShaperFactory() {}

    @Override
    public ChannelShaper[] createChannelShapers(AgentAdapter agentAdapter, AuthSession session)  {
        ChannelDescription[] configuration = session.variables().get(CHANNEL_CONFIGURATION_KEY);
        if (configuration == null)
            return null; // session was created by something other than BasicAuthRealm
        AgentAdapter.Factory factory = session.variables().get(FACTORY_KEY);
        AgentAdapterChannels adapter;
        if (factory != null)
            adapter = new AgentAdapterChannels(configuration, factory);
        else
            adapter = new AgentAdapterChannels(configuration, agentAdapter);
        return adapter.getNewShapers();
    }
}
