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

import com.devexperts.auth.AuthSession;
import com.devexperts.connector.proto.Configurable;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SubscriptionConsumer;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.qtp.auth.BasicChannelShaperFactory;
import com.devexperts.qd.qtp.auth.ChannelShapersFactory;
import com.devexperts.qd.spi.QDFilterContext;
import com.devexperts.qd.spi.QDFilterFactory;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.qd.util.LegacyAdapter;
import com.devexperts.services.Services;
import com.devexperts.util.LogUtil;
import com.devexperts.util.LoggedThreadPoolExecutor;
import com.devexperts.util.TimePeriod;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;

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
    private static final Iterable<ChannelShapersFactory> CHANNEL_SHAPERS_FACTORIES =
        Services.createServices(ChannelShapersFactory.class, null);

    private static final Logging log = Logging.getLogging(AgentAdapter.class);

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

        /**
         * Subscription keep-alive period.
         * <p>If more than zero, unsubscription requests will be delayed for a specified period to amortize
         * fast unsub/sub sequences.
         * <p><b>NOTE:</b> For the moment only zero and unlimited periods are supported.
         */
        private TimePeriod subscriptionKeepAlive = TimePeriod.ZERO;

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

        @Nonnull
        public synchronized TimePeriod getSubscriptionKeepAlive() {
            return subscriptionKeepAlive;
        }

        @Configurable(description = "subscription keep-alive period (0 or 'inf')")
        public synchronized void setSubscriptionKeepAlive(TimePeriod keepAlive) {
            Objects.requireNonNull(keepAlive);
            if (subscriptionKeepAlive.equals(keepAlive))
                return;
            if (!TimePeriod.ZERO.equals(keepAlive) && !TimePeriod.UNLIMITED.equals(keepAlive))
                throw new IllegalArgumentException("Only zero or infinite supported");
            this.subscriptionKeepAlive = keepAlive;
            rebuildChannels();
        }

        synchronized Executor getOrCreateSubscriptionExecutor() {
            if (subscriptionExecutor != null)
                return subscriptionExecutor;
            if (subscriptionThreads > 0)
                subscriptionExecutor = new LoggedThreadPoolExecutor(subscriptionThreads, this + "-Subscription", log);
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
            AgentAdapter adapter = new AgentAdapter(endpoint,
                getCommonScheme(ticker, stream, history), getFilter(), getStripe(), stats);
            adapter.setAgentFactory(this);
            return adapter;
        }
    }

    // ------------------------- instance fields -------------------------

    private final DataScheme scheme;
    private final QDFilter localFilter; // @NotNull
    protected QDFilter localStripe; // @NotNull
    private AgentAdapter.Factory factory;
    private boolean skipRemoveSubscription = false; // current implementation supports only infinite keep-alive period

    // Filters received from remote peer in DESCRIBE PROTOCOL message
    final QDFilter[] peerFilter = new QDFilter[N_CONTRACTS];
    QDFilter peerStripe;

    private AgentChannels channels; // effectively final, filled by initialize method

    private MessageVisitor retrieveVisitor;

    // ------------------------- constructors -------------------------

    /**
     * Creates new agent adapter for specified endpoints, ticker, stream, history, filter and stats.
     * Any of the endpoint, collectors and/or filter may be {@code null}.
     *
     * <p>The resulting adapter will be {@link #initialize(ChannelShaper[]) initiailized}.
     * It will use equal weight of 1 and no aggregation for all collectors.
     */
    public AgentAdapter(QDEndpoint endpoint, QDTicker ticker, QDStream stream, QDHistory history,
        SubscriptionFilter filter, QDFilter stripe, QDStats stats)
    {
        super(endpoint, stats);
        this.scheme = getCommonScheme(ticker, stream, history);
        this.localFilter = QDFilter.fromFilter(filter, scheme);
        this.localStripe = (stripe != null) ? stripe : QDFilter.ANYTHING;
        ArrayList<ChannelShaper> shapers = new ArrayList<>();
        if (ticker != null)
            shapers.add(newDynamicShaper(ticker));
        if (stream != null)
            shapers.add(newDynamicShaper(stream));
        if (history != null)
            shapers.add(newDynamicShaper(history));
        channels = new AgentChannels(new OwnerImpl(), shapers);
    }

    @Deprecated
    public AgentAdapter(QDEndpoint endpoint, QDTicker ticker, QDStream stream, QDHistory history,
        SubscriptionFilter filter, QDStats stats)
    {
        this(endpoint, ticker, stream, history, filter, null, stats);
    }

    /**
     * Creates new agent adapter for specified ticker, stream, history, filter and stats.
     * Any of the collectors and/or filter may be {@code null}.
     *
     * <p>The resulting adapter will be {@link #initialize(ChannelShaper[]) initiailized}.
     * It will use equal weight of 1 and no aggregation for all collectors.
     */
    @Deprecated
    public AgentAdapter(QDTicker ticker, QDStream stream, QDHistory history, SubscriptionFilter filter, QDStats stats) {
        this(null, ticker, stream, history, filter, null, stats);
    }

    /**
     * Creates new agent adapter for specified scheme and stats.
     *
     * <p>Adapter created by this constructor must be {@link #initialize(ChannelShaper[]) initialized}
     * with shapers before being used.
     *
     * @param scheme data scheme
     * @param stats stats
     */
    public AgentAdapter(DataScheme scheme, QDStats stats) {
        this(null, scheme, QDFilter.ANYTHING, QDFilter.ANYTHING, stats);
    }

    private AgentAdapter(QDEndpoint endpoint, DataScheme scheme, QDFilter filter, QDFilter stripe, QDStats stats) {
        super(endpoint, stats);
        this.scheme = Objects.requireNonNull(scheme, "scheme");
        this.localFilter = Objects.requireNonNull(filter, "filter");
        this.localStripe = Objects.requireNonNull(stripe, "stripe");
    }

    private DynamicChannelShaper newDynamicShaper(QDCollector collector) {
        DynamicChannelShaper shaper = new DynamicChannelShaper(collector.getContract(), null, localFilter);
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
        if (channels != null)
            throw new IllegalArgumentException("Already initialized");
        channels = new AgentChannels(new OwnerImpl(), Arrays.asList(shapers));
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

    protected QDAgent.Builder createAgentBuilder(
        QDCollector collector, SubscriptionFilter filter, String keyProperties)
    {
        return collector.agentBuilder()
            .withFilter(QDFilter.fromFilter(filter, scheme))
            //TODO Move to parameter (it will require changes in ChannelShaper and AgentChannel.Config)
            .withStripe(MessageAdapter.intersectStripes(peerStripe, localStripe))
            .withKeyProperties(keyProperties)
            .withOptSet(getRemoteOptSet());
    }

    public String toString() {
        return super.toString() + (localFilter == QDFilter.ANYTHING  ? "" : "[" + localFilter + "]");
    }

    @Override
    public DataScheme getScheme() {
        return scheme;
    }

    private class OwnerImpl implements AgentChannels.Owner {
        @Override
        public QDAgent createAgent(QDCollector collector, QDFilter filter) {
            return AgentAdapter.this.createAgent(collector, filter, getStats().getFullKeyProperties());
        }

        @Override
        public QDAgent createVoidAgent(QDContract contract) {
            return QDFactory.getDefaultFactory().createVoidAgentBuilder(contract, scheme).build();
        }

        @Override
        public QDFilter getPeerFilter(QDContract contract) {
            return peerFilter[contract.ordinal()];
        }

        @Override
        public void recordsAvailable() {
            notifyListener();
        }

        @Override
        public boolean retrieveData(DataProvider dataProvider, QDContract contract) {
            return retrieveVisitor.visitData(dataProvider, MessageType.forData(contract));
        }
    }

    @Override
    public String getSymbol(char[] chars, int offset, int length) {
        return channels.getSymbol(chars, offset, length);
    }

    /**
     * @deprecated No need to use this method. It does nothing. Channels are automatically update on change
     *     of their parameters.
     */
    public void updateChannel(ChannelShaper shaper) {}

    private void setAgentFactory(AgentAdapter.Factory factory) {
        this.factory = factory;
        skipRemoveSubscription = TimePeriod.UNLIMITED.equals(factory.getSubscriptionKeepAlive());
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
        } else if (!skipRemoveSubscription || !message.isSubscriptionRemove()) {
            RecordSource sub = LegacyAdapter.of(iterator);
            if (skipRemoveSubscription) {
                RecordBuffer buf = skipRemoveSubscription(sub);
                processSubscription(buf, message);
                buf.release();
            } else {
                processSubscription(sub, message);
            }
            LegacyAdapter.release(iterator, sub);
        }
        SubscriptionConsumer.VOID.processSubscription(iterator); // silently ignore all remaining data if it was not processed
    }

    private void processSubscription(RecordSource sub, MessageType message) {
        if (!channels.processSubscription(sub, message.getContract(), message.isSubscriptionAdd()))
            reportIgnoredMessage("Contract is not supported", message);
    }

    private RecordBuffer skipRemoveSubscription(RecordSource sub) {
        RecordBuffer buf = RecordBuffer.getInstance(sub.getMode());
        for (RecordCursor cur; (cur = sub.next()) != null; ) {
            if (!EventFlag.REMOVE_SYMBOL.in(cur.getEventFlags()))
                buf.append(cur);
        }
        return buf;
    }

    @Override
    protected void closeImpl() {
        assert Thread.holdsLock(this);
        if (channels != null)
            channels.close();
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
            QDFilter combinedFilter = channels.combinedFilter(contract);
            if (combinedFilter != QDFilter.NOTHING) {
                // prepare message for this contract
                MessageDescriptor addSubMessage = desc.newMessageDescriptor(MessageType.forAddSubscription(contract));
                desc.addSend(desc.newMessageDescriptor(MessageType.forData(contract)));
                desc.addReceive(addSubMessage);
                desc.addReceive(desc.newMessageDescriptor(MessageType.forRemoveSubscription(contract)));
                // postpone setting contract filter
                messageTypeFilters.put(addSubMessage, combinedFilter.toString());
            }
        }
        // Use any non-trivial striper for all collectors and messages
        QDFilter stableStripe = CompositeFilters.toStableFilter(localStripe);
        if (stableStripe != QDFilter.ANYTHING) {
            desc.setProperty(ProtocolDescriptor.STRIPE_PROPERTY, stableStripe.toString());
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
                    log.warn("Cannot parse filter '" + LogUtil.hideCredentials(filter) + "'" +
                        " from " + LogUtil.hideCredentials(getRemoteHostAddress()), e);
                    filters.put(filter, QDFilter.ANYTHING);
                }
            peerFilter[contract.ordinal()] = filters.get(filter);
        }
        String stripe = desc.getProperty(ProtocolDescriptor.STRIPE_PROPERTY);
        if (stripe != null) {
            try {
                peerStripe = filterFactory.createFilter(stripe, QDFilterContext.REMOTE_FILTER);
            } catch (IllegalArgumentException e) {
                log.warn("Cannot parse stripe filter '" + LogUtil.hideCredentials(stripe) + "'" +
                    " from " + LogUtil.hideCredentials(getRemoteHostAddress()), e);
            }
        }
    }

    @Override
    public boolean isProtocolDescriptorCompatible(ProtocolDescriptor desc) {
        if (channels == null)
            return true;
        return Arrays.stream(channels.shapers)
            .map(ChannelShaper::getContract)
            .distinct()
            .anyMatch(contract -> desc.canSend(MessageType.forAddSubscription(contract)) &&
                desc.canReceive(MessageType.forData(contract)));
    }

    // returns true if more data remains in collectors, false otherwise
    protected boolean retrieveDataMessages(MessageVisitor visitor) {
        if (channels == null)
            return false;
        retrieveVisitor = visitor;
        boolean hasMoreData = channels.retrieveData();
        retrieveVisitor = null;
        return hasMoreData;
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
        if (channels != null)
            time = Math.min(time, channels.nextRetrieveTime(currentTime));
        return time;
    }
}
