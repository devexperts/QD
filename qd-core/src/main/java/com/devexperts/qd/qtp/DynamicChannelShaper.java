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

import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;

import java.util.concurrent.Executor;

public class DynamicChannelShaper extends ChannelShaper implements QDFilter.UpdateListener {
    public DynamicChannelShaper(QDContract contract, Executor subscriptionExecutor, QDFilter subscriptionFilter) {
        super(contract, subscriptionExecutor, subscriptionFilter.isDynamic());
        super.setSubscriptionFilter(subscriptionFilter);
    }

    @Override
    public DynamicChannelShaper clone() {
        return (DynamicChannelShaper) super.clone();
    }

    @Override
    public void setSubscriptionFilter(QDFilter subscriptionFilter) {
        throw new UnsupportedOperationException();
    }

    public void updateFilter() {
        filterUpdated(getSubscriptionFilter());
    }

    @Override
    public void filterUpdated(QDFilter filter) {
        super.setSubscriptionFilter(filter.getUpdatedFilter());
    }

    @Override
    void bind(AgentChannel channel) {
        // add permanent listener for all future updates first(!)
        getSubscriptionFilter().getUpdated().addUpdateListener(this);
        // then make sure we have an up-to-date filter (before binding, so that we don't have to reconfigure)
        updateFilter();
        super.bind(channel);
    }

    @Override
    public void close() {
        getSubscriptionFilter().getUpdated().removeUpdateListener(this);
    }

}
