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
package com.devexperts.qd.qtp;

import com.devexperts.qd.DataProvider;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordSource;

import java.util.Collection;

/**
 * The AgentChannels class represents a collection of agent channels, each associated with a QDAgent.
 * The main methods of this class, such as {@link #processSubscription}, {@link #retrieveData},
 * and {@link #nextRetrieveTime}, automatically operate on the collection of channels.
 */
public class AgentChannels {

    public static void clearDataInBuffer(RecordBuffer buf, boolean keepTime) {
        RecordCursor cur;
        while ((cur = buf.writeNext()) != null) {
            if (keepTime) {
                cur.clearDataButTime();
            } else {
                cur.clearData();
            }
        }
    }

    /**
     * The Owner interface represents the owner of the agent channels. The agent inside the channel will notify
     * the channel owner by calling the {@link #recordsAvailable()} method. Other methods are necessary for creating
     * and configuring the agent inside the channel.
     */
    public static interface Owner {

        /**
         * This method is used internally by agent adapter to create agent for the corresponding
         * collector, filter when the corresponding subscription arrives for a first time.
         * This method is called while holding <code>this</code> adapter's lock.
         * This implementation returns <code>collector.createAgent(filter, keyProperties)</code>.
         * This method may be overriden to create agent with other filter, otherwise customize the agent
         * that is being created, or to keep track of created agents.
         *
         * @param collector collector to create agent for
         * @param filter    subscription filter for the agent
         * @return newly created agent
         */
        public QDAgent createAgent(QDCollector collector, QDFilter filter);

        /**
         * This method is used to create void agent just to keep subscription.
         *
         * @param contract contract to create agent for
         * @return newly created agent
         */
        public QDAgent createVoidAgent(QDContract contract);

        /**
         * When the channel configuration is updated, it will be summarized by logical AND with the filter that is specified
         * in shaper.
         *
         * @param contract - contract specified in the shaper
         * @return filter to be added to the filter that is specified in shaper
         */
        public QDFilter getPeerFilter(QDContract contract);

        /**
         * Notifies the owner when records are available.
         * <p>
         * This method is called by AgentChannel to notify the owner that records are available. It is important to note
         * that AgentChannel is designed not to call this method unnecessarily. If it is called, it expects that the
         * data will be retrieved from the collector using the {@link #retrieveData()}
         * method.
         * <p>
         * If the aggregationPeriod is set, this method will be called only the first time. Subsequently, you must
         * manually calculate the next retrieval time using {@link #nextRetrieveTime(long)} after each data retrieval.
         * If there has been no data available for an extended period, {@link #nextRetrieveTime(long)} will return
         * {@link Long#MAX_VALUE}, indicating that you must wait for the recordsAvailable notification to occur again.
         */
        public void recordsAvailable();

        /**
         * Retrieves accumulated data from data provider.
         * Returns <code>true</code> if some data still remains in the provider
         * or <code>false</code> if all accumulated data were retrieved.
         */
        public boolean retrieveData(DataProvider dataProvider, QDContract contract);
    }

    private final Owner owner;
    final ChannelShaper[] shapers;
    private final AgentChannel[] channels; // initially all elements are null, filled (assigned) lazily

    public AgentChannels(Owner owner, Collection<ChannelShaper> shapers) {
        this.owner = owner;
        this.shapers = shapers.toArray(new ChannelShaper[0]);
        channels = new AgentChannel[this.shapers.length];
    }

    /**
     * Processes subscription.
     *
     * @param sub               the subscription
     * @param contract          the QDContract
     * @param isSubscriptionAdd flag indicating whether the subscription is added or removed
     * @return true if the contract exists, false otherwise
     */
    public boolean processSubscription(RecordSource sub, QDContract contract, boolean isSubscriptionAdd) {
        boolean hasContract = false;
        long initialPosition = sub.getPosition();
        for (int i = 0; i < shapers.length; i++) {
            ChannelShaper shaper = shapers[i];
            if (shaper.getContract() != contract)
                continue;
            hasContract = true;
            AgentChannel channel = getOrCreateChannelAt(i);
            sub.setPosition(initialPosition);
            channel.processSubscription(sub, isSubscriptionAdd);
        }
        return hasContract;
    }

    /**
     * Returns the next time when the {@link #retrieveData} method should be called.
     *
     * @param currentTime the current time in milliseconds obtained from {@link System#currentTimeMillis()}
     * @return the next scheduled time for invoking {@link #retrieveData};
     *         returns 0 if data should be retrieved immediately using {@link #retrieveData},
     *         returns {@link Long#MAX_VALUE} if waiting for {@link Owner#recordsAvailable()} to be triggered.
     */
    public long nextRetrieveTime(long currentTime) {
        long time = Long.MAX_VALUE;
        for (AgentChannel channel : channels) {
            if (channel == null)
                continue; // not initialized yet (no subscription)
            long retrieveTime = channel.nextRetrieveTime(currentTime);
            if (retrieveTime == 0)
                return 0;
            time = Math.min(time, retrieveTime);
        }
        return time;
    }

    /**
     * Retrieves accumulated messages using the provided RetrieveFromProvider function.
     *
     * @return true if some messages still remain in the provider, false otherwise
     */
    public boolean retrieveData() {
        for (int iterations = channels.length; --iterations >= 0;) {
            long currentTime = System.currentTimeMillis();
            double minDistance = Double.POSITIVE_INFINITY; // how much weight should be distributed to allow channel with data to achieve quota of 1
            AgentChannel dueChannel = null; // first channel to achieve quota of 1
            for (AgentChannel channel : channels) {
                if (channel == null)
                    continue; // not initialized yet (no subscription)
                if (channel.hasSnapshotOrDataForNow(currentTime)) {
                    if (channel.quota >= 1) {
                        minDistance = 0;
                        dueChannel = channel;
                        break;
                    }
                    double distance = (1 - channel.quota) / channel.shaper.getWeight();
                    if (distance < minDistance) {
                        minDistance = distance;
                        dueChannel = channel;
                    }
                }
            }
            if (dueChannel == null)
                return false; // no one has any data

            if (minDistance > 0) {
                for (AgentChannel channel : channels) { // distribute more quota
                    if (channel == null)
                        continue; // not initialized yet (no subscription)
                    channel.quota += minDistance * channel.shaper.getWeight();
                    if (channel.quota >= 1) // can happen for channels which has no data for now
                        channel.quota = 1;
                }
            }

            dueChannel.quota = 0;
            if (dueChannel.retrieveSnapshotOrData(currentTime))
                return true;
        }
        return true;
    }

    public void close() {
        for (AgentChannel channel : channels) {
            if (channel != null)
                channel.close();
        }
    }

    public void closeAndExamineDataBySubscription(RecordBuffer buf) {
        for (AgentChannel channel : channels) {
            if (channel != null) {
                channel.closeAndExamineDataBySubscription(buf);
                clearDataInBuffer(buf, channel.shaper.getContract() == QDContract.HISTORY);
            }
        }
    }

    String getSymbol(char[] chars, int offset, int length) {
        QDCollector prevCollector = null;
        for (AgentChannel channel : channels) {
            if (channel == null)
                continue; // not initialized yet (no subscription)
            QDCollector collector = channel.shaper.getCollector();
            if (collector == prevCollector || collector == null)
                continue;
            String result = collector.getSymbol(chars, offset, length);
            if (result != null)
                return result;
            prevCollector = collector;
        }
        return null;
    }

    QDFilter combinedFilter(QDContract contract) {
        QDFilter combinedFilter = QDFilter.NOTHING;
        for (ChannelShaper shaper : shapers) {
            if (shaper.getContract() == contract) {
                // compute combined filter for all channels for this contract
                combinedFilter = CompositeFilters.makeOr(combinedFilter, shaper.getSubscriptionFilter().toStableFilter());
            }
        }
        return combinedFilter;
    }

    private AgentChannel getOrCreateChannelAt(int i) {
        AgentChannel channel = channels[i];
        if (channel == null) {
            ChannelShaper shaper = shapers[i];
            channel = new AgentChannel(owner, shaper);
            channels[i] = channel;
        }
        return channel;
    }
}
