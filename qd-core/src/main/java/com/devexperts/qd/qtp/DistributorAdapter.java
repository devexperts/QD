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

import com.devexperts.connector.proto.Configurable;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataConsumer;
import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDCollector;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.QDHistory;
import com.devexperts.qd.QDStream;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.ng.RecordListener;
import com.devexperts.qd.ng.RecordProvider;
import com.devexperts.qd.qtp.fieldreplacer.FieldReplacersCache;
import com.devexperts.qd.spi.QDFilterContext;
import com.devexperts.qd.spi.QDFilterFactory;
import com.devexperts.qd.stats.QDStats;
import com.devexperts.util.LogUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * The <code>DistributorAdapter</code> adapts distributor side of QD to message API.
 * The distributor side of an QD is an aggregation of {@link QDDistributor}
 * for all its APIs (the <code>DistributorAdapter</code> creates its own distributors,
 * one distributor per API). Thus, the <code>DistributorAdapter</code> can be used
 * to represent an outside data provider in the specific QD.
 *
 * if you are a QD client - use this Adapter
 */
public class DistributorAdapter extends MessageAdapter implements QDFilter.UpdateListener {

    private static final QDContract[] QD_CONTRACTS = QDContract.values();
    private static final int N_CONTRACTS = QD_CONTRACTS.length;
    private static final MessageType[] ADD_MESSAGES = new MessageType[N_CONTRACTS];
    private static final MessageType[] REMOVE_MESSAGES = new MessageType[N_CONTRACTS];

    static {
        for (QDContract contract : QD_CONTRACTS) {
            ADD_MESSAGES[contract.ordinal()] = MessageType.forAddSubscription(contract);
            REMOVE_MESSAGES[contract.ordinal()] = MessageType.forRemoveSubscription(contract);
        }
    }

    /**
     * The factory for distributor side of an QD.
     */
    public static class Factory extends MessageAdapter.AbstractFactory {
        private FieldReplacersCache fieldReplacer = null;

        /**
         * Creates new factory. Accepts <code>null</code> parameters.
         */
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
        }

        @Override
        public MessageAdapter createAdapter(QDStats stats) {
            return new DistributorAdapter(endpoint, ticker, stream, history,
                getFilter(), getStripe(), stats, fieldReplacer);
        }

        /**
         * Field Replacers specification for input data.
         */
        public String getFieldReplacer() {
            return fieldReplacer == null ? null : fieldReplacer.getSpec();
        }

        /**
         * Set Field Replacers configuration for input data.
         *
         * @param fieldReplacer field replacers configuration to process input data.
         */
        @Configurable(description = "Field Replacers for input connection")
        public void setFieldReplacer(String fieldReplacer) {
            this.fieldReplacer = fieldReplacer == null ? null : FieldReplacersCache.valueOf(getScheme(), fieldReplacer);
        }
    }

    private static final Logging log = Logging.getLogging(DistributorAdapter.class);

    // ------------------------- instance fields -------------------------

    private final DataScheme scheme;
    private final QDFilter localFilter; // @NotNull
    private final QDFilter localStripe; // @NotNull

    private final QDCollector[] collectors = new QDCollector[N_CONTRACTS];
    private final AtomicReferenceArray<QDDistributor> distributors = new AtomicReferenceArray<>(N_CONTRACTS);

    // Filters received from remote peer in DESCRIBE PROTOCOL message
    private final QDFilter[] peerFilter = new QDFilter[N_CONTRACTS];
    private QDFilter peerStripe;

    private final RecordListener subListener = new SubListener();

    private final FieldReplacersCache fieldReplacer;

    private int phaseAdd;
    private int phaseRemove;

    // ------------------------- constructors -------------------------

    public DistributorAdapter(QDEndpoint endpoint, QDTicker ticker, QDStream stream, QDHistory history,
        SubscriptionFilter filter, QDFilter stripe, QDStats stats, FieldReplacersCache fieldReplacer)
    {
        super(endpoint, stats);
        this.scheme = getCommonScheme(ticker, stream, history);
        this.localFilter = QDFilter.fromFilter(filter, scheme);
        this.localStripe = (stripe != null) ? stripe : QDFilter.ANYTHING;
        this.fieldReplacer = fieldReplacer;
        collectors[QDContract.TICKER.ordinal()] = ticker;
        collectors[QDContract.STREAM.ordinal()] = stream;
        collectors[QDContract.HISTORY.ordinal()] = history;
    }

    @Deprecated
    public DistributorAdapter(QDEndpoint endpoint, QDTicker ticker, QDStream stream, QDHistory history,
        SubscriptionFilter filter, QDStats stats, FieldReplacersCache fieldReplacer)
    {
        this(endpoint, ticker, stream, history, filter, null, stats, fieldReplacer);
    }

    @Deprecated
    public DistributorAdapter(QDEndpoint endpoint, QDTicker ticker, QDStream stream, QDHistory history,
        SubscriptionFilter filter, QDStats stats)
    {
        this(endpoint, ticker, stream, history, filter, null, stats, null);
    }

    @Deprecated
    public DistributorAdapter(QDTicker ticker, QDStream stream, QDHistory history,
        SubscriptionFilter filter, QDStats stats)
    {
        this(null, ticker, stream, history, filter, null, stats, null);
    }

    // ------------------------- instance methods -------------------------

    public QDCollector getCollector(QDContract contract) {
        return collectors[contract.ordinal()];
    }

    @Deprecated
    protected QDDistributor createDistributor(QDCollector collector, SubscriptionFilter filter, String keyProperties) {
        return createDistributor(collector, QDFilter.fromFilter(filter, scheme), QDFilter.ANYTHING, keyProperties);
    }

    /**
     * This method is used internally by distributor adapter to create agent for the corresponding
     * collector, filter, stripe, and keyProperties from this adapter's constructor.
     * This method may be overridden to create agent with other filter, otherwise customize the agent
     * that is being created, or to keep track of created agents.
     */
    protected QDDistributor createDistributor(
        QDCollector collector, QDFilter filter, QDFilter stripe, String keyProperties)
    {
        return collector.distributorBuilder()
            .withFilter(filter)
            .withStripe(stripe)
            .withKeyProperties(keyProperties)
            .build();
    }

    private QDDistributor getOrCreateDistributor(int i) {
        QDDistributor distributor = distributors.get(i);
        if (distributor != null)
            return distributor;
        QDCollector collector = collectors[i];
        if (collector == null)
            return null;
        synchronized (this) {
            distributor = distributors.get(i);
            if (distributor != null)
                return distributor;

            // Create distributor combining filters and stripes from both local and remote parties
            distributor = createDistributor(collector,
                CompositeFilters.makeAnd(peerFilter[i], localFilter),
                intersectStripes(peerStripe, localStripe),
                getStats().getFullKeyProperties());
            distributors.set(i, distributor);
        }
        return distributor;
    }

    // For tests only!
    @Deprecated
    QDDistributor getDistributor(QDContract contract) {
        return distributors.get(contract.ordinal());
    }

    public String toString() {
        return super.toString() + (localFilter == QDFilter.ANYTHING  ? "" : "[" + localFilter + "]");
    }

    @Override
    public DataScheme getScheme() {
        return scheme;
    }

    @Override
    public String getSymbol(char[] chars, int offset, int length) {
        QDCollector c;
        String s;
        for (int i = 0; i < N_CONTRACTS; i++)
            if ((c = collectors[i]) != null && (s = c.getSymbol(chars, offset, length)) != null)
                return s;
        return null;
    }

    @Override
    public FieldReplacersCache getFieldReplacer() {
        return fieldReplacer;
    }

    // ========== Dynamic filters support ==========

    @Override
    public void filterUpdated(QDFilter filter) {
        markForImmediateRestart();
        close();
    }

    // ========== MessageAdapter Override ==========

    @Override
    protected void startImpl(MasterMessageAdapter master) {
        if (localFilter.isDynamic())
            log.warn("Using dynamic filter '" + LogUtil.hideCredentials(localFilter) + "'" +
                " in distributor address will cause connection reset when filter changes");
        localFilter.addUpdateListener(this); // listen for filter updates
        // Legacy behavior: immediately send subscription if we are not using DESCRIBE_PROTOCOL messages,
        // which is when useDescribeProtocol() was not called before start.
        if (!useDescribeProtocol)
            sendSubscriptionFromAllCollectors();
        super.startImpl(master);
    }

    @Override
    protected void closeImpl() {
        localFilter.removeUpdateListener(this);
        for (int i = 0; i < N_CONTRACTS; i++)
            if (distributors.get(i) != null)
                distributors.get(i).close();
    }

    @Override
    protected void processData(DataIterator iterator, MessageType message) {
        if (!message.isData())
            throw new IllegalArgumentException(message.toString());
        if (!isAlive()) {
            reportIgnoredMessage("Adapter is " + getStatus(), message);
        } else {
            QDContract contract = message.getContract();
            if (contract != null) {
                QDDistributor distributor = getOrCreateDistributor(contract.ordinal());
                if (distributor == null) {
                    reportIgnoredMessage("Contract is not supported", message);
                } else {
                    distributor.processData(iterator);
                    return; // processed -- exit from method
                }
            }
        }
        DataConsumer.VOID.processData(iterator); // silently ignore otherwise
    }

    @Override
    public boolean retrieveMessages(MessageVisitor visitor) {
        super.retrieveMessages(visitor);
        long mask = retrieveMask();
        mask = retrieveDescribeProtocolMessage(visitor, mask);
        mask = retrieveAddAndRemoveMessages(visitor, mask);
        // note: addMask was previously enclosed into finally block. This could lead to StockOverflow and
        // offers no real protection, since any exception should terminate ongoing connection anyway.
        addMask(mask);
        return mask != 0;
    }

    @Override
    public void prepareProtocolDescriptor(ProtocolDescriptor desc) {
        super.prepareProtocolDescriptor(desc);
        QDFilter stableFilter = CompositeFilters.toStableFilter(this.localFilter);
        if (stableFilter != QDFilter.ANYTHING)
            desc.setProperty(ProtocolDescriptor.FILTER_PROPERTY, stableFilter.toString());
        for (QDContract contract : QD_CONTRACTS)
            if (collectors[contract.ordinal()] != null) {
                desc.addSend(desc.newMessageDescriptor(MessageType.forAddSubscription(contract)));
                desc.addSend(desc.newMessageDescriptor(MessageType.forRemoveSubscription(contract)));
                desc.addReceive(desc.newMessageDescriptor(MessageType.forData(contract)));
            }
        desc.addReceive(desc.newMessageDescriptor(MessageType.RAW_DATA));
        QDFilter stableStripe = CompositeFilters.toStableFilter(localStripe);
        if (stableStripe != QDFilter.ANYTHING) {
            desc.setProperty(ProtocolDescriptor.STRIPE_PROPERTY, stableStripe.toString());
        }
    }

    @Override
    public void processDescribeProtocol(ProtocolDescriptor desc, boolean logDescriptor) {
        super.processDescribeProtocol(desc, logDescriptor);

        QDFilterFactory filterFactory = CompositeFilters.getFactory(scheme);
        String stripe = desc.getProperty(ProtocolDescriptor.STRIPE_PROPERTY);
        if (stripe != null) {
            try {
                peerStripe = filterFactory.createFilter(stripe, QDFilterContext.REMOTE_FILTER);
            } catch (IllegalArgumentException e) {
                log.warn("Cannot parse stripe filter '" + LogUtil.hideCredentials(stripe) + "'" +
                    " from " + LogUtil.hideCredentials(getRemoteHostAddress()), e);
            }
        }

        Map<String, QDFilter> filters = new HashMap<>();
        filters.put(null, QDFilter.ANYTHING);
        for (QDContract contract : QD_CONTRACTS) {
            if (collectors[contract.ordinal()] == null)
                continue; // don't have collector for this contract -- don't care
            boolean add = desc.canReceive(MessageType.forAddSubscription(contract));
            boolean remove = desc.canReceive(MessageType.forRemoveSubscription(contract));
            if (!add && !remove)
                continue; // don't create distributor for this contract at all
            if (add) {
                // Filters are supported for ADD_SUBSCRIPTION only
                MessageDescriptor md = desc.getReceive(MessageType.forAddSubscription(contract));
                String filter = md.getProperty(ProtocolDescriptor.FILTER_PROPERTY);
                if (!filters.containsKey(filter)) // null is anyway in filters map (maps to QDFilter.ANYTHING)
                    try {
                        filters.put(filter, filterFactory.createFilter(filter, QDFilterContext.REMOTE_FILTER));
                    } catch (IllegalArgumentException e) {
                        log.warn("Cannot parse filter '" + LogUtil.hideCredentials(filter) + "'" +
                            " from " + LogUtil.hideCredentials(getRemoteHostAddress()) + ": " + e);
                        filters.put(filter, QDFilter.ANYTHING);
                    }
                peerFilter[contract.ordinal()] = filters.get(filter);
            }
            QDDistributor distributor = getOrCreateDistributor(contract.ordinal());
            distributor.getAddedRecordProvider().setRecordListener(add ? subListener : RecordListener.VOID);
            distributor.getRemovedRecordProvider().setRecordListener(remove ? subListener : RecordListener.VOID);
        }
    }

    @Override
    public boolean isProtocolDescriptorCompatible(ProtocolDescriptor desc) {
        for (QDContract contract : QD_CONTRACTS)
            if (collectors[contract.ordinal()] != null &&
                    (desc.canReceive(MessageType.forAddSubscription(contract)) ||
                    desc.canSend(MessageType.forData(contract)) ||
                    desc.canSend(MessageType.RAW_DATA)))
            {
                return true;
            }
        return false;
    }

    private void sendSubscriptionFromAllCollectors() {
        for (int i = 0; i < N_CONTRACTS; i++)
            if (collectors[i] != null) {
                QDDistributor distributor = getOrCreateDistributor(i);
                distributor.getAddedRecordProvider().setRecordListener(subListener);
                distributor.getRemovedRecordProvider().setRecordListener(subListener);
            }
    }

    private long retrieveAddAndRemoveMessages(MessageVisitor visitor, long mask) {
        // first process all add messages until they are all exhausted
        int cur = phaseAdd;
        int stop = phaseAdd;
        boolean hasMore = false;
        do {
            MessageType message = ADD_MESSAGES[cur];
            if (hasMessageMask(mask, message)) {
                SubscriptionProvider provider = distributors.get(cur).getAddedSubscriptionProvider();
                hasMore = visitSubscription(visitor, provider, message);
                if (!hasMore)
                    mask = clearMessageMask(mask, message);
            }
            phaseAdd = cur = (cur + 1) % N_CONTRACTS;
        } while (!hasMore && cur != stop);
        // then process remove messages if there's still potential room for them
        if (!hasMore)
            mask = retrieveRemoveMessages(visitor, mask);
        return mask;
    }

    private long retrieveRemoveMessages(MessageVisitor visitor, long mask) {
        int cur = phaseRemove;
        int stop = phaseRemove;
        boolean hasMore = false;
        do {
            MessageType message = REMOVE_MESSAGES[cur];
            if (hasMessageMask(mask, message)) {
                SubscriptionProvider provider = distributors.get(cur).getRemovedSubscriptionProvider();
                hasMore = visitSubscription(visitor, provider, message);
                if (!hasMore)
                    mask = clearMessageMask(mask, message);
            }
            phaseRemove = cur = (cur + 1) % N_CONTRACTS;
        } while (!hasMore && cur != stop);
        return mask;
    }

    // extension method for FeedAdapter in feed tool
    protected boolean visitSubscription(MessageVisitor visitor, SubscriptionProvider provider, MessageType message) {
        return visitor.visitSubscription(provider, message);
    }

    // extension method. It is used to delay subscription processing until credentials are known
    public void subscriptionAvailable(SubscriptionProvider provider) {
        if (provider == null)
            throw new NullPointerException();
        for (int i = 0; i < N_CONTRACTS; i++) {
            QDDistributor distributor = distributors.get(i);
            if (distributor != null) {
                // NOTE: We cannot call getXXXSubProvider unless we definitely know that we need them,
                // since calling those methods lazily initializes the corresponding distributor data structures
                if (provider == distributor.getAddedSubscriptionProvider()) {
                    subscriptionChanged(provider, ADD_MESSAGES[i]);
                    return; // we found provider
                }
                if (provider == distributor.getRemovedSubscriptionProvider()) {
                    subscriptionChanged(provider, REMOVE_MESSAGES[i]);
                    return; // we found provider
                }
            }
        }
        throw new AssertionError("Unknown subscription provider: " + provider);
    }

    protected void subscriptionChanged(SubscriptionProvider provider, MessageType message) {
        addMask(getMessageMask(message));
    }

    private class SubListener implements RecordListener {
        @Override
        public void recordsAvailable(RecordProvider provider) {
            subscriptionAvailable(provider);
        }
    }
}
