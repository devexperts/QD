/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.hash;

import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.QDDistributor;
import com.devexperts.qd.QDErrorHandler;
import com.devexperts.qd.QDTicker;
import com.devexperts.qd.impl.AbstractCollector;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.ng.RecordSource;
import com.devexperts.qd.stats.QDStats;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

/**
 * The {@code HashTicker} is a hash-based implementation of {@link QDTicker}.
 * It collects and distributes consumers' subscriptions, processes and distributes
 * incoming data and caches current values of processed data for random access.
 */
final class HashTicker extends AbstractCollector implements QDTicker {
    private static final int EXAMINE_BATCH_SIZE = 10000;

    private final DataRecord[] records;

    private final QDStats stats;
    private final QDStats stats_sub;
    private final QDStats stats_storage;

    private final HashMap<RecordValue, RecordValue> values = new HashMap<>();

    private final HashSet<HashDistributor> distributors_set = new HashSet<>();
    private HashDistributor[] distributors = new HashDistributor[0]; // Current snapshot of distributors_set.

    QDErrorHandler error_handler;

    HashTicker(Builder<?> builder) {
        super(builder);
        this.records = new DataRecord[scheme.getRecordCount()];
        for (int i = records.length; --i >= 0;)
            records[i] = scheme.getRecord(i);

        error_handler = scheme.getService(QDErrorHandler.class);
        if (error_handler == null)
            error_handler = QDErrorHandler.DEFAULT;

        this.stats = builder.getStats();
        this.stats_sub = stats.create(QDStats.SType.UNIQUE_SUB);
        this.stats_storage = stats.create(QDStats.SType.STORAGE_DATA);
    }

    @Override
    public QDStats getStats() {
        return stats;
    }

    // 'shared_key' is used only by getValue method to avoid key allocation for 'get' operation.
    private final RecordKey shared_key = new RecordKey(null, 0, null);

    RecordValue getValue(DataRecord record, int cipher, String symbol) {
        shared_key.set(record, cipher, symbol);
        //noinspection SuspiciousMethodCalls
        return values.get(shared_key);
    }

    boolean addSub(DataRecord record, int cipher, String symbol, HashAgent agent) {
        boolean added = false;
        RecordValue value = getValue(record, cipher, symbol);
        if (value == null) {
            value = new RecordValue(record, cipher, symbol);
            values.put(value, value);
            stats_sub.updateAdded(getRid(record));
            HashDistributor[] distributors = this.distributors;
            for (int i = distributors.length; --i >= 0;)
                if (distributors[i].addSub(value))
                    added = true;
        }
        if (value.addAgent(agent))
            agent.addSub(value);
        if (value.isAvailable())
            agent.recordChanged(value);
        return added;
    }

    boolean removeSub(DataRecord record, int cipher, String symbol, HashAgent agent) {
        boolean removed = false;
        RecordValue value = getValue(record, cipher, symbol);
        if (value != null && value.removeAgent(agent)) {
            agent.removeSub(value);
            if (value.getAgentCount() == 0) {
                values.remove(value);
                stats_sub.updateRemoved(getRid(record));
                if (value.isAvailable())
                    stats_storage.updateRemoved(getRid(record));
                HashDistributor[] distributors = this.distributors;
                for (int i = distributors.length; --i >= 0;)
                    if (distributors[i].removeSub(value))
                        removed = true;
            }
        }
        return removed;
    }

    void closed(HashDistributor distributor) {
        if (distributors_set.remove(distributor)) {
            distributors = distributors_set.toArray(new HashDistributor[distributors_set.size()]);
        }
    }

    void notifyAdded() {
        HashDistributor[] distributors = this.distributors; // Atomic read.
        for (int i = distributors.length; --i >= 0;)
            distributors[i].notifyAdded();
    }

    void notifyRemoved() {
        HashDistributor[] distributors = this.distributors; // Atomic read.
        for (int i = distributors.length; --i >= 0;)
            distributors[i].notifyRemoved();
    }

    void process(RecordSource source) {
        HashSet<HashAgent> changed_agents = new HashSet<>();
        synchronized (this) {
            for (RecordCursor cursor; (cursor = source.next()) != null;) {
                DataRecord record = cursor.getRecord();
                int rid = getRid(record);
                RecordValue value = getValue(record, cursor.getCipher(), cursor.getSymbol());
                if (value == null) {
                    continue;
                }
                boolean changed = !value.isAvailable();
                if (changed)
                    stats_storage.updateAdded(rid);
                else
                    stats_storage.updateChanged(rid);
                for (int i = 0, n = record.getIntFieldCount(); i < n; i++) {
                    int v = cursor.getInt(i);
                    if (!record.getIntField(i).equals(value.getInt(i), v)) {
                        value.setInt(i, v);
                        changed = true;
                    }
                }
                for (int i = 0, n = record.getObjFieldCount(); i < n; i++) {
                    Object v = cursor.getObj(i);
                    if (!record.getObjField(i).equals(value.getObj(i), v)) {
                        value.setObj(i, v);
                        changed = true;
                    }
                }
                value.setAvailable(true);
                if (changed) {
                    HashAgent[] agents = value.getAgents();
                    for (int k = agents.length; --k >= 0;)
                        if (agents[k].recordChanged(value))
                            changed_agents.add(agents[k]);
                }
            }
        }
        for (HashAgent agent : changed_agents)
            agent.notifyListener();
    }

    // ========== QDCollector Implementation ==========

    @Override
    public QDAgent buildAgent(QDAgent.Builder builder) {
        return new HashAgent(this, builder, stats.create(QDStats.SType.AGENT, builder.getKeyProperties()));
    }

    @Override
    public synchronized QDDistributor buildDistributor(QDDistributor.Builder builder) {
        HashDistributor distributor = new HashDistributor(this, builder.getFilter(), stats.create(QDStats.SType.DISTRIBUTOR, builder.getKeyProperties()));
        for (RecordValue value : values.keySet())
            distributor.addSub(value);
        if (distributors_set.add(distributor))
            distributors = distributors_set.toArray(new HashDistributor[distributors_set.size()]);
        return distributor;
    }

    // ========== QDTicker Implementation ==========

    private int getRid(DataRecord record) {
        int rid = record.getId();
        if (records[rid] == record)
            return rid;
        throw new IllegalArgumentException("Unknown record.");
    }

    @Override
    public boolean isAvailable(DataRecord record, int cipher, String symbol) {
        getRid(record); // To check record validity.
        synchronized (this) {
            RecordValue value = getValue(record, cipher, symbol);
            return value != null && value.isAvailable();
        }
    }

    @Override
    public void remove(RecordSource source) {
        RecordCursor.Owner temp = RecordCursor.allocateOwner();
        synchronized (this) {
            for (RecordCursor cursor; (cursor = source.next()) != null;) {
                RecordValue value = getValue(cursor.getRecord(), cursor.getCipher(), cursor.getSymbol());
                if (value != null && value.isAvailable() && cursor.isDataIdenticalTo(value.getData(temp, RecordMode.DATA)))
                    value.setAvailable(false);
            }
        }
    }

    @Override
    public int getInt(DataIntField field, int cipher, String symbol) {
        getRid(field.getRecord()); // To check record validity.
        synchronized (this) {
            RecordValue value = getValue(field.getRecord(), cipher, symbol);
            return value == null || !value.isAvailable() ? 0 : value.getInt(field.getIndex());
        }
    }

    @Override
    public Object getObj(DataObjField field, int cipher, String symbol) {
        getRid(field.getRecord()); // To check record validity.
        synchronized (this) {
            RecordValue value = getValue(field.getRecord(), cipher, symbol);
            return value == null || !value.isAvailable() ? null : value.getObj(field.getIndex());
        }
    }

    @Override
    public void getData(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol) {
        getRid(record); // To check record validity.
        synchronized (this) {
            RecordValue value = getValue(record, cipher, symbol);
            if (value == null || !value.isAvailable())
                setEmpty(owner, record, cipher, symbol);
            else
                value.getData(owner, RecordMode.DATA);
        }
    }

    private static void setEmpty(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol) {
        owner.setReadOnly(true);
        owner.setRecord(record);
        owner.setSymbol(cipher, symbol);
        owner.setArrays(new int[record.getIntFieldCount()], new Object[record.getObjFieldCount()]);
        owner.setOffsets(0, 0);
    }

    @Override
    public boolean getDataIfAvailable(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol) {
        getRid(record); // To check record validity.
        synchronized (this) {
            RecordValue value = getValue(record, cipher, symbol);
            if (value == null || !value.isAvailable())
                return false;
            value.getData(owner, RecordMode.DATA);
            return true;
        }
    }

    @Override
    public boolean getDataIfSubscribed(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol) {
        getRid(record); // To check record validity.
        synchronized (this) {
            RecordValue value = getValue(record, cipher, symbol);
            if (value == null)
                return false;
            if (!value.isAvailable())
                setEmpty(owner, record, cipher, symbol);
            else
                value.getData(owner, RecordMode.DATA);
            return true;
        }
    }

    synchronized boolean isSub(DataRecord record, int cipher, String symbol, HashAgent agent) {
        RecordValue value = getValue(record, cipher, symbol);
        return value != null && value.containsAgent(agent);
    }

    boolean examineSnapshot(Set<RecordValue> values, boolean checkAvailable, RecordSink sink, RecordMode mode) {
        RecordValue[] snapshot;
        synchronized (this) {
            snapshot = values.toArray(new RecordValue[values.size()]);
        }
        RecordCursor.Owner temp = RecordCursor.allocateOwner();
        int nExaminedInBatch = 0;
        for (RecordValue value : snapshot) {
            if (checkAvailable && !value.isAvailable())
                continue;
            if (!sink.hasCapacity()) {
                if (nExaminedInBatch > 0)
                    sink.flush();
                return true;
            }
            sink.append(value.getData(temp, mode));
            nExaminedInBatch++;
            if (nExaminedInBatch >= EXAMINE_BATCH_SIZE) {
                sink.flush();
                nExaminedInBatch = 0;
            }
        }
        if (nExaminedInBatch > 0)
            sink.flush();
        return false;
    }

    @Override
    public synchronized boolean isSubscribed(DataRecord record, int cipher, String symbol, long time) {
        return getValue(record, cipher, symbol) != null;
    }

    @Override
    public boolean examineSubscription(RecordSink sink) {
        return examineSnapshot(values.keySet(), false, sink, RecordMode.DATA);
    }

    @Override
    public int getSubscriptionSize() {
        return values.size();
    }

    @Override
    public boolean examineData(RecordSink sink) {
        return examineSnapshot(values.keySet(), true, sink, RecordMode.DATA);
    }

    @Override
    public boolean examineDataBySubscription(RecordSink sink, RecordSource sub) {
        RecordCursor.Owner temp = RecordCursor.allocateOwner();
        int nExaminedInBatch = 0;
        for (RecordCursor cursor; (cursor = sub.next()) != null;) {
            RecordValue value;
            synchronized (this) {
                value = getValue(cursor.getRecord(), cursor.getCipher(), cursor.getSymbol());
            }
            if (value == null || !value.isAvailable())
                continue;
            if (!sink.hasCapacity()) {
                if (nExaminedInBatch > 0)
                    sink.flush();
                return true;
            }
            sink.append(value.getData(temp, RecordMode.DATA));
            nExaminedInBatch++;
            if (nExaminedInBatch >= EXAMINE_BATCH_SIZE) {
                sink.flush();
                nExaminedInBatch = 0;
            }
        }
        if (nExaminedInBatch > 0)
            sink.flush();
        return false;
    }

    @Override
    public void setErrorHandler(QDErrorHandler errorHandler) {
        this.error_handler = errorHandler;
    }

    @Override
    public void close() {
        // nothing to do
    }
}
