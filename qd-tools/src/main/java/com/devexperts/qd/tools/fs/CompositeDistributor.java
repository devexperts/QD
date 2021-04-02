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
package com.devexperts.qd.tools.fs;

import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.impl.AbstractDistributor;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;

/**
 * This QDDistributor consist of two distributors.
 * It provides subscription of the first distributor,
 * but passes received data to the other one.
 */
class CompositeDistributor extends AbstractDistributor {
    private final QDDistributor subscriptionProviderDistributor;
    private final QDDistributor dataConsumerDistributor;

    CompositeDistributor(QDDistributor subscriptionProviderDistributor, QDDistributor dataConsumerDistributor) {
        this.subscriptionProviderDistributor = subscriptionProviderDistributor;
        this.dataConsumerDistributor = dataConsumerDistributor;
    }

    public RecordProvider getAddedRecordProvider() {
        return subscriptionProviderDistributor.getAddedRecordProvider();
    }

    public RecordProvider getRemovedRecordProvider() {
        return subscriptionProviderDistributor.getRemovedRecordProvider();
    }

    public void close() {
        subscriptionProviderDistributor.close();
        dataConsumerDistributor.close();
    }

    public QDStats getStats() {
        return subscriptionProviderDistributor.getStats();
    }

    public void process(RecordSource source) {
        dataConsumerDistributor.process(source);
    }
}
