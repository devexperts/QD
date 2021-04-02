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
package com.dxfeed.api.impl;

import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.kit.CompositeFilters;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.util.SubscriptionProcessor;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.IndexerFunction;
import com.dxfeed.api.DXFeedSubscription;
import com.dxfeed.api.DXPublisher;
import com.dxfeed.api.osub.WildcardSymbol;
import com.dxfeed.event.EventType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DXPublisherImpl extends DXPublisher {
    private final DXEndpointImpl endpoint;
    private final List<QDDistributor> publishDistributors = new ArrayList<>();
    private final IndexedSet<Class<?>, Subscription<?>> subscriptionsByClass = IndexedSet.create((IndexerFunction<Class<?>, Subscription<?>>) sub -> sub.eventType);

    DXPublisherImpl(DXEndpointImpl endpoint) {
        this.endpoint = endpoint;
        for (QDContract contract : endpoint.getContracts()) {
            int ordinal = contract.ordinal();
            while (publishDistributors.size() <= ordinal)
                publishDistributors.add(null);
            publishDistributors.set(ordinal, endpoint.getCollector(contract).distributorBuilder().build());
        }
    }

    public void closeImpl() {
        for (Subscription<?> subscription : subscriptionsByClass) {
            subscription.close();
        }
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void publishEvents(Collection<?> events) {
        if (endpoint.isClosed() || events.isEmpty())
            return;
        int nextContract = 0;
        int thisContract;
        int doneContracts = 0;
        do {
            thisContract = nextContract;
            nextContract = 0;
            RecordMode mode = RecordMode.FLAGGED_DATA;
            if (endpoint.getQDEndpoint().hasEventTimeSequence())
                mode = mode.withEventTimeSequence();
            RecordBuffer buf = RecordBuffer.getInstance(mode);
            for (Object event : events) {
                EventDelegateSet delegateSet = endpoint.getDelegateSetByEventType(event.getClass());
                List<EventDelegate<?>> delegates = delegateSet.getPubDelegatesByEvent((EventType<?>) event);
                for (int i = 0, delegatesSize = delegates.size(); i < delegatesSize; i++) {
                    EventDelegate delegate = delegates.get(i);
                    int curContract = 1 << delegate.getContract().ordinal();
                    if (curContract != thisContract && thisContract != 0) {
                        if (nextContract == 0 && (doneContracts & curContract) == 0)
                            nextContract = curContract;
                        continue;
                    }
                    RecordCursor cursor = delegate.putEvent((EventType<?>) event, buf);
                    if (cursor != null)
                        thisContract = curContract;
                }
            }
            if (!buf.isEmpty()) // assert thisContract != 0
                getOrCreateDistributor(Integer.numberOfTrailingZeros(thisContract)).process(buf);
            buf.release();
            doneContracts |= thisContract;
        } while (nextContract != 0);
    }

    private synchronized QDDistributor getOrCreateDistributor(int contractOrdinal) {
        QDDistributor distributor = publishDistributors.get(contractOrdinal);
        if (distributor == null) {
            distributor = endpoint.getCollector(QDContract.values()[contractOrdinal]).distributorBuilder().build();
            publishDistributors.set(contractOrdinal, distributor);
        }
        return distributor;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> DXPublisherObservableSubscriptionImpl<T> getSubscription(Class<T> eventType) {
        Subscription<T> subscription = (Subscription<T>) subscriptionsByClass.getByKey(eventType);
        if (subscription == null) {
            subscription = createSubscriptionImpl(eventType);
            if (endpoint.isClosed())
                subscription.close();
        }
        return subscription.observableSubscription;
    }

    @SuppressWarnings("unchecked")
    private synchronized <T> Subscription<T> createSubscriptionImpl(Class<T> eventType) {
        Subscription<T> subscription = (Subscription<T>) subscriptionsByClass.getByKey(eventType);
        if (subscription == null) {
            subscriptionsByClass.add(subscription = new Subscription<>(eventType));
            subscription.start();
        }
        return subscription;
    }

    private class Subscription<T> {
        final Class<T> eventType;
        final Set<DataRecord> wildcardRecords; // set of records subscribed to by "*" via QDStream
        final InnerSubscription<T> innerSubscription;
        final DXPublisherObservableSubscriptionImpl<T> observableSubscription;
        final List<Processor<T>> processors = new ArrayList<>();

        @SuppressWarnings("unchecked")
        Subscription(Class<T> eventType) {
            this.eventType = eventType;
            wildcardRecords = new IndexedSet<DataRecord, DataRecord>();
            innerSubscription = new InnerSubscription(eventType);
            observableSubscription = new DXPublisherObservableSubscriptionImpl<>(innerSubscription);
            EnumMap<QDContract, Set<DataRecord>> records = new EnumMap<>(QDContract.class);
            EventDelegateSet<?, ?> delegateSet = endpoint.getDelegateSetByEventType(eventType);
            if (delegateSet != null)
                for (EventDelegate<?> eventDelegate : delegateSet.getAllPubDelegates()) {
                    QDContract contract = eventDelegate.getContract();
                    Set<DataRecord> set = records.get(contract);
                    if (set == null)
                        records.put(contract, set = new HashSet<>());
                    set.add(eventDelegate.getRecord());
                }
            for (Map.Entry<QDContract, Set<DataRecord>> entry : records.entrySet()) {
                QDContract contract = entry.getKey();
                Set<DataRecord> set = entry.getValue();
                QDDistributor distributor = endpoint.getCollector(contract).distributorBuilder()
                    .withFilter(CompositeFilters.forRecords(set))
                    .build();
                processors.add(new Processor<>(endpoint.getOrCreateExecutor(), contract, distributor, this));
            }
        }

        void start() {
            for (Processor<T> processor : processors) {
                processor.start();
            }
        }

        void close() {
            innerSubscription.close();
            for (Processor<T> processor : processors) {
                processor.close();
            }
        }
    }

    @SuppressWarnings("serial")
    private static class InnerSubscription<T> extends DXFeedSubscription<T> {
        InnerSubscription(Class<T> eventType) {
            super(eventType);
        }

        @Override
        protected boolean shallNotifyOnSymbolUpdate(@Nonnull Object symbol, @Nullable Object oldSymbol) {
            return true;
        }
    }

    private class Processor<T> extends SubscriptionProcessor {
        private final QDContract contract;
        private final QDDistributor distributor;
        private final Subscription<T> subscription;
        private final Set<Object> symbols = new IndexedSet<>(); // reusable object (sub processing is single-threaded)

        private Processor(Executor executor, QDContract contract, QDDistributor distributor,
            Subscription<T> subscription)
        {
            super(executor, contract);
            this.contract = contract;
            this.distributor = distributor;
            this.subscription = subscription;
        }

        public void start() {
            startProcessing(distributor);
        }

        public void close() {
            stopProcessing();
            distributor.close();
        }

        @Override
        protected void processAddedSubscription(RecordSource source) {
            processSub(source, true);
        }

        @Override
        protected void processRemovedSubscription(RecordSource source) {
            processSub(source, false);
        }

        @SuppressWarnings("unchecked")
        private void processSub(RecordSource source, boolean add) {
            symbols.clear();
            Collection<DataRecord> wildcards = null;
            RecordCursor cursor;
            while ((cursor = source.next()) != null) {
                DataRecord record = cursor.getRecord();
                List<EventDelegate<?>> delegates = endpoint.getDelegateListByContractAndRecord(contract, record);
                if (delegates == null)
                    continue;
                for (EventDelegate<?> delegate : delegates) {
                    if (!delegate.isPub() || delegate.getEventType() != subscription.eventType)
                        continue;
                    String qdSymbol = endpoint.decode(cursor.getCipher(), cursor.getSymbol());
                    if (qdSymbol.equals(WildcardSymbol.RESERVED_PREFIX) && contract == QDContract.STREAM) {
                        if (wildcards == null)
                            wildcards = new ArrayList<>();
                        wildcards.add(record);
                    } else
                        symbols.add(delegate.getSubscriptionSymbolByQDSymbolAndTime(qdSymbol, cursor.getTime()));
                }
            }
            if (add) {
                if (wildcards != null) {
                    if (subscription.wildcardRecords.isEmpty())
                        symbols.add(WildcardSymbol.ALL);
                    subscription.wildcardRecords.addAll(wildcards);
                }
                subscription.innerSubscription.addSymbols(symbols);
            } else {
                if (wildcards != null) {
                    subscription.wildcardRecords.removeAll(wildcards);
                    if (subscription.wildcardRecords.isEmpty())
                        symbols.add(WildcardSymbol.ALL);
                }
                subscription.innerSubscription.removeSymbols(symbols);
            }
        }
    }
}
