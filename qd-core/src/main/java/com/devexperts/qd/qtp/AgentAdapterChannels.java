/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp;

import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.qtp.auth.BasicAuthRealmFactory;
import com.devexperts.qd.util.QDConfig;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.TimePeriod;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for "channels" parameter of {@link AgentAdapter.Factory}.
 * Channels configuration string is a parenthesis-separated string of channel descriptors.
 * For example:
 * {@code (opt&ticker@1s)(!opt&ticker@.1s)(stream[weight=7])(history)} defines four channels,
 * with stream having weight 7 of total weight 10 (default weight of each channel is 1).
 *
 * @see ChannelDescription
 */
public class AgentAdapterChannels {
    private final ChannelDescription[] channels;
    private final List<DynamicChannelShaper> shapers = new ArrayList<>();

    public AgentAdapterChannels(ChannelDescription[] channels, AgentAdapter adapter) throws InvalidFormatException {
        this(channels, adapter.getAgentFactory());
    }

    public AgentAdapterChannels(ChannelDescription[] channels, AgentAdapter.Factory factory) throws InvalidFormatException {
        this.channels = channels;
        QDFilter factoryFilter = factory.getFilter();
        if (channels.length != 0) {
            // explicit channels configuration
            for (ChannelDescription channel :channels) {
                TimePeriod aggregationPeriod = TimePeriod.valueOf(
                    QDConfig.getProperty(channel.properties, "aggregationPeriod"),
                    channel.aggregationPeriod != null ? channel.aggregationPeriod : null);
                QDFilter filter = channel.filterStr == null ? factoryFilter : CompositeFilters.makeAnd(factoryFilter,
                    CompositeFilters.valueOf(channel.filterStr, factory.getScheme()));
                boolean found = false;
                for (QDCollector collector : factory.getCollectors()) {
                    if (collector != null && collector.getContract() == channel.contract) {
                        DynamicChannelShaper shaper = new DynamicChannelShaper(channel.contract,
                            factory.getOrCreateSubscriptionExecutor(), filter);
                        shaper.setCollector(collector);
                        shaper.setDefaultAggregationPeriod(aggregationPeriod);
                        if (aggregationPeriod != null)
                            shaper.setMinAggregationPeriod(aggregationPeriod);
                        QDConfig.setProperties(shaper, channel.properties);
                        shapers.add(shaper);
                        found = true;
                        break;
                    }
                }
                if (!found)
                    throw new InvalidFormatException("Missing contract: " + channel.contract);
            }
        } else {
            // implicit channels configuration
            for (QDCollector collector : factory.getCollectors()) {
                if (collector != null) {
                    DynamicChannelShaper shaper = new DynamicChannelShaper(collector.getContract(),
                        factory.getOrCreateSubscriptionExecutor(), factoryFilter);
                    shaper.setCollector(collector);
                    shapers.add(shaper);
                }
            }
        }
    }

    AgentAdapterChannels(String channels, AgentAdapter.Factory factory) {
        this(BasicAuthRealmFactory.parseAgentChannelDescription(channels).toArray(new ChannelDescription[]{}), factory);
    }

    public ChannelShaper[] getNewShapers() {
        DynamicChannelShaper[] result = shapers.toArray(new DynamicChannelShaper[shapers.size()]);
        for (int i = 0; i < result.length; i++) {
            result[i].updateFilter();
            result[i] = result[i].clone();
        }
        return result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (ChannelDescription channel : channels) {
            sb.append('(').append(channel).append(')');
        }
        return sb.toString();
    }

}
