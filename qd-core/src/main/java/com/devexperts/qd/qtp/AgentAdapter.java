/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp;

import java.util.*;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;

import com.devexperts.auth.AuthSession;
import com.devexperts.connector.proto.Configurable;
import com.devexperts.qd.*;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.qtp.auth.BasicChannelShaperFactory;
import com.devexperts.qd.qtp.auth.ChannelShapersFactory;
import com.devexperts.qd.spi.QDFilterContext;
import com.devexperts.qd.spi.QDFilterFactory;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.LegacyAdapter;
import com.devexperts.services.Services;
import com.devexperts.util.LoggedThreadPoolExecutor;
import com.devexperts.util.TimePeriod;

/**
 * The <code>AgentAdapter</code> adapts agent side of QD to message API.
 * The agent side of an QD is an aggregation of {@link QDAgent agents} for all its data sources.
 * Thus, the <code>AgentAdapter</code> can be used to represent an outside data consumer
 * in the specific QD.
 *
 * if you are a QD publisher - use this Adapter
 */
public class AgentAdapter extends MessageAdapter {
    private static final QDContract[] QD_CONTRACTS = QDContract.values();
    private static final int N_CONTRACTS = QD_CONTRACTS.length;
    private static final Iterable<ChannelShapersFactory> CHANNEL_SHAPERS_FACTORIES = Services.createServices(ChannelShapersFactory.class, null);

    /**
     * The factory for agent side of an QD.
     */
    public static class Factory extends MessageAdapter.AbstractFactory {
        /**
         * Aggregation period is a single time period that serves as a default aggregation period
         * for all channels, unless something else is explicitly specified.
         */
        private TimePeriod aggregationPeriod = TimePeriod.valueOf(0);

        /**
         * Channels configuration.
         * @see AgentAdapterChannels
         */
        private AgentAdapterChannels channels;

        /**
         * Size of the pool for subscription-handling threads
         * (zero default - does not use separate thread pool for subscription).
         */
        private int subscriptionThreads;

        /**
         * Explicit subscription executor.
         */
        private Executor subscriptionExecutor;

        public Factory(QDTicker ticker, QDStream stream, QDHistory history, SubscriptionFilter filter) {
            super(ticker, stream, history, filter);
        }

        public Factory(QDEndpoint endpoint, SubscriptionFilter filter) {
            super(endpoint, filter);
        }

        public Factory(QDTicker ticker) {
            this(ticker, null, null, null);
        }

        public Factory(QDStream stream) {
            this(null, stream, null, null);
        }

        public Factory(QDHistory history) {
            this(null, null, history, null);
        }

        public Factory(QDCollector collector) {
            this(collector instanceof QDTicker ? (QDTicker) collector : null,
                collector instanceof QDStream ? (QDStream) collector : null,
                collector instanceof QDHistory ? (QDHistory) collector : null,
                null);
            channels = new AgentAdapterChannels("", this);
        }

        public synchronized TimePeriod getAggregationPeriod() {
            return aggregationPeriod;
        }

        @Configurable(description = "default aggregation period for all channels")
        public synchronized void setAggregationPeriod(TimePeriod aggregationPeriod) {
            if (aggregationPeriod.equals(this.aggregationPeriod)) // also throws NPE
                return;
            if (aggregationPeriod.getTime() < 0)
                throw new IllegalArgumentException("cannot be negative");
            this.aggregationPeriod = aggregationPeriod;
            rebuildChannels();
        }

        public synchronized String getChannels() {
            return channels == null ? "" : channels.toString();
        }

        @Configurable(description = "channels configuration string")
        public synchronized void setChannels(String channels) {
            if (channels == null)
                throw new NullPointerException();
            // Immediately parse agent channels configuration to complain on any problems
            this.channels = new AgentAdapterChannels(channels, this);
        }

        public synchronized int getSubscriptionThreads() {
            return subscriptionThreads;
        }

        @Configurable(description = "size of the pool for subscription-handling threads\n" +
            "(zero default - does not use separate thread pool for subscription)")
        public synchronized void setSubscriptionThreads(int subscriptionThreads) {
            if (subscriptionThreads == this.subscriptionThreads)
                return;
            if (subscriptionThreads < 0)
                throw new IllegalArgumentException("cannot be negative");
            this.subscriptionThreads = subscriptionThreads;
            rebuildChannels();
        }

        public synchronized Executor getSubscriptionExecutor() {
            return subscriptionExecutor;
        }

        @Configurable(description = "explicit subscription executor")
        public synchronized void setSubscriptionExecutor(Executor subscriptionExecutor) {
            this.subscriptionExecutor = subscriptionExecutor;
            rebuildChannels();
        }

        synchronized Executor getOrCreateSubscriptionExecutor() {
            if (subscriptionExecutor != null)
                return subscriptionExecutor;
            if (subscriptionThreads > 0)
                subscriptionExecutor = new LoggedThreadPoolExecutor(subscriptionThreads, this + "-Subscription", QDLog.log);
            return subscriptionExecutor;
        }

        @Nonnull
        AgentAdapterChannels getAgentAdapterChannels() {
            AgentAdapterChannels channels;
            synchronized (this) {
                if (this.channels == null)
                    this.channels = new AgentAdapterChannels("", this); // create default implicit configuration if not set explicitly
                channels = this.channels;
            }
            return channels;
        }

        private void rebuildChannels() {
            if (channels != null) // rebuild agent channels configuration
                channels = new AgentAdapterChannels(channels.toString(), this);
        }

        @Override
        public MessageAdapter createAdapter(QDStats stats) {
            AgentAdapter adapter = new AgentAdapter(endpoint, getCommonScheme(ticker, stream, history), getFilter(), stats);
            adapter.setAgentFactory(this);
            return adapter;
        }
    }

    // ------------------------- instance fields -------------------------

    private final DataScheme scheme;
    private final QDFilter filter; // @NotNull
    private AgentAdapter.Factory factory;

    final QDFilter[] peerFilter = new QDFilter[N_CONTRACTS]; // filters received from remote peer in DESCRIBE PROTOCOL message

    private ChannelShaper[] shapers; // effectively final, filled by initialize method
    private AgentChannel[] channels; // effective final, initially all null values, allocated by initialize method, filled (assigned) lazily

    // ------------------------- constructors -------------------------

    /**
     * Creates new agent adapter for specified endpoints, ticker, stream, history, filter and stats.
     * Any of the endpoint, collectors and/or filter may be {@code null}.
     *
     * The resulting adapter will be {@link #initialize(ChannelShaper[]) initiailized}.
     * It will use equal weight of 1 and no aggregation for all collectors.
     */
    public AgentAdapter(QDEndpoint endpoint, QDTicker ticker, QDStream stream, QDHistory history, SubscriptionFilter filter, QDStats stats) {
        super(endpoint, stats);
        this.scheme = getCommonScheme(ticker, stream, history);
        this.filter = QDFilter.fromFilter(filter, scheme);
        ArrayList<ChannelShaper> shapers = new ArrayList<>();
        if (ticker != null)
            shapers.add(newDynamicShaper(ticker));
        if (stream != null)
            shapers.add(newDynamicShaper(stream));
        if (history != null)
            shapers.add(newDynamicShaper(history));
        initialize(shapers.toArray(new ChannelShaper[shapers.size()]));
    }

    /**
     * Creates new agent adapter for specified ticker, stream, history, filter and stats.
     * Any of the collectors and/or filter may be {@code null}.
     *
     * The resulting adapter will be {@link #initialize(ChannelShaper[]) initiailized}.
     * It will use equal weight of 1 and no aggregation for all collectors.
     */
    public AgentAdapter(QDTicker ticker, QDStream stream, QDHistory history, SubscriptionFilter filter, QDStats stats) {
        this(null, ticker, stream, history, filter, stats);
    }

    /**
     * Creates new agent adapter for specified scheme and stats.
     *
     * Adapter created by this constructor must be {@link #initialize(ChannelShaper[]) initialized}
     * with shapers before being used.
     *
     * @param scheme data scheme
     * @param stats stats
     */
    public AgentAdapter(DataScheme scheme, QDStats stats) {
        this(null, scheme, QDFilter.ANYTHING, stats);
    }

    private AgentAdapter(QDEndpoint endpoint, DataScheme scheme, QDFilter filter, QDStats stats) {
        super(endpoint, stats);
        if (scheme == null)
            throw new NullPointerException();
        this.scheme = scheme;
        this.filter = filter;
    }

    private DynamicChannelShaper newDynamicShaper(QDCollector collector) {
        DynamicChannelShaper shaper = new DynamicChannelShaper(collector.getContract(), null, this.filter);
        shaper.setCollector(collector);
        return shaper;
    }

    // ------------------------- instance methods -------------------------

    /**
     * Initializes the adapter with specified channel shapers. This method must be called exactly once
     * before the adapter is used (unless the adapter is created with
     * {@link #AgentAdapter(QDTicker, QDStream, QDHistory, SubscriptionFilter, QDStats)} constructor in which case
     * it ia already initialized).
     * All channels are bound to this agent and cannot be used in other agent.
     *
     * @param shapers shaping configuration for adapter channels
     * @return this agent adapter (in order to allow chained notation)
     */
    public synchronized AgentAdapter initialize(ChannelShaper... shapers) {
        if (this.shapers != null)
            throw new IllegalArgumentException("Already initialized");
        this.shapers = shapers.clone();
        channels = new AgentChannel[shapers.length];
        return this;
    }

    /**
     * This method is used internally by agent adapter to create agent for the corresponding
     * collector, filter, and keyProperties when the corresponding subscription arrives for a first time.
     * This method is called while holding <code>this</code> adapter's lock.
     * This implementation returns <code>collector.createAgent(filter, keyProperties)</code>.
     * This method may be overriden to create agent with other filter, otherwise customize the agent
     * that is being created, or to keep track of created agents.
     *
     * @param collector collector to create agent for
     * @param filter subscription filter for the agent
     * @param keyProperties key properties for stats
     * @return newly created agent
     */
    protected QDAgent createAgent(QDCollector collector, SubscriptionFilter filter, String keyProperties) {
        return createAgentBuilder(collector, filter, keyProperties).build();
    }

    protected QDAgent.Builder createAgentBuilder(QDCollector collector, SubscriptionFilter filter, String keyProperties) {
        return collector.agentBuilder()
            .withFilter(QDFilter.fromFilter(filter, scheme))
            .withKeyProperties(keyProperties)
            .withOptSet(getRemoteOptSet());
    }

    public String toString() {
        return super.toString() + (filter == QDFilter.ANYTHING  ? "" : "[" + filter + "]");
    }

    @Override
    public DataScheme getScheme() {
        return scheme;
    }

    @Override
    public String getSymbol(char[] chars, int offset, int length) {
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

    /**
     * @deprecated No need to use this method. It does nothing. Channels are automatically update on change
     * of their parameters.
     */
    public void updateChannel(ChannelShaper shaper) {}

    private void setAgentFactory(AgentAdapter.Factory factory) {
        this.factory = factory;
    }

    Factory getAgentFactory() {
        return factory;
    }

    @Override
    public boolean supportsMixedSubscription() {
        return true; // All QDAgents must support mixed subscription
    }

    // ========== MessageAdapter Override ==========

    @Override
    protected void processSubscription(SubscriptionIterator iterator, MessageType message) {
        if (!message.isSubscription())
            throw new IllegalArgumentException(message.toString());
        if (!isAlive()) {
            reportIgnoredMessage("Adapter is " + getStatus(), message);
        } else {
            boolean hasContract = false;
            QDContract contract = message.getContract();
            RecordSource sub = LegacyAdapter.of(iterator);
            long initialPosition = sub.getPosition();
            for (int i = 0; i < shapers.length; i++) {
                ChannelShaper shaper = shapers[i];
                if (shaper.getContract() != contract)
                    continue;
                hasContract = true;
                AgentChannel channel = getOrCreateChannelAt(i);
                sub.setPosition(initialPosition);
                channel.processSubscription(message, sub);
            }
            LegacyAdapter.release(iterator, sub);
            if (!hasContract)
                reportIgnoredMessage("Contract is not supported", message);
        }
        SubscriptionConsumer.VOID.processSubscription(iterator); // silently ignore all remaining data if it was not processed
    }

    private AgentChannel getOrCreateChannelAt(int i) {
        AgentChannel channel = channels[i];
        if (channel == null) {
            ChannelShaper shaper = shapers[i];
            channel = new AgentChannel(this, shaper);
            shaper.bind(channel);
            channels[i] = channel;
        }
        return channel;
    }

    @Override
    protected void closeImpl() {
        assert Thread.holdsLock(this);
        if (channels != null) {
            for (AgentChannel channel : channels) {
                if (channel != null)
                    channel.close();
            }
        }
    }

    @Override
    public boolean retrieveMessages(MessageVisitor visitor) {
        super.retrieveMessages(visitor);
        long mask = retrieveMask();
        mask = retrieveDescribeProtocolMessage(visitor, mask);
        // note: addMask was previously enclosed into finally block. This could lead to StockOverflow and
        // offers no real protection, since any exception should terminate ongoing connection anyway.
        addMask(mask);
        boolean result = mask != 0;
        result |= retrieveDataMessages(visitor);
        return result;
    }

    @Override
    public void prepareProtocolDescriptor(ProtocolDescriptor desc) {
        super.prepareProtocolDescriptor(desc);
        Map<MessageDescriptor, String> messageTypeFilters = new HashMap<>();
        for (QDContract contract : QD_CONTRACTS) {
            QDFilter combinedFilter = QDFilter.NOTHING;
            for (ChannelShaper shaper : shapers) {
                if (shaper.getContract() == contract) {
                    // compute combined filter for all channels for this contract
                    combinedFilter = CompositeFilters.makeOr(combinedFilter, shaper.getSubscriptionFilter().toStableFilter());
                }
            }
            if (combinedFilter != QDFilter.NOTHING) {
                // prepare message for this contract
                MessageDescriptor addSubscriptionMessage = desc.newMessageDescriptor(MessageType.forAddSubscription(contract));
                desc.addSend(desc.newMessageDescriptor(MessageType.forData(contract)));
                desc.addReceive(addSubscriptionMessage);
                desc.addReceive(desc.newMessageDescriptor(MessageType.forRemoveSubscription(contract)));
                // postpone setting contract filter
                messageTypeFilters.put(addSubscriptionMessage, combinedFilter.toString());
            }
        }
        // now check if filters for all message types are the same.
        HashSet<String> filtersStringSet = new HashSet<>(messageTypeFilters.values());
        if (filtersStringSet.size() == 1) {
            // use common filter property
            String filter = filtersStringSet.iterator().next();
            if (!filter.equals(QDFilter.ANYTHING.toString())) // don't need to set filter for "anything" explicitly
                desc.setProperty(ProtocolDescriptor.FILTER_PROPERTY, filter);
        } else {
            // set its own filter for each contract
            messageTypeFilters.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .forEach(entry -> entry.getKey().setProperty(ProtocolDescriptor.FILTER_PROPERTY, entry.getValue()));
        }
    }

    @Override
    public void processDescribeProtocol(ProtocolDescriptor desc, boolean logDescriptor) {
        super.processDescribeProtocol(desc, logDescriptor);
        if (channels == null)
            return;
        QDFilterFactory filterFactory = CompositeFilters.getFactory(scheme);
        Map<String, QDFilter> filters = new HashMap<>();
        filters.put(null, QDFilter.ANYTHING);
        for (QDContract contract : QD_CONTRACTS) {
            MessageDescriptor md = desc.getReceive(MessageType.forData(contract));
            if (md == null)
                continue;
            String filter = md.getProperty(ProtocolDescriptor.FILTER_PROPERTY);
            if (!filters.containsKey(filter)) // null is anyway in filters map (maps to QDFilter.ANYTHING)
                try {
                    filters.put(filter, filterFactory.createFilter(filter, QDFilterContext.REMOTE_FILTER));
                } catch (IllegalArgumentException e) {
                    QDLog.log.warn("Cannot parse filter '" + filter + "' from " + getRemoteHostAddress(), e);
                    filters.put(filter, QDFilter.ANYTHING);
                }
            peerFilter[contract.ordinal()] = filters.get(filter);
        }
    }

    @Override
    public boolean isProtocolDescriptorCompatible(ProtocolDescriptor desc) {
        if (shapers == null)
            return true;
        for (QDContract contract : QD_CONTRACTS) {
            boolean hasContract = false;
            for (ChannelShaper shaper : shapers) {
                if (shaper.getContract() != contract)
                    continue;
                hasContract = true;
            }
            if (hasContract && desc.canSend(MessageType.forAddSubscription(contract)) && desc.canReceive(MessageType.forData(contract)))
                return true;
        }
        return false;
    }

    // returns true if more data remains in collectors, false otherwise
    protected boolean retrieveDataMessages(MessageVisitor visitor) {
        if (channels == null)
            return false;
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

            if (minDistance > 0)
                for (AgentChannel channel : channels) { // distribute more quota
                    if (channel == null)
                        continue; // not initialized yet (no subscription)
                    channel.quota += minDistance * channel.shaper.getWeight();
                    if (channel.quota >= 1) // can happen for channels which has no data for now
                        channel.quota = 1;
                }

            dueChannel.quota = 0;
            if (dueChannel.retrieveSnapshotOrData(visitor, currentTime))
                return true;
        }
        return true;
    }

    @Override
    public void reinitConfiguration(AuthSession session) {
        if (session == null) {
            if (hasAuthRealm())
                return;
            if (channels == null)
                initialize(factory.getAgentAdapterChannels().getNewShapers());
            return;
        }
        ChannelShaper[] shapers = null;
        if (CHANNEL_SHAPERS_FACTORIES != null) {
            for (ChannelShapersFactory shapersFactory : CHANNEL_SHAPERS_FACTORIES) {
                shapers = shapersFactory.createChannelShapers(this, session);
                if (shapers != null)
                    break;
            }
        }
        if (shapers == null)
            shapers = BasicChannelShaperFactory.INSTANCE.createChannelShapers(this, session);
        initialize(shapers);
    }

    @Override
    public long nextRetrieveTime(long currentTime) {
        long time = super.nextRetrieveTime(currentTime);
        if (channels != null) {
            for (AgentChannel channel : channels) {
                if (channel == null)
                    continue; // not initialized yet (no subscription)
                time = Math.min(time, channel.nextRetrieveTime(currentTime));
            }
        }
        return time;
    }

    // extension method for SubscriptionAdapter in feed tool
    protected boolean visitData(MessageVisitor visitor, RecordProvider provider, MessageType message) {
        return visitor.visitData(provider, message);
    }
}
