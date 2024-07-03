/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd;

import com.devexperts.qd.ng.RecordConsumer;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStatsContainer;

/**
 * The {@code QDDistributor} represents an individual data provider in the {@link QDCollector}.
 * It is responsible for tracking state of the provider in the collector,
 * including its total subscription, and to provide access point for that provider.
 */
public interface QDDistributor extends DataConsumer, RecordConsumer, QDStatsContainer {

    /**
     * Returns subscription provider that is used to accumulate added subscription.
     * On the first invocation of this method all the internal (potentially large) data structures are initialized
     * and the queue of added subscriptions is maintained further on. Don't invoke this method if you don't have to.
     *
     * @deprecated Use {@link #getAddedRecordProvider()}
     */
    public SubscriptionProvider getAddedSubscriptionProvider();

    /**
     * Returns record provider that is used to accumulate added subscription.
     * On the first invocation of this method all the internal (potentially large) data structures are initialized
     * and the queue of added subscriptions is maintained further on. Don't invoke this method if you don't have to.
     */
    public RecordProvider getAddedRecordProvider();

    /**
     * Returns subscription provider that is used to accumulate removed subscription.
     * On the first invocation of this method all the internal (potentially large) data structures are initialized
     * and the queue of removed subscriptions is maintained further on. Don't invoke this method if you don't have to.
     *
     * @deprecated Use {@link #getRemovedRecordProvider()}
     */
    public SubscriptionProvider getRemovedSubscriptionProvider();

    /**
     * Returns subscription provider that is used to accumulate removed subscription.
     * On the first invocation of this method all the internal (potentially large) data structures are initialized
     * and the queue of removed subscriptions is maintained further on. Don't invoke this method if you don't have to.
     */
    public RecordProvider getRemovedRecordProvider();

    /**
     * Closes this distributor and releases allocated resources in its {@link QDCollector}.
     * Closed distributor can not be activated again and shall not be used anymore.
     *
     * <p>The behavior of closed distributor with respect to different actions is undefined, but is guaranteed
     * to be safe, that is, it will not produce any exceptions or disruptive behavior. In particular, repeated
     * calls to {@code close} will not produce any further changes to its state.
     * Methods like {@link #processData(DataIterator) processData} may continue to work or may result in no action
     * depending on implementation. Subscription retrieval may continue to work or may retrieve no subscription.
     */
    public void close();

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #process(RecordSource)}.
     */
    public void processData(DataIterator iterator);

    /**
     * Processes all data from specified record source.
     * @param source the record source.
     */
    public void process(RecordSource source);

    /**
     * Builder for collector distributors.
     * Instances of this class are immutable, withXXX method creates new instances if needed.
     */
    public interface Builder {
        public QDFilter getFilter();
        public QDFilter getStripe();
        public String getKeyProperties();
        public Builder withFilter(QDFilter filter);
        public Builder withStripe(QDFilter stripeFilter);
        public Builder withKeyProperties (String keyProperties);
        public QDDistributor build();
    }
}
