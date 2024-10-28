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
package com.devexperts.qd.stats;

import com.devexperts.qd.DataScheme;
import com.devexperts.util.ArrayUtil;
import com.devexperts.util.AtomicArrays;
import com.devexperts.util.IndexedSet;
import com.devexperts.util.JMXNameBuilder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Statistics gathering delegate.
 *
 * <p>Statistics can be gathered per different {@link SType type} per set of {@link SValue values}.
 * Value for stat is calculated as a sum of all its children nodes.
 *
 * <p>Additionally, special "sum" nodes exist, that aggregate only values for the {@link SType#isSameAs(SType) same}
 * type. Sum nodes are used to accumulate values for closed stats and to aggregate similar type stats using
 * "alternative" hierarchy. Sum nodes are created for each {@link SType type} and propagated up to the stat's root.
 * Getting value from the sum node would aggregate only similar type stats and can be more meaningful.
 *
 * <p>Consider the following stats example:
 * <pre>
 * Any
 *   Box={name=BigBox}
 *     Box={name=Box1}
 *       Apple={name=apple1}
 *       Orange={name=orange1}
 *     Box={name=Box2}
 *       Apple={name=apple2}
 *       Orange={name=orange2}
 *     #Apple{sum}
 *     #Orange{sum}
 *   #Apple{sum}
 *   #Orange{sum}</pre>
 *   
 * <p>In the example above box stats contain 2 other boxes keeping apples and oranges. When getting value from any
 * of the box would aggregate statistics for all fruits. Additionally, sum nodes are created for {@code Apple} and
 * {@code Orange} types and it is possible to calculate stats only for apples or oranges.
 * Boxes inside a box represent the "same" type hierarchy, so no sum node is needed and if either "Box1" or
 * "box2" would be closed then its stats would be accumulated in the "BigBox".
 *
 * <p><b>Use with caution!</b> This API is old and has contradictory contract due to backward compatibility
 * and therefore is subject to change in the future.
 */
public class QDStats {

    // ========== Utility Stuff ==========

    protected static final QDStats[] EMPTY_CHILDREN = new QDStats[0];

    /**
     * The instance of QDStats that is always empty.
     */
    public static final QDStats VOID = new VoidQDStats(SType.VOID);

    /**
     * Returns an instance of {@link QDStats} that does not actually track anything
     * (is always empty like {@link #VOID} but is named).
     */
    @SuppressWarnings("unused")
    public static QDStats createNamedVoid(String keyProperties) {
        return keyProperties == null ? VOID : VOID.create(SType.VOID, keyProperties);
    }

    // ========== MetaData ==========

    // Memory value kinds.
    protected static final int KIND_ADDED = 0;
    protected static final int KIND_CHANGED = 1;
    protected static final int KIND_FILTERED = 2;
    protected static final int KIND_REMOVED = 3;
    protected static final int KIND_SIZE = Integer.MAX_VALUE; // Virtual index, equal to (KIND_ADDED - KIND_REMOVED)

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
    @Deprecated
    public static final int FLAG_COLLECTION_OF_ANYTHING = 8;
    @Deprecated
    public static final int FLAG_COLLECTION_OF_SELF = 16;
    public static final int FLAG_COUNT = 32;

    /**
     * Defines specific statistic value, including its name and address.
     */
    public static final class SValue {
        private static int valueCount;
        private static SValue[] values = new SValue[11];

        public static final SValue IO_READ_RTTS = new SValue("IOReadRtts", FLAG_IO, KIND_IO_READ_RTTS);
        public static final SValue IO_WRITE_RTTS =
            new SValue("IOWriteRtts", FLAG_IO, KIND_IO_WRITE_RTTS);
        public static final SValue IO_SUB_READ_RECORDS =
            new SValue("IOSubReadRecords", FLAG_IO, KIND_IO_SUB_READ_RECORDS);
        public static final SValue IO_SUB_WRITE_RECORDS =
            new SValue("IOSubWriteRecords", FLAG_IO, KIND_IO_SUB_WRITE_RECORDS);
        public static final SValue IO_DATA_READ_RECORDS =
            new SValue("IODataReadRecords", FLAG_IO, KIND_IO_DATA_READ_RECORDS);
        public static final SValue IO_DATA_WRITE_RECORDS =
            new SValue("IODataWriteRecords", FLAG_IO, KIND_IO_DATA_WRITE_RECORDS);
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
            return valueCount;
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

            if (valueCount >= values.length)
                values = ArrayUtil.grow(values, 0);
            values[valueCount++] = this;
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
            for (int i = 0; i < valueCount; i++) {
                if (name.equals(values[i].name))
                    return values[i];
            }
            throw new IllegalArgumentException("SValue name not found: " + name);
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
        public static final SType ANY = new SType("Any");

        public static final SType UNIQUE_SUB = new SType("UniqueSub", FLAG_RID | FLAG_MEM);
        public static final SType STORAGE_DATA = new SType("StorageData", FLAG_RID | FLAG_MEM);
        public static final SType DROPPED_DATA = new SType("DroppedData", FLAG_RID | FLAG_MEM);

        public static final SType AGENT = new SType("Agent");
        public static final SType AGENT_DATA = new SType("AgentData", FLAG_RID | FLAG_MEM);
        public static final SType AGENT_SUB = new SType("AgentSub", FLAG_RID | FLAG_MEM);

        public static final SType DISTRIBUTOR = new SType("Distributor");
        public static final SType DISTRIBUTOR_ASUB = new SType("DistributorASub", FLAG_RID | FLAG_MEM);
        public static final SType DISTRIBUTOR_RSUB = new SType("DistributorRSub", FLAG_RID | FLAG_MEM);

        public static final SType TICKER = new SType("Ticker");
        public static final SType STREAM = new SType("Stream");
        public static final SType HISTORY = new SType("History");

        public static final SType CLIENT_SOCKET_CONNECTOR = new SType("ClientSocketConnector", FLAG_RID | FLAG_IO);
        public static final SType SERVER_SOCKET_CONNECTOR = new SType("ServerSocketConnector", FLAG_RID | FLAG_IO);
        public static final SType CONNECTION = new SType("Connection", FLAG_RID | FLAG_IO);
        public static final SType CONNECTIONS = new SType("Connections", FLAG_RID | FLAG_IO, CONNECTION);

        protected final String name;
        protected final int flag;

        // Establishes total equivalence relation among all types
        protected final SType eqType;

        public SType(String name) {
            this(name, 0);
        }

        /**
         * Creates type with the given {@code name} and {@code flag} and which is equivalent to the listed types.
         *
         * @param name type's name
         * @param flag describes which value kinds this type contains
         * @param sameAs list of equivalent types (all types must be equivalent to each other)
         */
        public SType(String name, int flag, SType... sameAs) {
            this.name = name;
            this.flag = flag;

            if (sameAs.length == 0) {
                this.eqType = this;
            } else if (Arrays.stream(sameAs).map(t -> t.eqType).distinct().count() > 1) {
                throw new IllegalArgumentException("Types are not equivalent: " + Arrays.toString(sameAs));
            } else {
                this.eqType = sameAs[0].eqType;
            }
        }

        public String getName() {
            return name;
        }

        public int getFlag() {
            return flag;
        }

        /** @deprecated use {@link #isSameAs(SType)} instead. */
        @Deprecated
        public boolean isCollectionOf(SType type) {
            return isSameAs(type);
        }

        /**
         * Returns {@code true} if the given type is "same" (i.e. equivalent) to this type.
         * @param type stat's type
         * @return {@code true} if types are the same or similar.
         */
        public boolean isSameAs(SType type) {
            return type.eqType == eqType;
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
            if (isIOType()) {
                return IO_EXTRA;
            } else if (isMemType()) {
                return MEM_EXTRA;
            } else {
                // Note that VOID has no flags set and also uses largest stride by design (to be all-inclusive)
                return Math.max(IO_EXTRA, MEM_EXTRA);
            }
        }

        public int getStride() {
            if (isIOType()) {
                return IO_STRIDE;
            } else if (isMemType()) {
                return MEM_STRIDE;
            } else {
                // Note that VOID has no flags set and also uses largest stride by design (to be all-inclusive)
                return Math.max(IO_STRIDE, MEM_STRIDE);
            }
        }

        public String toString() {
            return name;
        }
    }

    // ========== Data Structures: Initialization and Management ==========

    // Named lock class for debugging (stack traces).
    protected static final class StatsLock {}

    /*
     * QDStats internal structure and invariants:
     *
     * - All stats (except root) have a non-null parent node.
     * - All stats (except root and its children) belong to a sum node of the "same" type.
     * - Sum nodes only contain references to the single type.
     * - Sum node is either parent (if they are "same") or it is it's "uncle", i.e. sibling of the parent.
     * - There cannot be more than one sum node for a given type in each node.
     * - Getting the value from a node would aggregate over all its descendants
     * - Getting the value from a sum node would aggregate only for the type of the sum node.
     */

    private QDStats parent; // (parent == null) for root only.
    private QDStats uncle; // separate sum node; (uncle == null) for root or when parent is sum node
    private StatsLock lock; // (lock != null && lock == parent.lock) always.
    private int ridCount; // (ridCount == 0 || ridCount == parent.ridCount) always.
    private DataScheme scheme;  // (scheme == null || scheme == parent.scheme) always.

    private SType type; // (type != null) always.
    private String keyProperties;
    private boolean closed;
    private boolean isSum;

    private int ridStride; // How many counters per RID are kept
    private long[] stats;

    private volatile QDStats[] children = EMPTY_CHILDREN;
    private IndexedSet<SType, QDStats> sumRefs = null;

    // Index in the children array where last child was added
    private int lastChildIndex;
    // Index of this child in parent's children array
    private int parentChildIndex = -1;
    // Index of this child in uncle's children array. Used if (uncle != null)
    private int uncleChildIndex = -1;

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
        return ridCount;
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
        return keyProperties;
    }

    /**
     * Returns full key properties including all parents starting from oldest ones.
     * Returns an empty string if there are no key properties.
     */
    public String getFullKeyProperties() {
        List<QDStats> list = new ArrayList<>();
        for (QDStats stats = this; stats != null; stats = stats.getParent()) {
            list.add(stats);
        }
        JMXNameBuilder nb = new JMXNameBuilder();
        for (int i = list.size(); --i >= 0;) {
            nb.appendKeyProperties(list.get(i).keyProperties);
        }
        return nb.toString();
    }

    protected boolean isSum() {
        return isSum;
    }

    protected boolean isSumFor(QDStats child) {
        return type.isSameAs(child.type);
    }

    // ----- Stats Management: SYNC is required (or single-use during construction).

    private void initStats(QDStats parent, StatsLock lock, SType type, int ridCount, DataScheme scheme) {
        if (this.parent != null && this.parent != parent)
            throw new IllegalStateException("parent is already initialized");
        if (this.lock != null)
            throw new IllegalStateException("already initialized");
        if (lock == null)
            throw new NullPointerException("lock is null");
        if (type == null)
            throw new NullPointerException("type is null");
        if (ridCount < 0)
            throw new IllegalArgumentException("ridCount is out of range");
        if (parent != null && lock != parent.lock)
            throw new IllegalArgumentException("lock does not match parent.lock");
        if (parent != null && (scheme != null && scheme != parent.scheme))
            throw new IllegalArgumentException("non-null scheme does not match parent.scheme");

        // Scheme record count has higher priority if scheme is specified
        int count = (scheme != null) ? scheme.getRecordCount() : ridCount;
        if (parent != null && (count != 0 && count != parent.ridCount))
            throw new IllegalArgumentException("non-zero ridCount does not match parent.ridCount");

        this.parent = parent;
        this.lock = lock;
        this.type = type;
        this.ridCount = count;
        this.scheme = scheme;

        ridStride = type.isRidType() && count > 0 ? type.getStride() : 0;

        if (type == SType.VOID && parent != null) {
            stats = parent.stats; // void does not allocate extra arrays
        } else {
            stats = new long[type.getExtra() + type.getStride() + count * ridStride];
        }
    }

    @Deprecated
    protected void initStats(QDStats parent, StatsLock lock, SType type, DataScheme scheme) {
        initStats(parent, lock, type, 0, scheme);
    }

    // "Closes" this stats object (e.g. updates it to 'garbage').
    protected void closeStats() {
        // :TODO: Maybe we shall report error (or ignore) if stats are not actually closed?
        if (type.isMemType() && type.isRidType()) {
            for (int i = 0; i <= ridCount; i++) {
                stats[i * ridStride + KIND_REMOVED] = stats[i * ridStride + KIND_ADDED];
            }
        }
    }

    protected void addClosedStats(QDStats child) {
        if ((type.flag & (FLAG_IO | FLAG_MEM)) != (child.type.flag & (FLAG_IO | FLAG_MEM)))
            return; // incompatible types

        long[] childStats = child.stats;
        // add common non-rid parts
        int typeExtra = type.getExtra();
        int typeStride = type.getStride();
        for (int i = 0; i < typeExtra + typeStride; i++) {
            stats[i] += childStats[i];
        }
        // add rid parts
        if (child.ridStride > 0) {
            for (int i = 0; i < child.ridCount; i++) {
                for (int j = 0; j < child.ridStride; j++) {
                    stats[typeExtra + (i + 1) * ridStride + j] += childStats[typeExtra + (i + 1) * child.ridStride + j];
                }
            }
        }
    }

    // ----- Child Management: SYNC is required.

    // SYNC: lock
    protected int addChild(QDStats child) {
        if (children == EMPTY_CHILDREN) {
            children = new QDStats[2]; // Optimized for Agent.
        }

        lastChildIndex = ArrayUtil.findFreeIndex(children, lastChildIndex, 0);
        if (lastChildIndex >= children.length) {
            children = ArrayUtil.grow(children, 0);
        }
        children[lastChildIndex] = child;
        return lastChildIndex;
    }

    protected void removeChild(QDStats child, int childIndex) {
        if (childIndex >= 0 && childIndex < children.length && children[childIndex] == child)
            children[childIndex] = null;
    }

    // "Closes" children of this stats object (e.g. nulls it and updates it to 'garbage').
    // Recursive (via closeInternal)!!!
    protected void closeChildren() {
        if (children == EMPTY_CHILDREN)
            return;
        for (QDStats child : children) {
            if (child != null)
                child.close();
        }
        children = EMPTY_CHILDREN;
        sumRefs = null;
    }

    // ----- Child Instantiation & Management: SYNC is required.

    protected QDStats newInstance(SType type, boolean unmanaged) {
        return new QDStats();
    }

    // SYNC: lock
    protected QDStats createInternal(SType type, String keyProperties, boolean isSum, int ridCount, DataScheme scheme) {
        // Do not create children for closed stats, but return VOID stats
        if (closed)
            return VOID;

        QDStats child = newInstance(type, keyProperties == null && !isSum);
        initChild(child, type, keyProperties, ridCount, scheme);
        child.isSum = isSum;

        // Add to parent's tree
        child.parent = this;
        child.parentChildIndex = addChild(child);

        // Create sum node for the child node up a tree when the current node is not summing child stats itself.
        if (parent != null && !isSumFor(child)) {
            QDStats uncle = parent.getOrCreateSum(type);

            // Add to uncle's tree
            child.uncle = uncle;
            child.uncleChildIndex = uncle.addChild(child);
        }
        return child;
    }

    // SYNC: lock
    protected QDStats getOrCreateSum(SType type) {
        // Check if sum node is already present.
        if (sumRefs != null && sumRefs.containsKey(type))
            return sumRefs.getByKey(type);

        // Recursive call since createInternal can call getOrCreateSumNode
        QDStats sum = createInternal(type, null, true, ridCount, scheme);

        // Register sum node in the refs set for fast lookup.
        if (sumRefs == null)
            sumRefs = IndexedSet.create(QDStats::getType);
        sumRefs.add(sum);

        return sum;
    }

    // SYNC: lock
    protected void closeInternal() {
        if (closed)
            return;
        closed = true;

        // Called before children are processed
        closeImpl();
        // Sum nodes behave as ordinary nodes, i.e. they close all their children.
        // This is because sum nodes should be closed only when their parent stat is closed.
        closeChildren();

        closeStats();
        if (uncle != null) {
            uncle.addClosedStats(this);
        } else if (parent != null) {
            parent.addClosedStats(this);
        }

        if (uncle != null) {
            uncle.removeChild(this, uncleChildIndex);
            uncle = null;
            uncleChildIndex = -1;
        }
        if (parent != null) {
            if (isSum() && parent.sumRefs != null)
                parent.sumRefs.removeValue(this);

            parent.removeChild(this, parentChildIndex);
            parent = null;
            parentChildIndex = -1;
        }
    }

    // SYNC: lock
    protected void initChild(QDStats child, SType type, String keyProperties, int ridCount, DataScheme scheme) {
        JMXNameBuilder.validateKeyProperties(keyProperties);
        child.keyProperties = keyProperties;
        child.initStats(this, lock, type, ridCount, scheme);
    }

    // ========== Gathering API ==========

    // invoked for each parsed packet
    public final void updateIOReadBytes(long bytes) {
        stats[KIND_IO_READ_BYTES] += bytes;
    }

    public final void updateIOReadRecordBytes(int rid, long bytes) {
        stats[KIND_IO_READ_BYTES + (rid + 1) * ridStride] += bytes;
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
        stats[KIND_IO_WRITE_BYTES + (rid + 1) * ridStride] += bytes;
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
        stats[KIND_ADDED + (rid + 1) * ridStride]++;
    }

    public final void updateChanged(int rid) {
        stats[KIND_CHANGED + (rid + 1) * ridStride]++;
    }

    public final void updateFiltered(int rid) {
        stats[KIND_FILTERED + (rid + 1) * ridStride]++;
    }

    public final void updateRemoved(int rid) {
        stats[KIND_REMOVED + (rid + 1) * ridStride]++;
    }

    public final void updateRemoved(int rid, int count) {
        stats[KIND_REMOVED + (rid + 1) * ridStride] += count;
    }

    // ========== Retrieval API ==========

    public long getValue(SValue value) {
        return getValue(value, false);
    }

    public long getValue(SValue value, boolean localOnly) {
        return getValueImpl(value, localOnly ? 0 : 2);
    }

    // Traverse flag values:
    //   0 - process local only
    //   1 - process isSumFor() nodes, i.e. equivalent type children
    //   2 - process recursive
    private long getValueImpl(SValue value, int traverse) {
        long v = getSingleValue(value, 0);
        if (value.isRid()) {
            for (int i = ridCount; --i >= 0; ) {
                v += getSingleValue(value, (i + 1) * ridStride);
            }
        }
        if (traverse == 0)
            return v;

        QDStats[] children = this.children; // Atomic read.
        for (QDStats child : children) {
            // Child is processed recursively (for sum nodes) or if it is of the "same" type as this node.
            if (child != null && (traverse == 2 || (traverse == 1 && isSumFor(child)))) {
                v += child.getValueImpl(value, child.isSum() ? 2 : 1);
            }
        }
        return v;
    }

    public void addValues(SValue value, boolean localOnly, long[] dest) {
        if (!value.isRid())
            throw new IllegalArgumentException("Method works only for rid values");
        addValuesImpl(value, localOnly ? 0 : 2, dest);
    }

    // See getValueImpl() method for traverse flag meaning
    private void addValuesImpl(SValue value, int traverse, long[] dest) {
        for (int i = ridCount; --i >= 0; ) {
            dest[i] += getSingleValue(value, (i + 1) * ridStride);
        }
        if (traverse == 0)
            return;

        QDStats[] children = this.children; // Atomic read.
        for (QDStats child : children) {
            // Child is processed recursively (for sum nodes) or if it is of the "same" type as this node.
            if (child != null && (traverse == 2 || (traverse == 1 && isSumFor(child)))) {
                child.addValuesImpl(value, child.isSum() ? 2 : 1, dest);
            }
        }
    }

    private long getSingleValue(SValue value, int index) {
        // Special handling for virtual KIND_SIZE which is defined as KIND_ADDED - KIND_REMOVED
        if (value.getKind() == KIND_SIZE) {
            // Read removed first to avoid negative sizes
            long removed = AtomicArrays.INSTANCE.getVolatileLong(stats, KIND_REMOVED + index);
            return AtomicArrays.INSTANCE.getVolatileLong(stats, KIND_ADDED + index) - removed;
        }
        return AtomicArrays.INSTANCE.getVolatileLong(stats, value.getKind() + index);
    }

    // ========== Constructors ==========

    /**
     * Creates uninitialized stats.
     * {@link #initRoot} must be called after construction.
     */
    public QDStats() {
    }

    /**
     * Creates uninitialized stats with a specified key properties.
     * {@link #initRoot} must be called after construction.
     * @throws IllegalArgumentException if key properties have invalid format.
     */
    public QDStats(String keyProperties) {
        JMXNameBuilder.validateKeyProperties(keyProperties);
        this.keyProperties = keyProperties;
    }

    /**
     * Creates root stats with a given {@code type}.
     */
    public QDStats(SType type) {
        initRoot(type, null);
    }

    /**
     * Creates root stats with a given {@code type} and {@code scheme}.
     */
    public QDStats(SType type, DataScheme scheme) {
        initRoot(type, scheme);
    }

    // ========== Management API ==========

    /**
     * @deprecated Use {@link #initRoot(SType, DataScheme)} instead.
     */
    @Deprecated
    public void initRoot(SType type, int ridCount) {
        initStats(null, new StatsLock(), type, ridCount, null);
    }

    /**
     * Initializes root statistics after instantiation using no-arg constructor.
     */
    public void initRoot(SType type, DataScheme scheme) {
        initStats(null, new StatsLock(), type, 0, scheme);
    }

    /**
     * Returns the first found child with the specified type or {@code null} if not found.
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
     * Returns the first found child with the corresponding type and key properties or {@code null} if not found.
     */
    public QDStats get(SType type, String keyProperties) {
        QDStats[] children = this.children; // Atomic read.
        for (QDStats child : children) {
            if (child != null && child.type == type && Objects.equals(child.getKeyProperties(), keyProperties))
                return child;
        }
        return null;
    }

    /**
     * Returns all children with the corresponding type.
     */
    public List<QDStats> getAll(SType type) {
        List<QDStats> result = new ArrayList<>();
        QDStats[] children = this.children; // Atomic read.
        for (QDStats child : children) {
            if (child != null && child.type == type)
                result.add(child);
        }
        return result;
    }

    /**
     * Returns the first found child with the corresponding type or {@link #create(SType) creates} new
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
     * Returns the first found child with the corresponding type and key properties or
     * {@link #create(SType, String) creates} new one if not found.
     * The result is {@link #VOID} for closed or void stats.
     */
    public QDStats getOrCreate(SType type, String keyProperties) {
        synchronized (lock) {
            QDStats child = get(type, keyProperties);
            if (child == null)
                child = create(type, keyProperties);
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
     * This child's stats will inherit per-record stats tracking from parent.
     * The result is {@link #VOID} for closed or void stats.
     *
     * @param type The type of this stat.
     * @param keyProperties The JMX key=value list of properties for naming of JMX beans (when JMX stats are used).
     */
    public final QDStats create(SType type, String keyProperties) {
        return create(type, keyProperties, true);
    }

    /**
     * Creates and returns new child with the corresponding type, key properties,
     * and an optional per-record tracking of stats.
     * The result is {@link #VOID} for closed or void stats.
     *
     * @param type The type of this stat.
     * @param keyProperties The JMX key=value list of properties for naming of JMX beans (when JMX stats are used).
     * @param useRid when true, then per-record stats tracking is inherited from parent, when false it is turned off
     *               in the created stats and in all its children.
     */
    public QDStats create(SType type, String keyProperties, boolean useRid) {
        synchronized (lock) {
            // Reset scheme and rid if per-record stats are not tracked
            return createInternal(type, keyProperties, false, useRid ? ridCount : 0, useRid ? scheme : null);
        }
    }

    // Closes this stats object and all its children.
    // Re-enterable.
    public void close() {
        synchronized (lock) {
            closeInternal();
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() +
            "{type=" + type + (isSum ? ",sum" : "") + (closed ? ",closed" : "") + "}[" + getFullKeyProperties() + "]";
    }

    // override in descendants
    protected void closeImpl() {}

    // override in descendants
    public void addMBean(String type, Object mbean) {}

    /**
     * Quotes key property value so that it can be safely used in <code>keyProperties</code> of
     * {@link #create(com.devexperts.qd.stats.QDStats.SType, String) create(type, keyProperties)}.
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
        public QDStats create(SType type, String keyProperties, boolean useRid) {
            if (keyProperties == null)
                return VOID;
            if (keyProperties.equals(getKeyProperties()))
                return this;
            VoidQDStats result = new VoidQDStats();
            initChild(result, SType.VOID, keyProperties, 0, null);
            return result;
        }

        @Override
        public void close() {}
    }

    public String toDebugString() {
        return printStats(new StringBuilder(), this, "", this.isSum).toString();
    }

    private static StringBuilder printStats(StringBuilder buff, QDStats stats, String prefix, boolean underSum) {
        buff.append(prefix)
            .append(underSum ? "ref " : "")
            .append(stats.isSum ? "#" : "")
            .append(stats.getType())
            .append("=")
            .append(stats)
            .append("/").append(System.identityHashCode(stats))
            .append("\n");
        if (!underSum) {
            prefix += "\t";
            for (QDStats c : stats.getChildren()) {
                if (c != null)
                    printStats(buff, c, prefix, stats.isSum);
            }
        }
        return buff;
    }
}
