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

import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.ng.RecordFilter;

import java.util.concurrent.Executor;

/**
 * This class represents configuration of a single data channel within {@link AgentAdapter}.
 * It includes contract, collector, keep rejected flag, subscription filter, data filter, relative weight,
 * and aggregation period.
 * Each channel may specify executor to be used for subscription processing (i.e. filtering) -
 * it is required when subscription filtering is a heavy operation (e.g. requires database access).
 *
 * <p> Channel may change its parameters except for contract, subscription executor, and keep reject flag,
 * to new values anytime.
 * The changes of data filter, weight or aggregation period are immediately effective
 * and do not require re-creation of agent (i.e. no interruption of data stream happens).
 * The changes of collector or subscription filter require re-creation of agent which
 * result with brief data stream interruption. The re-creation of agent will happen
 * upon data or subscription message processing; it can be forced via invocation of
 * {@link AgentAdapter#updateChannel} method.
 */
public class ChannelShaper implements Cloneable {
    private final QDContract contract; // fixed on creation
    private final Executor subscriptionExecutor; // fixed on creation
    private final boolean keepRejected; // fixed on creation

    private volatile AgentChannel channel; // set only once after creation in "bind" method

    private volatile QDCollector collector;
    private volatile QDFilter subscriptionFilter = QDFilter.ANYTHING; // non-null
    private volatile RecordFilter dataFilter;
    private volatile int weight = 1;
    private volatile long aggregationPeriod;

    /**
     * Creates new channel shaper with specified contract and subscription executor.
     * This channel shaper does not keep subscription rejected by a filter.
     *
     * @param contract channel contract; may not be {@code null}
     * @param subscriptionExecutor subscription executor; may be {@code null}
     * @throws NullPointerException if {@code contract} is {@code null}
     */
    public ChannelShaper(QDContract contract, Executor subscriptionExecutor) {
        this(contract, subscriptionExecutor, false);
    }

    /**
     * Creates new channel shaper with specified contract, subscription executor,
     * and keep rejected flag.
     *
     * @param contract channel contract; may not be {@code null}
     * @param subscriptionExecutor subscription executor; may be {@code null}
     * @param keepRejected {@code true} when rejected subscription elements shall be kept for future changes of filter.
     * @throws NullPointerException if {@code contract} is {@code null}
     */
    public ChannelShaper(QDContract contract, Executor subscriptionExecutor, boolean keepRejected) {
        if (contract == null)
            throw new NullPointerException();
        this.contract = contract;
        this.subscriptionExecutor = subscriptionExecutor;
        this.keepRejected = keepRejected;
    }

    /**
     * Creates new channel shaper with specified parameters.
     * All initialization parameters except for collector may be {@code null}.
     *
     * @param subscriptionExecutor subscription executor; may be {@code null}
     * @param collector channel collector; may not be {@code null}
     * @param subscriptionFilter subscription filter; may be {@code null}
     * @param dataFilter data filter; may be {@code null}
     * @throws NullPointerException if {@code collector} is {@code null}
     * @deprecated Use {@link #ChannelShaper(QDContract, Executor)} and {@link #setCollector(QDCollector)},
     * {@link #setSubscriptionFilter(QDFilter)}, {@link #setDataFilter(RecordFilter)}
     */
    public ChannelShaper(Executor subscriptionExecutor, QDCollector collector,
        SubscriptionFilter subscriptionFilter, RecordFilter dataFilter)
    {
        this(collector.getContract(), subscriptionExecutor);
        setCollector(collector);
        setSubscriptionFilter(subscriptionFilter);
        setDataFilter(dataFilter);
    }

    /**
     * Binds this channel to the specified agent adapter.
     * @param channel agent channel.
     */
    synchronized void bind(AgentChannel channel) {
        if (this.channel != null)
            throw new IllegalStateException("Already bound");
        if (channel == null)
            throw new NullPointerException();
        this.channel = channel;
    }

    /**
     * Closes this channel shaper and releases its resources (stops tracking dynamic filters).
     */
    public void close() {}

    @Override
    public ChannelShaper clone()  {
        try {
            ChannelShaper clone = (ChannelShaper) super.clone();
            if (clone.channel != null)
                throw new IllegalStateException("Cannot clone bound channel");
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns contract of the channel.
     * <b>This property is fixed for a given channel.</b>
     *
     * @return channel contract
     */
    public QDContract getContract() {
        return contract;
    }

    /**
     * Returns {@code true} if there is an executor for subscription processing.
     * <b>This property is fixed for a given channel.</b>
     *
     * @return {@code true} if there is an executor for subscription processing.
     */
    public boolean hasSubscriptionExecutor() {
        return subscriptionExecutor != null;
    }

    /**
     * Returns executor for subscription processing used by the channel
     * or {@code null} if none specified.
     * <b>This property is fixed for a given channel.</b>
     *
     * @return subscription executor or {@code null}
     */
    public Executor getSubscriptionExecutor() {
        return subscriptionExecutor;
    }

    /**
     * Returns {@code true} when subscription rejected by this channel's filter is kept for
     * the case of future changes of subscription filter.
     * <b>This property is fixed for a given channel.</b>
     */
    public boolean isKeepRejected() {
        return keepRejected;
    }

    /**
     * Returns collector currently used by the channel.
     *
     * @return channel collector or {@code null}
     */
    public final QDCollector getCollector() {
        return collector;
    }

    /**
     * Sets collector for the channel. The contract of collector must match the contract of this channel.
     * The collector may be {@code null} in which case no agent will be created for the channel.
     * Change of collector will trigger re-creation of agent.
     *
     * @param collector channel collector; may be {@code null}
     */
    public void setCollector(QDCollector collector) {
        if (collector != null && collector.getContract() != contract)
            throw new IllegalArgumentException("Wrong contract");
        this.collector = collector;
        reconfigureIfNeeded();
    }

    /**
     * Returns subscription filter currently used by the channel.
     *
     * @return subscription filter currently used by the channel (non-null).
     */
    public final QDFilter getSubscriptionFilter() {
        return subscriptionFilter;
    }

    /**
     * Sets subscription filter for the channel.
     * The subscription filter may be {@code null} in which case all subscription will be accepted.
     * Change of subscription filter will trigger re-creation of agent.
     *
     * @param subscriptionFilter subscription filter; may be {@code null}
     * @deprecated Use {@link #setSubscriptionFilter(QDFilter)}
     *   that accepts non-null {@link QDFilter} instance which replaces legacy {@link SubscriptionFilter}.
     */
    public void setSubscriptionFilter(SubscriptionFilter subscriptionFilter) {
        setSubscriptionFilter(QDFilter.fromFilter(subscriptionFilter, collector == null ? null : collector.getScheme()));
    }

    /**
     * Sets subscription filter for the channel.
     * Use {@link QDFilter#ANYTHING} to accept any subscription.
     * Change of subscription filter will trigger re-creation of agent.
     *
     * @param subscriptionFilter subscription filter;
     *              may be {@code null} for backwards-compatibility (it is replaced with {@link QDFilter#ANYTHING})
     */
    public void setSubscriptionFilter(QDFilter subscriptionFilter) {
        if (subscriptionFilter == null)
            subscriptionFilter = QDFilter.ANYTHING;
        boolean updated = false;
        synchronized (this) {
            if (this.subscriptionFilter != subscriptionFilter) {
                this.subscriptionFilter = subscriptionFilter;
                updated = true;
            }
        }
        if (updated)
            reconfigureIfNeeded();
    }

    /**
     * Returns data filter currently used by the channel.
     *
     * @return data filter or {@code null}
     */
    public RecordFilter getDataFilter() {
        return dataFilter;
    }

    /**
     * Sets data filter for the channel.
     * The data filter may be {@code null} in which case all data will be accepted.
     * Change of data filter is immediate and will not trigger re-creation of agent.
     *
     * @param dataFilter data filter; may be {@code null}
     */
    public void setDataFilter(RecordFilter dataFilter) {
        this.dataFilter = dataFilter;
        reconfigureIfNeeded();
    }

    /**
     * Returns relative weight of this channel data within the adapter for shaping.
     *
     * @return relative weight of this channel data
     */
    public int getWeight() {
        return weight;
    }

    /**
     * Sets relative weight of this channel data within the adapter for shaping.
     * The weight must be between 1 and 100 inclusive.
     * Change of weight is immediate and will not trigger re-creation of agent.
     *
     * @param weight relative weight
     * @throws IllegalArgumentException if weight is less than 1 or greater than 100.
     */
    public void setWeight(int weight) {
        if (weight < 1 || weight > 100)
            throw new IllegalArgumentException("Weight is out of limits: " + weight);
        this.weight = weight;
        // no need to reconfigure on changing weight -- it is not part of configuration, but is used during
        // data retrieval only to compute quotas
    }

    /**
     * Returns aggregation period in milliseconds for this channel data.
     *
     * @return aggregation period in milliseconds
     */
    public long getAggregationPeriod() {
        return aggregationPeriod;
    }

    /**
     * Sets aggregation period for this channel data.
     * The aggregation period must be non-negative and must not exceed 24 hours.
     * Change of aggregation period is immediate and will not trigger re-creation of agent.
     *
     * @param aggregationPeriod in milliseconds
     * @throws IllegalArgumentException if aggregation period is negative or greater than 24 hours.
     */
    public void setAggregationPeriod(long aggregationPeriod) {
        if (aggregationPeriod < 0 || aggregationPeriod > 24 * 3600 * 1000)
            throw new IllegalArgumentException("Aggregation period is out of limits: " + aggregationPeriod);
        this.aggregationPeriod = aggregationPeriod;
        reconfigureIfNeeded();
    }

    private void reconfigureIfNeeded() {
        AgentChannel channel = this.channel; // atomic read
        if (channel != null)
            channel.reconfigureIfNeeded();
    }
}
