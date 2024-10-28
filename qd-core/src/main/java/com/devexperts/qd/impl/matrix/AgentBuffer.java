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
package com.devexperts.qd.impl.matrix;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordFilter;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.stats.QDStats;

// extensible for test purposes
class AgentBuffer implements RecordFilter {
    public static final int BASE = 1;

    private static final int DEFAULT_REBASE_THRESHOLD = Integer.MAX_VALUE / 2;

    private static final Logging log = Logging.getLogging(AgentBuffer.class);

    private final QDStats stats;
    private final Agent agent;
    private final RecordBuffer buffer;

    // A persistent position for the 0th real position in buffer
    private long firstPosition = BASE;

    private int maxBufferSize;
    private QDAgent.BufferOverflowStrategy overflowStrategy = QDAgent.BufferOverflowStrategy.DROP_OLDEST;

    private int droppedRecords;
    private int lastLogTime; // UNIX seconds when last time the log was written
    private boolean blocked; // true when the distribution to this buffer was blocked

    private int lastDroppedCipher = 0;
    private String lastDroppedSymbol = null;
    private DataRecord lastDroppedRecord = null;

    AgentBuffer(Agent agent) {
        this.agent = agent;
        this.stats = agent.stats.create(QDStats.SType.AGENT_DATA);
        this.buffer = new RecordBuffer(agent.collector.getAgentBufferMode(agent));
        this.maxBufferSize = agent.collector.management.getAgentBufferSizeDefault();
    }

    // overridable for test purposes
    int getRebaseThreshold() {
        return DEFAULT_REBASE_THRESHOLD;
    }

    public void setBufferSizeLLocked(int maxBufferSize) {
        this.maxBufferSize = maxBufferSize;
    }

    public void setBufferOverflowStrategyLLocked(QDAgent.BufferOverflowStrategy overflowStrategy) {
        this.overflowStrategy = overflowStrategy;
    }

    /**
     * Returns a conversion number such that
     * actualPosition = persistentPosition + positionBase.
     */
    public long getPositionBase() {
        return buffer.getPosition() - firstPosition;
    }

    // AFTER CALL TO THIS METHOD: getPositionBase() == BASE
    // Must be called on a compacted buffer (first actual element on buffer's position zero)
    public void rewindAndRebasePosition() {
        // restore original position in buffer
        buffer.rewind();
        firstPosition = BASE;
    }

    public long getLastPersistentPosition() {
        return buffer.getLimit() - getPositionBase();
    }

    // Rebasing is needed when firstPosition becomes too large to reliable compare
    // persistent positions with firstPosition for "isInBuffer" method
    // (we cannot allow firstPosition to wrap)
    public boolean needsRebase() {
        int rebaseThreshold = getRebaseThreshold();
        return (int) firstPosition > rebaseThreshold || (int) (firstPosition >> 32) > rebaseThreshold;
    }

    public boolean hasNext() {
        return buffer.hasNext();
    }

    public RecordCursor next() {
        return buffer.next();
    }

    public boolean isInBuffer(long persistentPosition) {
        return persistentPosition >= firstPosition;
    }

    public void unlinkFromPersistentPosition(long persistentPosition) {
        assert buffer.getMode().hasLink() && isInBuffer(persistentPosition); // history only & only when present in buffer
        buffer.unlinkFrom(persistentPosition + getPositionBase());
    }

    public void flagFromPersistentPosition(long persistentPosition, int eventFlags) {
        assert buffer.getMode().hasLink() && isInBuffer(persistentPosition); // history only & only when present in buffer
        buffer.flagFrom(persistentPosition + getPositionBase(), eventFlags);
    }

    public RecordCursor writeCursorAtPersistentPosition(long persistentPosition) {
        // assert inInBuffer(persistentPosition)
        return buffer.writeCursorAt(persistentPosition + getPositionBase());
    }

    @Override
    public boolean accept(RecordCursor cursor) {
        assert !buffer.getMode().hasLink(); // it is not used for History, which uses linking to clear records from AgentBuffer
        // Implements RecordFilter for compacting only and only for Stream:
        // See compactAndRefilter. Hidden as implementation details.
        boolean accepted = agent.collector.keepInStreamBufferOnRefilter(agent, cursor);
        if (!accepted)
            stats.updateRemoved(cursor.getRecord().getId());
        return accepted;
    }

    public void compact() {
        assert buffer.getMode().hasLink(); // only for linked buffers (History)
        buffer.compact();
    }

    public void compactAndRefilter() {
        assert !buffer.getMode().hasLink(); // only for non-linked & filtered buffers (Stream)
        buffer.compact(this);
    }

    public RecordCursor addDataAndCompactIfNeeded(RecordCursor cursor) {
        stats.updateAdded(cursor.getRecord().getId());
        return buffer.addDataAndCompactIfNeeded(cursor);
    }

    /**
     * Retrieves data from buffer into the given sink.
     * @param sink the sink to retrieve data to.
     * @param nRetrieveLimit the maximal number of records to retrieve.
     * @return number of records retrieved and appended to sink.
     */
    public int retrieveData(RecordSink sink, int nRetrieveLimit) {
        long originalPosition = buffer.getPosition();
        try {
            int count = 0;
            while (sink.hasCapacity()) {
                RecordCursor cursor = buffer.next();
                if (cursor == null)
                    break;
                // 1. We don't have to check sub on every retrieve, since only subscribed data is in the buffer
                // 2. sink.append(cursor) may fail with exception. Cleanup buffer in finally slot to make
                //    sure that buffer is left in consistent state in this case (as if record was processed)
                try {
                    stats.updateRemoved(cursor.getRecord().getId());
                    if (!cursor.isUnlinked()) { // just skip unlinked entries
                        sink.append(cursor);
                        if (++count >= nRetrieveLimit)
                            break;
                    }
                } finally {
                    buffer.cleanup(cursor);
                }
            }
            return count;
        } finally {
            firstPosition += buffer.getPosition() - originalPosition;
        }
    }

    public boolean isBlocked() {
        return blocked;
    }

    public boolean unblock() {
        // unblock blocked buffer when it became empty
        if (blocked && !buffer.hasNext()) {
            blocked = false;
            return true;
        }
        return false;
    }

    public boolean blockNewRecord() {
        if (overflowStrategy != QDAgent.BufferOverflowStrategy.BLOCK)
            return false;
        if (buffer.size() < maxBufferSize)
            return false;
        blocked = true;
        return true;
    }

    public boolean dropNewRecord(RecordCursor cursor) {
        if (overflowStrategy != QDAgent.BufferOverflowStrategy.DROP_NEWEST)
            return false;
        if (buffer.size() < maxBufferSize)
            return false;
        makeDropped(cursor);
        return true;
    }

    public void dropOldRecords() {
        if (overflowStrategy != QDAgent.BufferOverflowStrategy.DROP_OLDEST)
            return;
        while (buffer.size() > maxBufferSize) {
            long originalPosition = buffer.getPosition();
            RecordCursor cursor = buffer.next();
            if (!cursor.isUnlinked()) // do not count unlinked records as dropped
                makeDropped(cursor);
            stats.updateRemoved(cursor.getRecord().getId());
            buffer.cleanup(cursor);
            firstPosition += buffer.getPosition() - originalPosition;
        }
    }

    private void makeDropped(RecordCursor cursor) {
        lastDroppedCipher = cursor.getCipher();
        lastDroppedSymbol = cursor.getSymbol();
        lastDroppedRecord = cursor.getRecord();
        droppedRecords++;
        agent.collector.statsDropped.updateAdded(cursor.getRecord().getId());
    }

    public void logDrops(Agent agent) {
        if (droppedRecords <= 0)
            return;
        int time = (int) (System.currentTimeMillis() / 1000);
        if (time - lastLogTime < agent.collector.management.getBufferOverflowLogIntervalSeconds())
            return;
        lastLogTime = time;
        String message = agent.collector.getContract() +
            " buffer overflow - " + droppedRecords + " records skipped for agent [" +
            agent.getStats().getFullKeyProperties() + "]." +
            (lastDroppedRecord == null ? "" : " Last record was " +
                agent.collector.getScheme().getCodec().decode(lastDroppedCipher, lastDroppedSymbol) + ":" +
                lastDroppedRecord.getName()
            );
        log.error(message);
        agent.collector.droppedLogAccept(message);
        agent.collector.counters.countDropped(droppedRecords);
        droppedRecords = 0;
    }

    public void clear() {
        buffer.clear();
    }

    public void closeStats() {
        stats.close();
    }

}
