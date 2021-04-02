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
package com.devexperts.qd.stats;

import com.devexperts.qd.DataScheme;
import com.devexperts.util.ArrayUtil;
import com.devexperts.util.AtomicArrays;
import com.devexperts.util.JMXNameBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Statistics gathering delegate.
 */
public class QDStats {

    // ========== Utility Stuff ==========

    /**
     * The instance of QDStats that is always empty.
     */
    public static final QDStats VOID = new VoidQDStats(SType.VOID);

    /**
     * Returns an instance of {@link QDStats} that does not actually track anything (is always empty like {@link #VOID} but is named.
     */
    public static QDStats createNamedVoid(String key_properties) {
        return key_properties == null ? VOID : VOID.create(SType.VOID, key_properties);
    }

    // ========== MetaData ==========

    // Memory value kinds.
    protected static final int KIND_ADDED = 0;
    protected static final int KIND_CHANGED = 1;
    protected static final int KIND_FILTERED = 2;
    protected static final int KIND_REMOVED = 3;
    protected static final int KIND_SIZE = Integer.MAX_VALUE; // virtual index, equal to (KIND_ADDED - KIND_REMOVED)

    protected static final int MEM_EXTRA = 0; // no extra items
    protected static final int MEM_STRIDE = 4; // 4 items per record in FLAG_MEM

    // IO value kinds -- total only
    protected static final int KIND_IO_READ_RTTS = 0;
    protected static final int KIND_IO_WRITE_RTTS = 1;
    protected static final int KIND_IO_SUB_READ_RECORDS = 2;
    protected static final int KIND_IO_SUB_WRITE_RECORDS = 3;
    protected static final int KIND_IO_DATA_READ_RECORDS = 4;
    protected static final int KIND_IO_DATA_WRITE_RECORDS = 5;
    protected static final int KIND_IO_DATA_READ_LAGS = 6;
    protected static final int KIND_IO_DATA_WRITE_LAGS = 7;

    // IO value kinds -- supports rid
    protected static final int KIND_IO_READ_BYTES = 8;
    protected static final int KIND_IO_WRITE_BYTES = 9;

    protected static final int IO_EXTRA = 8; // 8 items extra
    protected static final int IO_STRIDE = 2; // 2 items per record with FLAG_IO (READ_BYTES and WRITE_BYTES)

    // Flags for each instance of QDStats
    public static final int FLAG_MEM = 1; // KIND_ADDED, KIND_CHANGED, KIND_FILTERED, KIND_REMOVED
    public static final int FLAG_IO = 2; // KIND_IO_xxx
    public static final int FLAG_RID = 4; // stores counters for each record separately
    public static final int FLAG_COLLECTION_OF_ANYTHING = 8;
    public static final int FLAG_COLLECTION_OF_SELF = 16;
    public static final int FLAG_COUNT = 32;

    /**
     * Defines specific statistic value, including its name and address.
     */
    public static final class SValue {
        private static int value_count;
        private static SValue[] values = new SValue[11];

        public static final SValue IO_READ_RTTS = new SValue("IOReadRtts", FLAG_IO, KIND_IO_READ_RTTS);
        public static final SValue IO_WRITE_RTTS = new SValue("IOWriteRtts", FLAG_IO, KIND_IO_WRITE_RTTS);
        public static final SValue IO_SUB_READ_RECORDS = new SValue("IOSubReadRecords", FLAG_IO, KIND_IO_SUB_READ_RECORDS);
        public static final SValue IO_SUB_WRITE_RECORDS = new SValue("IOSubWriteRecords", FLAG_IO, KIND_IO_SUB_WRITE_RECORDS);
        public static final SValue IO_DATA_READ_RECORDS = new SValue("IODataReadRecords", FLAG_IO, KIND_IO_DATA_READ_RECORDS);
        public static final SValue IO_DATA_WRITE_RECORDS = new SValue("IODataWriteRecords", FLAG_IO, KIND_IO_DATA_WRITE_RECORDS);
        public static final SValue IO_DATA_READ_LAGS = new SValue("IODataReadLags", FLAG_IO, KIND_IO_DATA_READ_LAGS);
        public static final SValue IO_DATA_WRITE_LAGS = new SValue("IODataWriteLags", FLAG_IO, KIND_IO_DATA_WRITE_LAGS);

        public static final SValue IO_READ_BYTES = new SValue("IOReadBytes", FLAG_IO, KIND_IO_READ_BYTES);
        public static final SValue IO_WRITE_BYTES = new SValue("IOWriteBytes", FLAG_IO, KIND_IO_WRITE_BYTES);

        public static final SValue RID_ADDED = new SValue("RecordAdded", FLAG_MEM, KIND_ADDED);
        public static final SValue RID_CHANGED = new SValue("RecordChanged", FLAG_MEM, KIND_CHANGED);
        public static final SValue RID_FILTERED = new SValue("RecordFiltered", FLAG_MEM, KIND_FILTERED);
        public static final SValue RID_REMOVED = new SValue("RecordRemoved", FLAG_MEM, KIND_REMOVED);
        public static final SValue RID_SIZE = new SValue("RecordSize", FLAG_MEM, KIND_SIZE);

        public static int getValueCount() {
            return value_count;
        }

        public static SValue getValue(int index) {
            return values[index];
        }

        private final String name;
        private final int flag;
        private final int kind;

        private SValue(String name, int flag, int kind) {
            this.name = name;
            this.flag = flag;
            this.kind = kind;

            if (value_count >= values.length)
                values = ArrayUtil.grow(values, 0);
            values[value_count++] = this;
        }

        public String getName() {
            return name;
        }

        public boolean isRid() {
            return isMem() ? kind >= MEM_EXTRA : kind >= IO_EXTRA;
        }

        public boolean isMem() {
            return flag == FLAG_MEM;
        }

        public boolean supportsFlag(int flag) {
            return (this.flag & flag) != 0;
        }

        public String toString() {
            return name;
        }

        int getKind() {
            return kind;
        }

        public static SValue valueOf(String name) {
            for (int i = 0; i < value_count; i++)
                if (name.equals(values[i].name))
                    return values[i];
            throw new IllegalArgumentException("SType name not found: " + name);
        }

    }

    /**
     * Defines specific statistic type, including its name.
     */
    public static class SType {
        public static final SType VOID = new SType("Void", 0);

        /**
         * This "magic" type gathers its grand-children's stats for any type of its child.
         * It should be used as a root only.
         */
        public static final SType ANY = new SType("Any", FLAG_COLLECTION_OF_ANYTHING);

        public static final SType UNIQUE_SUB = new SType("UniqueSub", FLAG_RID | FLAG_MEM);
        public static final SType STORAGE_DATA = new SType("StorageData", FLAG_RID | FLAG_MEM);

        public static final SType AGENT = new SType("Agent", 0);
        public static final SType AGENT_DATA = new SType("AgentData", FLAG_RID | FLAG_MEM);
        public static final SType AGENT_SUB = new SType("AgentSub", FLAG_RID | FLAG_MEM);

        public static final SType DISTRIBUTOR = new SType("Distributor", 0);
        public static final SType DISTRIBUTOR_ASUB = new SType("DistributorASub", FLAG_RID | FLAG_MEM);
        public static final SType DISTRIBUTOR_RSUB = new SType("DistributorRSub", FLAG_RID | FLAG_MEM);

        public static final SType TICKER = new SType("Ticker", FLAG_COLLECTION_OF_SELF, AGENT, DISTRIBUTOR);
        public static final SType STREAM = new SType("Stream", FLAG_COLLECTION_OF_SELF, AGENT, DISTRIBUTOR);
        public static final SType HISTORY = new SType("History", FLAG_COLLECTION_OF_SELF, AGENT, DISTRIBUTOR);

        public static final SType HTTP_CONNECTOR = new SType("HttpConnector", FLAG_RID | FLAG_IO);
        public static final SType QD_SERVLET = new SType("QDServlet", FLAG_RID | FLAG_IO);
        public static final SType CLIENT_SOCKET_CONNECTOR = new SType("ClientSocketConnector", FLAG_RID | FLAG_IO);
        public static final SType SERVER_SOCKET_CONNECTOR = new SType("ServerSocketConnector", FLAG_RID | FLAG_IO);
        public static final SType CONNECTION = new SType("Connection", FLAG_RID | FLAG_IO);
        public static final SType CONNECTIONS = new SType("Connections", FLAG_RID | FLAG_IO, CONNECTION);

        /** @deprecated Use {@link #CLIENT_SOCKET_CONNECTOR} */
        public static final SType CLIENT_SOCKET = CLIENT_SOCKET_CONNECTOR;

        protected final String name;
        protected final int flag;
        protected final List<SType> collection_of;

        public SType(String name, int flag, SType... collection_of) {
            this.name = name;
            this.flag = flag;
            this.collection_of = collection_of.length == 0 ? Collections.<SType>emptyList() :
                Arrays.asList(collection_of);
        }

        public String getName() {
            return name;
        }

        public int getFlag() {
            return flag;
        }

        public boolean isCollectionOf(SType type) {
            return (flag & FLAG_COLLECTION_OF_ANYTHING) != 0 ||
                collection_of.contains(type) ||
                (flag & FLAG_COLLECTION_OF_SELF) != 0 && type == this;
        }

        public boolean isMemType() {
            return (flag & FLAG_MEM) != 0;
        }

        public boolean isIOType() {
            return (flag & FLAG_IO) != 0;
        }

        public boolean isRidType() {
            return (flag & FLAG_RID) != 0;
        }

        public int getExtra() {
            if (isIOType())
                return IO_EXTRA;
            else if (isMemType())
                return MEM_EXTRA;
            else  // Note that VOID has no flags set and also uses largest stride by design (to be all-inclusive)
                return Math.max(IO_EXTRA, MEM_EXTRA);
        }

        public int getStride() {
            if (isIOType())
                return IO_STRIDE;
            else if (isMemType())
                return MEM_STRIDE;
            else // Note that VOID has no flags set and also uses largest stride by design (to be all-inclusive)
                return Math.max(IO_STRIDE, MEM_STRIDE);
        }

        public String toString() {
            return name;
        }

    }

    // ========== Data Structures: Initialization and Management ==========

    protected static final class StatsLock {} // Named lock class for debugging (stacktraces).
    protected static final QDStats[] EMPTY_CHILDREN = new QDStats[0];

    private QDStats parent; // (parent == null) for root.
    private StatsLock lock; // (lock != null && lock == parent.lock) always.
    private SType type; // (type != null) always.
    private int rid_count; // (rid_count == parent.rid_count) always.
    private DataScheme scheme;  // (scheme == parent.scheme) always.
    private boolean closed;

    private int rid_stride; // how many counters per RID are kept
    private long[] stats;

    private String key_properties;
    private boolean sum_mode;

    private volatile QDStats[] children = EMPTY_CHILDREN;
    private int last_child_index; // index in the children array where last child was added
    private int parent_child_index = -1; // index of this child in parent.children array
    private int uncle_child_index = -1; // index of this child in 2nd parent children array

    // ----- Access: No SYNC is required, atomic reads are required.

    protected QDStats getParent() { // :todo:
        return parent;
    }

    protected Object getLock() { // :todo:
        return lock;
    }

    /**
     * Returns type of this statistics instance.
     */
    public SType getType() {
        return type;
    }

    public int getRidCount() {
        return rid_count;
    }

    public DataScheme getScheme() {
        return scheme;
    }

    protected QDStats[] getChildren() { // :todo: May not be modified!!! Requires atomic reads!!!
        return children;
    }

    /**
     * Returns key properties that are specific to this particular instance of QDStats as
     * they were specified during initialization of this instance. Use
     * {@link #getFullKeyProperties()} if you need a complete key properties, including
     * properties inherited from parent stats.
     */
    public String getKeyProperties() {
        return key_properties;
    }

    /**
     * Returns full key properties including all parents starting from oldest ones.
     * Returns an empty string if there are no key properties.
     */
    public String getFullKeyProperties() {
        List<QDStats> list = new ArrayList<QDStats>();
        for (QDStats stats = this; stats != null; stats = stats.getParent())
            list.add(stats);
        JMXNameBuilder nb = new JMXNameBuilder();
        for (int i = list.size(); --i >= 0;)
            nb.appendKeyProperties(list.get(i).key_properties);
        return nb.toString();
    }

    protected boolean isSumMode() {
        return sum_mode;
    }

    protected boolean hasSum(QDStats parent, QDStats child) {
        return parent.type.isCollectionOf(child.type);
    }

    // ----- Stats Management: SYNC is required (or single-use during construction).

    private void initStats(QDStats parent, StatsLock lock, SType type, int rid_count) {
        if (this.lock != null)
            throw new IllegalStateException("Already initialized");
        if (lock == null)
            throw new NullPointerException("lock is null");
        if (type == null)
            throw new NullPointerException("type is null");
        if (rid_count < 0)
            throw new IllegalArgumentException("rid_count is out of range");
        if (parent != null && lock != parent.lock)
            throw new IllegalArgumentException("lock does not match parent.lock");
        if (parent != null && (rid_count != 0 && rid_count != parent.rid_count))
            throw new IllegalArgumentException("non-zero rid_count does not match parent.rid_count");

        this.parent = parent;
        this.lock = lock;
        this.type = type;
        this.rid_count = rid_count;

        rid_stride = type.isRidType() && rid_count > 0 ? type.getStride() : 0;

        if (type == SType.VOID && parent != null) {
            stats = parent.stats; // void does not allocate extra arrays
        } else {
            stats = new long[type.getExtra() + type.getStride() + rid_count * rid_stride];
        }
    }

    protected void initStats(QDStats parent, StatsLock lock, SType type, DataScheme scheme) {
        initStats(parent, lock, type, scheme == null ? 0 : scheme.getRecordCount());
        this.scheme = scheme;
    }

    // "Closes" this stats (e.g. updates it to 'garbage').
    protected void closeStats() {
        // :TODO: Maybe we shall report error (or ignore) if stats are not actually closed?
        if (type.isMemType() && type.isRidType()) {
            for (int i = 0; i <= rid_count; i++)
                stats[i * rid_stride + KIND_REMOVED] = stats[i * rid_stride + KIND_ADDED];
        }
    }

    protected void addClosedStats(QDStats child) {
        if ((type.flag & (FLAG_IO | FLAG_MEM)) != (child.type.flag & (FLAG_IO | FLAG_MEM)))
            return; // incompatible types
        long[] childStats = child.stats;
        // add common non-rid parts
        int typeExtra = type.getExtra();
        int typeStride = type.getStride();
        for (int i = 0; i < typeExtra + typeStride; i++)
            stats[i] += childStats[i];
        // add rid parts
        if (child.rid_stride > 0)
            for (int i = 0; i < child.rid_count; i++)
                for (int j = 0; j < child.rid_stride; j++)
                    stats[typeExtra + (i + 1) * rid_stride + j] += childStats[typeExtra + (i + 1) * child.rid_stride + j];
    }

    // ----- Child Management: SYNC is required.

    // SYNC: lock
    protected void addChild(QDStats child) {
        if (children == EMPTY_CHILDREN) {
            children = new QDStats[2]; // Optimized for Agent.
        }
        last_child_index = ArrayUtil.findFreeIndex(children, last_child_index, 0);
        if (last_child_index >= children.length)
            children = ArrayUtil.grow(children, 0);
        if (child.parent_child_index < 0) {
            children[child.parent_child_index = last_child_index] = child;
        } else if (child.uncle_child_index < 0)
            children[child.uncle_child_index = last_child_index] = child;
        else
            throw new IllegalStateException("too many parents");
    }

    // Returns true if child was actually removed.
    protected boolean removeChild(QDStats child) {
        if (child.parent_child_index >= 0 && child.parent_child_index < children.length && children[child.parent_child_index] == child) {
            children[child.parent_child_index] = null;
            return true;
        }
        if (child.uncle_child_index >= 0 && child.uncle_child_index < children.length && children[child.uncle_child_index] == child) {
            children[child.uncle_child_index] = null;
            return true;
        }
        return false;
    }

    // "Closes" children of this stats (e.g. nulls it and updates it to 'garbage').
    // Recursive (via closeInternal)!!!
    protected void closeChildren() {
        if (children == EMPTY_CHILDREN)
            return;
        for (QDStats child : children)
            if (child != null)
                closeInternal(child);
        children = EMPTY_CHILDREN;
    }

    // ----- Child Instantiation & Management: SYNC is required.

    protected QDStats newInstance(SType type, boolean unmanaged) {
        return new QDStats();
    }

    // SYNC: lock
    protected QDStats createInternal(SType type, String key_properties, boolean sum_mode, int rid_count, DataScheme scheme) {
        if (closed)
            return VOID; // do not create children for closed stats, but return VOID stats
        QDStats child = newInstance(type, key_properties == null && !sum_mode);
        child.sum_mode = sum_mode;
        initChild(child, type, key_properties, rid_count, scheme);
        addChild(child);
        if (parent != null && hasSum(parent, this)) {
            QDStats pc = parent.get(type);
            if (pc == null) {
                // Note, that sum node inherits rid_count and scheme from parent (even if this children's rid_count is zero).
                pc = parent.createInternal(type, null, true, parent.rid_count, parent.scheme);
            }
            pc.addChild(child);
        }
        return child;
    }

    // SYNC: lock
    protected void initChild(QDStats child, SType type, String key_properties, int rid_count, DataScheme scheme) {
        JMXNameBuilder.validateKeyProperties(key_properties);
        child.key_properties = key_properties;
        if (scheme != null)
            child.initStats(this, lock, type, scheme);
        else
            child.initStats(this, lock, type, rid_count);
    }

    // Must be called on the same instance that created specified child.
    protected void closeInternal(QDStats child) {
        if (child.parent != this || !removeChild(child))
            return;

        child.closeImpl();
        child.closeChildren(); // Recursive (via closeInternal)!!!
        child.closeStats();
        child.parent = null;

        if (parent != null && hasSum(parent, this)) {
            QDStats pc = parent.get(child.type);
            if (pc != null && pc.removeChild(child)) {
                pc.addClosedStats(child);
                return;
            }
        }
        addClosedStats(child);
    }

    // ========== Gathering API ==========

    // invoked for each parsed packet
    public final void updateIOReadBytes(long bytes) {
        stats[KIND_IO_READ_BYTES] += bytes;
    }

    public final void updateIOReadRecordBytes(int rid, long bytes) {
        stats[KIND_IO_READ_BYTES + (rid + 1) * rid_stride] += bytes;
        stats[KIND_IO_READ_BYTES] -= bytes; // to prevent double accounting of record bytes
    }

    public final void updateIOReadSubRecord() {
        stats[KIND_IO_SUB_READ_RECORDS]++;
    }

    public final void updateIOReadDataRecord() {
        stats[KIND_IO_DATA_READ_RECORDS]++;
    }

    public final void updateIOReadRtts(int rtt) {
        stats[KIND_IO_READ_RTTS] += rtt;
    }

    public final void updateIOReadDataLags(long sumLag) {
        stats[KIND_IO_DATA_READ_LAGS] += sumLag;
    }

    // invoked for each composed packet
    public final void updateIOWriteBytes(long bytes) {
        stats[KIND_IO_WRITE_BYTES] += bytes;
    }

    public final void updateIOWriteRecordBytes(int rid, long bytes) {
        stats[KIND_IO_WRITE_BYTES + (rid + 1) * rid_stride] += bytes;
        stats[KIND_IO_WRITE_BYTES] -= bytes; // to prevent double accounting of record bytes
    }

    public final void updateIOWriteSubRecord() {
        stats[KIND_IO_SUB_WRITE_RECORDS]++;
    }

    public final void updateIOWriteDataRecord() {
        stats[KIND_IO_DATA_WRITE_RECORDS]++;
    }

    public final void updateIOWriteRtts(int rtt) {
        stats[KIND_IO_WRITE_RTTS] += rtt;
    }

    public final void updateIOWriteDataLags(long sumLag) {
        stats[KIND_IO_DATA_WRITE_LAGS] += sumLag;
    }

    public final void updateAdded(int rid) {
        stats[KIND_ADDED + (rid + 1) * rid_stride]++;
    }

    public final void updateChanged(int rid) {
        stats[KIND_CHANGED + (rid + 1) * rid_stride]++;
    }

    public final void updateFiltered(int rid) {
        stats[KIND_FILTERED + (rid + 1) * rid_stride]++;
    }

    public final void updateRemoved(int rid) {
        stats[KIND_REMOVED + (rid + 1) * rid_stride]++;
    }

    public final void updateRemoved(int rid, int count) {
        stats[KIND_REMOVED + (rid + 1) * rid_stride] += count;
    }

    // ========== Retrieval API ==========

    public long getValue(SValue value, boolean localOnly) {
        if (value.getKind() == KIND_SIZE) {
            // read removed first to avoid negative sizes, see QD-447
            long removed = getValueImpl(SValue.RID_REMOVED, localOnly);
            return getValueImpl(SValue.RID_ADDED, localOnly) - removed;
        }
        return getValueImpl(value, localOnly);
    }

    private long getValueImpl(SValue value, boolean localOnly) {
        long v = AtomicArrays.INSTANCE.getVolatileLong(stats, value.getKind());
        if (value.isRid())
            for (int i = rid_count; --i >= 0;)
                v += AtomicArrays.INSTANCE.getVolatileLong(stats, value.getKind() + (i + 1) * rid_stride);
        if (localOnly)
            return v;
        QDStats[] children = this.children; // Atomic read.
        for (QDStats child : children) {
            if (child != null)
                v += child.getValueImpl(value, hasSum(this, child) && !sum_mode);
        }
        return v;
    }

    public void addValues(SValue value, boolean localOnly, long[] dest) {
        if (!value.isRid())
            throw new IllegalArgumentException("this method is only for rid values");
        if (value.getKind() == KIND_SIZE)
            for (int i = rid_count; --i >= 0;) {
                // read removed first to avoid negative sizes, see QD-447
                long removed = AtomicArrays.INSTANCE.getVolatileLong(stats, KIND_REMOVED + (i + 1) * rid_stride);
                dest[i] += AtomicArrays.INSTANCE.getVolatileLong(stats, KIND_ADDED + (i + 1) * rid_stride) - removed;
            }
        else
            for (int i = rid_count; --i >= 0;)
                dest[i] += AtomicArrays.INSTANCE.getVolatileLong(stats, value.getKind() + (i + 1) * rid_stride);
        if (localOnly)
            return;
        QDStats[] children = this.children; // Atomic read.
        for (QDStats child : children) {
            if (child != null)
                child.addValues(value, hasSum(this, child) && !sum_mode, dest);
        }
    }

    // ========== Retrieval API convenience methods ==========

    public long getValue(SValue value) {
        return getValue(value, false);
    }

    // ========== Constructors ==========

    /**
     * Creates uninitialized stats.
     * {@link #initRoot} or {@link #initStats} must be called after construction.
     */
    public QDStats() {
    }

    /**
     * Creates uninitialized stats with a specified key properties.
     * {@link #initRoot} or {@link #initStats} must be called after construction.
     * @throws IllegalArgumentException if key properties have invalid format.
     */
    public QDStats(String key_properties) {
        JMXNameBuilder.validateKeyProperties(key_properties);
        this.key_properties = key_properties;
    }

    /**
     * Creates root stats with given <code>type</code>.
     */
    public QDStats(SType type) {
        initRoot(type, null);
    }

    public QDStats(SType type, DataScheme scheme) {
        initRoot(type, scheme);
    }

    // ========== Management API ==========

    /**
     * Initializes root statistics after instantiation using no-arg constructor.
     * @deprecated Use {@link #initRoot(com.devexperts.qd.stats.QDStats.SType, com.devexperts.qd.DataScheme)} instead.
     */
    public void initRoot(SType type, int rid_count) {
        initStats(null, new StatsLock(), type, rid_count);
    }

    public void initRoot(SType type, DataScheme scheme) {
        initStats(null, new StatsLock(), type, scheme);
    }

    /**
     * Returns first found child with the corresponding type or <code>null</code> if not found.
     */
    public QDStats get(SType type) {
        QDStats[] children = this.children; // Atomic read.
        for (QDStats child : children) {
            if (child != null && child.type == type)
                return child;
        }
        return null;
    }

    /**
     * Returns first found child with the corresponding type or {@link #create creates} new
     * one if not found. The result is {@link #VOID} for closed or void stats.
     */
    public QDStats getOrCreate(SType type) {
        synchronized (lock) {
            QDStats child = get(type);
            if (child == null)
                child = create(type);
            return child;
        }
    }

    /**
     * Returns first found child with the corresponding type or {@link #VOID VOID} if not found.
     */
    public QDStats getOrVoid(SType type) {
        QDStats child = get(type);
        if (child == null)
            child = VOID;
        return child;
    }

    /**
     * Creates and returns new child with the corresponding type.
     * The result is {@link #VOID} for closed or void stats.
     */
    public final QDStats create(SType type) {
        return create(type, null);
    }

    /**
     * Creates and returns new child with the corresponding type and key properties.
     * This children stats will inherit per-record stats tracking from parent.
     * The result is {@link #VOID} for closed or void stats.
     *
     * @param type The type of this stats.
     * @param key_properties The JMX key=value list of properties for naming of JMX beans (when JMX stats are used).
     */
    public final QDStats create(SType type, String key_properties) {
        return create(type, key_properties, true);
    }

    /**
     * Creates and returns new child with the corresponding type, key properties,
     * and an optional per-record tracking of stats.
     * The result is {@link #VOID} for closed or void stats.
     *
     * @param type The type of this stats.
     * @param key_properties The JMX key=value list of properties for naming of JMX beans (when JMX stats are used).
     * @param useRid when true, then per-record stats tracking is inherited from parent, when false it is turned off
     *               in the created stats and in all its children.
     */
    public QDStats create(SType type, String key_properties, boolean useRid) {
        synchronized (lock) {
            return createInternal(type, key_properties, false,
                useRid ? rid_count : 0, useRid ? scheme : null); // reset scheme and rid if per-record stats are not tracked
        }
    }

    // Closes this stats and all its children.
    // Re-enterable.
    public void close() {
        synchronized (lock) {
            if (closed)
                return;
            closed = true;
            if (parent != null) {
                parent.closeInternal(this);
            } else {
                closeImpl();
                closeChildren();
            }
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{type=" + type +
            (sum_mode ? ",sum" : "") +
            (closed ? ",closed" : "") +
            "}[" + getFullKeyProperties() + "]";
    }

    // override in descendants
    protected void closeImpl() {}

    // override in descendants
    public void addMBean(String type, Object mbean) {}

    /**
     * Quotes key property value so that it can be safely used in <code>key_properties</code> of
     * {@link #create(com.devexperts.qd.stats.QDStats.SType, String) create(type, key_properties)}.
     * @deprecated Use {@link JMXNameBuilder#quoteKeyPropertyValue(String)}
     */
    public static String quoteKeyPropertyValue(String value) {
        return JMXNameBuilder.quoteKeyPropertyValue(value);
    }

    private static class VoidQDStats extends QDStats {
        VoidQDStats() {}

        VoidQDStats(SType type) {
            super(type);
        }

        @Override
        public long getValue(SValue value, boolean localOnly) {
            return 0;
        }

        @Override
        public void addValues(SValue value, boolean localOnly, long[] dest) {}

        @Override
        public QDStats get(SType type) {
            return this;
        }

        @Override
        public QDStats create(SType type, String key_properties, boolean useRid) {
            if (key_properties == null)
                return VOID;
            if (key_properties.equals(getKeyProperties()))
                return this;
            VoidQDStats result = new VoidQDStats();
            initChild(result, SType.VOID, key_properties, 0, null);
            return result;
        }

        @Override
        public void close() {}
    }
}
