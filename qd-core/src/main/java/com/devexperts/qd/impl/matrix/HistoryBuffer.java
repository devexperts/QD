/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2025 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.impl.matrix;

import com.devexperts.annotation.Internal;
import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.QDAgent;
import com.devexperts.qd.ng.EventFlag;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.ng.RecordMode;
import com.devexperts.qd.ng.RecordSink;
import com.devexperts.qd.stats.QDStats;

import java.util.Arrays;

import static com.devexperts.qd.impl.matrix.History.REMOVE_EVENT;
import static com.devexperts.qd.impl.matrix.History.SNAPSHOT_BEGIN;
import static com.devexperts.qd.impl.matrix.History.SNAPSHOT_END;
import static com.devexperts.qd.impl.matrix.History.SNAPSHOT_SNIP;
import static com.devexperts.qd.impl.matrix.History.TX_PENDING;

/**
 * The <code>HistoryBuffer</code> stores historic values for certain data record and symbol.
 */
public final class HistoryBuffer {
    private static final Logging log = Logging.getLogging(HistoryBuffer.class);

    private static final int INIT_CAPACITY = 16;
    private static final int MAX_LINEAR_SCAN = 32;
    private static final double NEED_COMPACT_DENSITY = 0.5;
    private static final double AFTER_COMPACT_DENSITY = 0.75;
    private static final double NEW_GAP_PROPORTION = 0.01;

    private static final int PAYLOAD_MARKER = 1; // payload slot contains data
    private static final int EMPTY_MARKER = 0;   // empty slot has no data and can be either within a gap or outside working range

    // HistoryBuffer data storage

    private final boolean withEventTimeSequence;
    private final int intOffset;
    private final int objOffset;
    private final int intStep;
    private final int objStep;

    private int mask;
    private int[] intValues;
    private Object[] objValues;

    private int min;
    private int max;
    private int payloadSize;

    // HistorySnapshot protocol state

    // flags bits:

    /**
     * Set when SNAPSHOT_BEGIN flag was seen. Reset on subscription changes.
     */
    private static final byte SNAPSHOT_BEGIN_SEEN_FLAG = 0x01;

    /**
     * Set when snapshot was completed with SNAPSHOT_END/SNIP. It means that
     * there is a consistent snapshot for "snapshotTime".
     * It is reset on subscription changes and on SNAPSHOT_BEGIN event.
     */
    private static final byte SNAPSHOT_END_SEEN_FLAG = 0x02;

    /**
     * Set when SNAPSHOT_BEGIN/MODE flags was ever seen, that is data source supports snapshot protocol.
     */
    private static final byte EVER_SNAPSHOT_MODE_FLAG = 0x04;

    /**
     * Set when here is an ongoing implicit snapshot sweep update transaction, that is when
     * anything updates with {@code time >= everSnapshotTime} while snapshot is being
     * received (SNAPSHOT_BEGIN_SEEN_FLAG is set, but not SNAPSHOT_END_SEEN_FLAG).
     * It is reset on SNAPSHOT_END event.
     */
    private static final byte SWEEP_TX_FLAG = 0x08;

    /**
     * Set when explicit transaction turned on by TX_PENDING event flag is in process.
     */
    private static final byte EXPLICIT_TX_FLAG = 0x10;

    /**
     * History buffer state flags.
     */
    private byte flags; // See XXX_FLAG and MODE_XXX

    /**
     * This time is set on SNAPSHOT_BEGIN and decreases on each event after that.
     * It is used for to track the update or previous snapshot with the new one and
     * as a limit time when data is retrieved from this HB with {@link QDAgent}.
     *
     * <p>Note, that HB may contain records with time below that, since there could
     * have been a previous snapshot. See {@link #everSnapshotTime}.
     */
    private long snapshotTime = Long.MAX_VALUE;

    /**
     * This time is similar to {@link #snapshotTime}, but it is NOT reset on
     * SNAPSHOT_BEGIN and tracks min ever reached snapshot time (lowest possible knownTime).
     * When {@link #wasEverSnapshotMode()}, all events with {@code time >= everSnapshotTime}
     * constitute a consistent snapshot, unless there is an ongoing explicit or implicit transaction,
     * which are tracked with {@link #flags}.
     *
     * <p>There cannot be any records in HB with time below this time.
     */
    private long everSnapshotTime = Long.MAX_VALUE;

    /**
     * This time is sent by the event with SNAPSHOT_SNIP flag and is reset to {@link Long#MIN_VALUE}
     * on any event below this time. There cannot be any records in HB with time below this time.
     * HB is in "snip mode" when this time differs from {@link Long#MIN_VALUE}.
     */
    private long snipSnapshotTime = Long.MIN_VALUE;

    /*
     * Time invariant (it is checked by validTimes method)
     *         snapshotTime >= everSnapshotTime >= snipSnapshotTime
     *   any time in buffer >= everSnapshotTime >= snipSnapshotTime
     *
     * Additional invariants that bind HB with subscription:
     *            any timeSub >= timeTotal
     *       everSnapshotTime >= timeTotal
     */

    /**
     * This time is used when all subscription is removed and we need to keep empty HistoryBuffer for some time
     * to remember it's state. In this case this time specifies when this buffer need to be completely released.
     * There is no dedicated expiration checking and enforcing threads or tasks - expirationTime is only checked
     * during rehash of total agent. As a result expired buffers might live longer than timeout and still be
     * reactivated if not removed by rehash.
     */
    long expirationTime = Long.MAX_VALUE;

    // results of the last examineData here
    int nExamined;
    long examinedTime; // this is set when nExamined > 0

    // ========== constructor ==========

    HistoryBuffer(DataRecord record, boolean withEventTimeSequence) {
        if (!record.hasTime() || record.getIntFieldCount() < 2)
            throw new IllegalArgumentException("Record does not contain time.");

        this.withEventTimeSequence = withEventTimeSequence;
        intOffset = withEventTimeSequence ? 3 : 1;
        objOffset = 0;
        intStep = record.getIntFieldCount() + intOffset;
        objStep = record.getObjFieldCount() + objOffset;

        allocInitial();
    }

    private void allocInitial() {
        min = 0;
        max = 0;
        payloadSize = 0;
        mask = INIT_CAPACITY - 1;
        intValues = new int[intStep * INIT_CAPACITY];
        objValues = objStep == 0 ? null : new Object[objStep * INIT_CAPACITY];
    }

    // ========== Internal ==========

    // checks time invariant
    boolean validTimes() {
        return snapshotTime >= everSnapshotTime && everSnapshotTime >= snipSnapshotTime &&
            (min == max || time(min) >= everSnapshotTime);
    }

    private long time(int index) {
        int place = index * intStep + intOffset;
        return (((long) intValues[place]) << 32) | (intValues[place + 1] & 0xffffffffL);
    }

    private boolean isPayload(int index) {
        return intValues[index * intStep] == PAYLOAD_MARKER;
    }

    private void setMarker(int index, int marker) {
        intValues[index * intStep] = marker;
    }

    private void setTime(int index, long time) {
        int place = index * intStep + intOffset;
        intValues[place] = (int) (time >>> 32);
        intValues[place + 1] = (int) time;
    }

    private long eventTimeSequence(int index) {
        if (!withEventTimeSequence)
            return 0;
        int place = index * intStep;
        return (((long) intValues[place + 1]) << 32) | (intValues[place + 2] & 0xffffffffL);
    }

    private void setEventTimeSequence(int index, long eventTimeSequence) {
        if (!withEventTimeSequence)
            return;
        int place = index * intStep;
        intValues[place + 1] = (int) (eventTimeSequence >> 32);
        intValues[place + 2] = (int) eventTimeSequence;
    }

    /**
     * Returns index such as:
     * <br>{@code time(index) <= time && time < time((index + 1) & mask)}<br>
     * <b>NOTE:</b> there must be some data!!!
     */
    private int getIndexInclusive(long time) {
        assert min != max;
        long lTime = time(min);
        if (time < lTime) {
            return (min - 1) & mask;
        }
        int r = max - 1;
        int rIndex = r & mask;
        long rTime = time(rIndex);
        if (time >= rTime) {
            return rIndex;
        }
        int l = min;
        if (r < l) {
            r += mask + 1;
        }
        while (true) {
            // Invariant: time(l & mask) == lTime <= time < rTime == time(r & mask)
            assert l < r;
            int n = r - l;
            if (n == 1) {
                return l & mask;
            }
            if (n == rTime - lTime) { // (rTime - lTime) might overflow but cannot be equal to n in that case
                // optimization for 1-spaced times (like a big order book)
                return (l + (int) (time - lTime)) & mask;
            }
            // Binary search
            int i = (l + r) >> 1;
            // Invariant: l < i < r
            long iTime = time(i & mask);
            if (iTime <= time) {
                l = i;
                lTime = iTime;
            } else {
                r = i;
                rTime = iTime;
            }
        }
    }

    /**
     * Returns index such as:
     * <br>{@code time(index) < time && time <= time((index + 1) & mask)}<br>
     * <b>NOTE:</b> there must be some data!!!
     */
    private int getIndexExclusive(long time) {
        assert min != max;
        long lTime = time(min);
        if (time <= lTime) {
            return (min - 1) & mask;
        }
        int r = max - 1;
        int rIndex = r & mask;
        long rTime = time(rIndex);
        if (time > rTime) {
            return rIndex;
        }
        int l = min;
        if (r < l) {
            r += mask + 1;
        }
        while (true) {
            // Invariant: time(l & mask) == lTime < time <= rTime == time(r & mask)
            assert l < r;
            int n = r - l;
            if (n == 1) {
                return l & mask;
            }
            if (n == rTime - lTime) {  // (rTime - lTime) might overflow but cannot be equal to n in that case
                // optimization for 1-spaced times (like a big order book)
                return (l + (int) (time - lTime) - 1) & mask;
            }
            // Binary search
            int i = (l + r) >> 1;
            // Invariant: l < i < r
            long iTime = time(i & mask);
            if (iTime < time) {
                l = i;
                lTime = iTime;
            } else {
                r = i;
                rTime = iTime;
            }
        }
    }

    /**
     * Copies cyclic array buffer from source to destination.
     * Uses source, source head index, source tail index and source length.
     * The destination buffer will start with 0 and end with original size.
     */
    private static void copy(Object src, Object dst, int head, int tail, int length) {
        if (tail < head) {
            System.arraycopy(src, head, dst, 0, length - head);
            System.arraycopy(src, 0, dst, length - head, tail);
        } else {
            System.arraycopy(src, head, dst, 0, tail - head);
        }
    }

    // This method can try to allocate memory and die due to OutOfMemoryError.
    private void ensureFreeSpace(int space) {
        assert space <= mask;
        int overallSize = overallSize();
        if (mask - overallSize < space) {
            int length = mask + 1;
            int newLength = length << 1;
            int[] intValuesNew = new int[newLength * intStep];
            copy(intValues, intValuesNew, min * intStep, max * intStep, length * intStep);
            intValues = intValuesNew;
            if (objStep != 0) {
                Object[] objValuesNew = new Object[newLength * objStep];
                copy(objValues, objValuesNew, min * objStep, max * objStep, length * objStep);
                objValues = objValuesNew;
            }
            mask = newLength - 1;
            min = 0;
            max = overallSize;
        }
    }

    /**
     * Checks if buffer needs compaction based on density threshold and array length.
     * Triggers compaction if density falls below a threshold for large enough arrays.
     */
    private void compactIfNeeded() {
        double density = (double) payloadSize / overallSize();
        if (density < NEED_COMPACT_DENSITY) {
            compact(AFTER_COMPACT_DENSITY);
        }
    }

    /**
     * Compacts the buffer by removing inactive records while maintaining relative order.
     * Moves active records closer together to reduce fragmentation.
     *
     * @param targetDensity The maximum gap size to maintain between records after compaction
     */
    private void compact(double targetDensity) {
        int currentGapSize = overallSize() - payloadSize;
        if (currentGapSize <= 0) {
            // no gaps to compact
            return;
        }
        if (targetDensity * Integer.MAX_VALUE < payloadSize) {
            // arithmetic overflow protection - targetDensity is too low anyway
            return;
        }
        // the formula below is protected from overflow by the check above
        int targetGapSize = (int) (payloadSize / targetDensity) - payloadSize;
        if (currentGapSize <= targetGapSize) {
            // targetDensity is already met - nothing to do
            return;
        }
        // if (targetDensity > 1), then (targetGapSize < 0) - treat it as 0 (no gaps)
        double gapShrinkRatio = (double) Math.max(targetGapSize, 0) / currentGapSize;
        int dest = min;
        for (int index = min; index != max; ) {
            // index points to start of payload section
            int i = index;
            while (i != max && isPayload(i)) {
                i = (i + 1) & mask;
            }
            int size = (i - index) & mask;
            // now: section [index, i) with length [size] is continuous payload section
            if (index != dest) {
                moveLeft(index, size, (index - dest) & mask);
            }
            dest = (dest + size) & mask;
            index = i;
            if (index == max)
                break;
            // index points to start of gap section followed by at least 1 payload at [max-1]
            while (i != max && !isPayload(i)) {
                i = (i + 1) & mask;
            }
            size = (i - index) & mask;
            // now: section [index, i) with length [size] is continuous gap section followed by payload
            int newGapSize = (int) Math.floor(size * gapShrinkRatio);
            if (newGapSize > 0) {
                long preGapTime = time((index - 1) & mask);
                long afterGapTime = time(i);
                // pass any "valid" search time because we do not need result
                fillGap(dest, newGapSize, preGapTime, afterGapTime, preGapTime + 1);
                dest += newGapSize;
            }
            index = i;
            // index points to start of payload section
        }
        clear(dest, max);
        max = dest;
    }

    /**
     * Moves data through the contiguous range in cyclic array from [index,index+len) to [dest,dest+len).
     */
    private void moveData(int index, int dest, int len) {
        System.arraycopy(intValues, index * intStep, intValues, dest * intStep, len * intStep);
        if (objStep != 0) {
            System.arraycopy(objValues, index * objStep, objValues, dest * objStep, len * objStep);
        }
    }

    private void moveLeft(int index, int size, int dist) {
        assert size > 0;
        int end = (index + size) & mask;
        if (index > end) {
            // original chunk is in two pieces: [0,end) and [index,mask]
            // move [index,mask] piece first
            int len = mask + 1 - index;
            moveData(index, index - dist, len);
            // setup to copy the remaining piece [0,end)
            index = 0;
            size -= len;
            if (size == 0) // the remaining piece could be empty
                return;
        }
        // now [index,index+size) is contiguous, but may wrap to the right (overCnt items overflow)
        int overCnt = Math.min(dist - index, size);
        if (overCnt > 0) {
            moveData(index, (index - dist) & mask, overCnt);
            // setup to copy the remaining piece
            index += overCnt;
            size -= overCnt;
            if (size == 0) // the remaining (non-overflowing) piece can be empty
                return;
        }
        // the rest moves normally inside the cyclic array to the left
        moveData(index, index - dist, size);
    }

    private void moveRight(int index, int size, int dist) {
        assert size > 0;
        int end = (index + size) & mask;
        if (index > end && end > 0) {
            // original chunk is in two pieces: [0,end) and [index,mask]
            // move [0,end) piece first
            int len = end;
            moveData(0, dist, len);
            // setup to copy the remaining piece [index,mask]
            size -= len;
            assert size > 0;
        }
        // now [index,index+size) is contiguous, but may wrap to the left (overCnt items overflow)
        int overCnt = Math.min(index + size + dist - mask - 1, size);
        if (overCnt > 0) {
            int k = index + size - overCnt; // the start of chunk to move
            moveData(k, (k + dist) & mask, overCnt);
            size -= overCnt;
            if (size == 0)  // the remaining (non-overflowing) piece can be empty
                return;
        }
        // the rest moves normally inside the cyclic array to the right
        moveData(index, index + dist, size);
    }

    /**
     * Finds nearest inactive slot around given index by scanning MAX_LINEAR_SCAN positions
     * in both directions. Moves data to fill the gap if found.
     *
     * @param lIndex Starting index to scan from
     * @return Found gap index or -1 if no gap found within scan range or not enough space to move
     */
    private int insertAtNearestEmptySlot(int lIndex) {
        if (mask - overallSize() < 1) {
            return -1;
        }
        int rIndex = (lIndex + 1) & mask;
        int l = lIndex;
        int r = rIndex;
        for (int i = 0; i < MAX_LINEAR_SCAN; i++) {
            int prev = l;
            l = (l - 1) & mask;
            if (!isPayload(l)) {
                moveLeft(prev, i + 1, 1);
                if (prev == min) {
                    min = (min - 1) & mask;
                }
                clearAt(lIndex);
                return lIndex;
            }
            r = (r + 1) & mask;
            if (!isPayload(r)) {
                moveRight(rIndex, i + 1, 1);
                if (r == max) {
                    max = (max + 1) & mask;
                }
                clearAt(rIndex);
                return rIndex;
            }
        }
        return -1;
    }

    /**
     * Prepares empty slots at or near specified index by moving existing data.
     * The allocated space will be placed with timestamps.
     * Choose an optimal direction (left/right) based on distance to buffer edges.
     *
     * @param index Target index to prepare size at
     * @param size Number of empty slots needed
     * @param lTime Timestamp of the left border
     * @param rTime Timestamp of the right border
     * @param time Timestamp for which it is necessary to find a suitable index
     * @return a suitable index for a time
     */
    private int insertGap(int index, int size, long lTime, long rTime, long time) {
        int l = (index + 1 - min) & mask;
        int r = (max - index - 1) & mask;
        int start;
        if (l < r || l == r && min < index) {
            moveLeft(min, l, size);
            min = (min - size) & mask;
            start = (index - size + 1) & mask;
        } else {
            index = (index + 1) & mask;
            moveRight(index, r, size);
            max = (max + size) & mask;
            start = index;
        }
        return fillGap(start, size, lTime, rTime, time);
    }

    /**
     * Determines the appropriate index for inserting a record with given timestamp.
     * Analyzes surrounding records and buffer structure to find optimal position.
     * May create or locate gaps between existing records while maintaining time ordering.
     * For dense regions, redistributes timestamps to ensure even spacing.
     *
     * @param index Initial target index to analyze
     * @param time Timestamp to find position for
     * @return Optimal index where record can be inserted while preserving time ordering
     */
    private int insertAt(int index, long time) {
        int lIndex = index;
        int rIndex = (index + 1) & mask;

        if (!isPayload(lIndex)) {
            return lIndex;
        }
        if (!isPayload(rIndex)) {
            return rIndex;
        }
        int emptyIndex = insertAtNearestEmptySlot(index);
        if (emptyIndex > -1) {
            setTime(emptyIndex, time);
            return emptyIndex;
        }

        // no space, need expanding
        long lTime = time(lIndex);
        long rTime = time(rIndex);

        long range = rTime - lTime;
        int gapSize = (int) (overallSize() * NEW_GAP_PROPORTION);
        if (gapSize <= 0) {
            gapSize = 1;
        }
        // note: range can arithmetically overflow around (MAX_VALUE - MIN_VALUE)
        // code below ignores overflow case because true range would be too large anyway
        if (gapSize > range - 1 && range > 1) {
            gapSize = Math.toIntExact(range - 1);
        }
        int offset = (index - min) & mask;
        ensureFreeSpace(gapSize);
        index = (min + offset) & mask; // recalculate actual index after resizing
        return insertGap(index, gapSize, lTime, rTime, time);
    }

    /**
     * Fills a gap in the history buffer with interpolated timestamps.
     * This method populates empty slots in the circular buffer between two existing records,
     * creating a smooth progression of timestamps while maintaining proper time ordering.
     * The method distributes timestamps evenly across the gap by calculating a step size
     * based on the time range divided by the number of gaps.
     *
     * @param index Starting index of the gap in the circular buffer
     * @param gapSize Number of empty slots to be filled with interpolated timestamps
     * @param lTime Timestamp of the element to the left of the gap
     * @param rTime Timestamp of the element to the right of the gap
     * @param time the target timestamp of the element expected to be placed into the gap ({@code lTime < time < rTime})
     * @return Index where the target element with {@code time} timestamp to be placed (the last element in the filled
     *         gap whose timestamp does not exceed the given timestamp value {@code time} or the first index in the gap).
     */
    private int fillGap(int index, int gapSize, long lTime, long rTime, long time) {
        int end = (index + gapSize) & mask;
        clear(index, end);
        long range = rTime - lTime;
        // note: range can arithmetically overflow around (MAX_VALUE - MIN_VALUE)
        // the below code circumvents it by dividing overflowed range by 2 via shift and then multiplying back later
        long step = range > 0 ? range / (gapSize + 1) : (range >>> 1) / (gapSize + 1) * 2;
        long fillTime = lTime + step;
        int result = index;
        for (int i = index; i != end; i = (i + 1) & mask, fillTime += step) {
            setTime(i, fillTime);
            if (fillTime <= time) {
                result = i;
            }
        }
        return result;
    }

    private void clearAt(int index) {
        int place = index * intStep;
        for (int i = 0; i < intStep; i++) {
            intValues[place + i] = 0;
        }
        if (objStep != 0) {
            place = index * objStep;
            for (int i = 0; i < objStep; i++) {
                objValues[place + i] = null;
            }
        }
    }

    /**
     * Clears ranges of elements in both integer and object arrays.
     * Handles cyclic buffer wraparound when clearing spans array boundaries.
     * Sets all cleared elements to 0/null.
     *
     * @param start Starting index to clear from
     * @param end the ending index (exclusive)
     */
    private void clear(int start, int end) {
        if (end >= start) {
            Arrays.fill(intValues, start * intStep, end * intStep, 0);
            if (objStep != 0) {
                Arrays.fill(objValues, start * objStep, end * objStep, null);
            }
        } else {
            Arrays.fill(intValues, start * intStep, intValues.length, 0);
            Arrays.fill(intValues, 0, end * intStep, 0);
            if (objStep != 0) {
                Arrays.fill(objValues, start * objStep, objValues.length, null);
                Arrays.fill(objValues, 0, end * objStep, null);
            }
        }
    }

    /**
     * Removes record at specified index and updates buffer structure.
     * For edge records (at min/max), removes continuous inactive records.
     * For middle records, marks as inactive and sets given timestamp.
     * Updates available count.
     *
     * @param index Index of record to remove
     * @param time Timestamp to set for inactive middle records
     */
    private void removeAt(int index, long time) {
        if (index == min) {
            int i = findLargerPayload((index + 1) & mask);
            clear(index, i);
            min = i;
        } else if (index == ((max - 1) & mask)) {
            int i = (findSmallerPayload((index - 1) & mask) + 1) & mask;
            clear(i, max);
            max = i;
        } else {
            clearAt(index);
            setMarker(index, EMPTY_MARKER);
            setTime(index, time);
        }
        payloadSize--;
        compactIfNeeded();
    }

    private int removeSnapshotFromSnipTime(
        long time, DataRecord record, int cipher, String symbol, RecordBuffer removeBuffer)
    {
        assert max != min;
        int startIndex = getIndexExclusive(time);
        int stopIndex = (min - 1) & mask;
        int removeCount = 0;
        for (int index = startIndex; index != stopIndex; index = (index - 1) & mask) {
            if (isPayload(index)) {
                removeBuffer.add(record, cipher, symbol).setTime(time(index));
                removeCount++;
            }
        }
        int prevMin = min;
        min = findLargerPayload((startIndex + 1) & mask);
        clear(prevMin, min);
        payloadSize -= removeCount;
        return removeCount;
    }

    private int removeSnapshotSweepBetween(
        long snapshotTime, long toTime, DataRecord record, int cipher, String symbol, RecordBuffer removeBuffer)
    {
        assert max != min;
        assert toTime <= snapshotTime;
        // find first index to start remove at
        int startIndex = getIndexExclusive(snapshotTime);
        int stopIndex = (min - 1) & mask;
        // buffer for removal until toTime
        int removeCount = 0;
        int index = startIndex;
        while (index != stopIndex) {
            long time = time(index);
            boolean payload = isPayload(index);
            if (time <= toTime && payload) {
                break;
            }
            if (payload) {
                removeBuffer.add(record, cipher, symbol).setTime(time);
                removeCount++;
            }
            index = (index - 1) & mask;
        }
        if (removeCount == 0) {
            return 0; // nothing was removed
        }
        startIndex = findLargerPayload((startIndex + 1) & mask);
        // now removed from index (exclusive) to adjusted startIndex (exclusive)
        int count = (startIndex - index - 1) & mask;
        int l = (index + 1 - min) & mask;
        int r = (max - startIndex) & mask;
        if (l < r) {
            if (l != 0) {
                moveRight(min, l, count);
            }
            int prevMin = min;
            min = (min + count) & mask;
            clear(prevMin, min);
        } else {
            if (r != 0) {
                moveLeft(startIndex, r, count);
            }
            int prevMax = max;
            max = (max - count) & mask;
            clear(max, prevMax);
        }
        payloadSize -= removeCount;
        return removeCount;
    }

    private int findLargerPayload(int startIndex) {
        if (checkIndexOutOfRange(startIndex)) {
            // just return input index
            return startIndex;
        }
        int index = startIndex;
        if (index != max) {
            while (!isPayload(index)) {
                index = (index + 1) & mask;
            }
        }
        return index;
    }

    private int findSmallerPayload(int startIndex) {
        if (checkIndexOutOfRange(startIndex)) {
            // just return input index
            return startIndex;
        }
        int index = startIndex;
        if (index != min) {
            while (!isPayload(index)) {
                index = (index - 1) & mask;
            }
        }
        return index;
    }

    private boolean checkIndexOutOfRange(int index) {
        return ((index - min) & mask) >= overallSize();
    }

    // ========== Transaction and snapshot support getters ==========

    // returns true when HB is a part of any kind of in-progress transaction
    public boolean isTx() {
        return (flags & (SWEEP_TX_FLAG | EXPLICIT_TX_FLAG)) != 0;
    }

    public boolean isSweepTx() {
        return (flags & SWEEP_TX_FLAG) != 0;
    }

    public boolean wasSnapshotBeginSeen() {
        return (flags & SNAPSHOT_BEGIN_SEEN_FLAG) != 0;
    }

    public boolean wasSnapshotEndSeen() {
        return (flags & SNAPSHOT_END_SEEN_FLAG) != 0;
    }

    public boolean wasEverSnapshotMode() {
        return (flags & EVER_SNAPSHOT_MODE_FLAG) != 0;
    }

    public boolean isWaitingForSnapshotBegin() {
        return (flags & (EVER_SNAPSHOT_MODE_FLAG | SNAPSHOT_BEGIN_SEEN_FLAG | SNAPSHOT_END_SEEN_FLAG)) ==
            EVER_SNAPSHOT_MODE_FLAG;
    }

    public long getSnapshotTime() {
        return snapshotTime;
    }

    public long getEverSnapshotTime() {
        return everSnapshotTime;
    }

    public long getSnipSnapshotTime() {
        return snipSnapshotTime;
    }

    boolean isSnipToTime(long time) {
        return time == snipSnapshotTime;
    }

    // ========== Transaction and snapshot support state update methods ==========

    void resetSnapshot() {
        // It is invoked when total subscription changes
        // Must not have any snapshot flags while waiting fresh snapshot from upstream data source
        // Implicit snapshot sweep transaction will linger if it was in progress until next snapshot.
        flags &= ~(SNAPSHOT_BEGIN_SEEN_FLAG | SNAPSHOT_END_SEEN_FLAG);
    }

    // (0) Is invoked from processRecordSource when SNAPSHOT_BEGIN/MODE flag is set
    // Returns true when EVER_SNAPSHOT_MODE_FLAG is just set
    boolean enterSnapshotModeFirstTime() {
        assert validTimes();
        if (wasEverSnapshotMode())
            return false;
        // pretend as if we have seen the actual SNAPSHOT_BEGIN (even if not).
        flags |= EVER_SNAPSHOT_MODE_FLAG | SNAPSHOT_BEGIN_SEEN_FLAG;
        return true;
    }

    void enterSnapshotModeForUnconflated() {
        flags |= EVER_SNAPSHOT_MODE_FLAG | SNAPSHOT_BEGIN_SEEN_FLAG;
    }

    // (1) Is invoked from processRecordSource with the value of TX_PENDING flag
    // returns true when this event is the END of transaction (TX_END)
    // Note, txPending flag will have to cleared later by invoking txEnd method.
    boolean updateExplicitTx(boolean txPending) {
        if (txPending) {
            flags |= EXPLICIT_TX_FLAG;
            return false;
        }
        if ((flags & EXPLICIT_TX_FLAG) != 0) {
            /*
             * Explicit transaction ends, not that this "txEnd" event still have to get into agent buffers in the
             * 2nd phase of processData. All the locks are released between 1st and 2nd phases, so a concurrent
             * retrieve from HB will see that there is no transaction in HB and no data in agent buffer.
             * However, it will check PROCESS_VERSION to see that this event is still being processed.
             *
             * See "HistoryTxTest.testLostTxPending1" case.
             */
            flags &= ~EXPLICIT_TX_FLAG;
            return !isTx(); // return true when we're in not any kind of tx anymore
        }
        return false; // not a part of transaction at all
    }

    // (2) Is invoked from processRecordSource when SNAPSHOT_BEGIN flag is set
    void snapshotBegin() {
        snapshotTime = Long.MAX_VALUE;
        assert validTimes();

        flags |= SNAPSHOT_BEGIN_SEEN_FLAG;
        flags &= ~SNAPSHOT_END_SEEN_FLAG;
    }

    // (3) Is invoked from processRecordSource when SNAPSHOT_SNIP flag is set
    boolean snapshotSnipAndRemove(long time,
        DataRecord record, int cipher, String symbol, RecordBuffer removeBuffer, QDStats stats, int rid)
    {
        if (isSnipToTime(time))
            return false; // nothing changes -- was snipped to this time before
        // remove records below snip time
        if (max != min) {
            int removeCount = removeSnapshotFromSnipTime(time, record, cipher, symbol, removeBuffer);
            stats.updateRemoved(rid, removeCount);
        }
        // make snipped
        snipSnapshotTime = time;
        trimSnapshotTimes(time);
        assert validTimes();
        return true; // new snapshot snip time
    }

    // (4) Is invoked from processRecordSource
    // returns true when everSnapshotTime was updated
    boolean updateSnapshotTimeAndSweepRemove(long time,
        long trimToTime, DataRecord record, int cipher, String symbol, RecordBuffer removeBuffer, QDStats stats, int rid)
    {
        long trimmedTime = Math.max(time, trimToTime);
        if (trimmedTime >= snapshotTime)
            return false; // nothing to do
        // Snapshot time goes down. Remove all encountered records between snapshotTime and time (when HB non empty)
        if (max != min) {
            int removeCount = removeSnapshotSweepBetween(snapshotTime, time < trimToTime ? trimToTime - 1 : time,
                record, cipher, symbol, removeBuffer);
            stats.updateRemoved(rid, removeCount);
        }
        snapshotTime = trimmedTime;
        boolean updatedEverSnapshotTime = trimmedTime < everSnapshotTime;
        if (updatedEverSnapshotTime) // move everSnapshotTime down as needed
            everSnapshotTime = trimmedTime;
        if (trimmedTime < snipSnapshotTime) // moved below most recent snip -- no longer snip
            snipSnapshotTime = Long.MIN_VALUE;
        assert validTimes();
        return updatedEverSnapshotTime;
    }

    // (5) see putRecord

    // (6) Is invoked from processRecordSource when anything was removed by sweep
    // or record was updated is the previous snapshot (everSnapshotTime was not updated)
    void updateSweepTxOn() {
        // turn on tx sweep transaction
        flags |= SWEEP_TX_FLAG;
    }

    // (7) Is invoked from processRecordSource when SNAPSHOT_SNIP or SNAPSHOT_END flag with time <= timeTotal is set
    boolean snapshotEnd() {
        if (!wasSnapshotBeginSeen())
            return false;
        if (wasSnapshotEndSeen())
            return false;
        flags |= SNAPSHOT_END_SEEN_FLAG;
        return true;
    }

    // (8) Is invoked from processRecordSource on snapshot snip or snapshot end
    boolean updateSweepTxOff() {
        if (!isSweepTx())
            return false;
        flags &= ~SWEEP_TX_FLAG;
        return !isTx(); // return true when we're in not any kind of tx anymore
    }

    // (9) see enforceMaxRecordCount

    // ========== Record Transfer ==========

    /**
     * Puts single record into storage. It only reads integer fields from 2 and on (time assumed to be already parsed).
     * Returns true when new record was added or existing record updated or removed.
     */
    // This method can try to allocate memory and die due to OutOfMemoryError.
    // (5) is invoked from processRecordSource
    boolean putRecord(long time, RecordCursor cursor, boolean removeEvent, QDStats stats, int rid) {
        if (max == min || time > time((max - 1) & mask)) {
            // empty HB or new record with time > max
            return !removeEvent && putNewRecordAboveMax(cursor, stats, rid);
        }
        if (time < time(min)) {
            // new record with time < min
            return !removeEvent && putNewRecordBelowMin(cursor, stats, rid);
        }
        int index = getIndexInclusive(time);
        if (time(index) != time || !isPayload(index)) {
            // new record in the middle
            return !removeEvent && putNewRecordInTheMiddle(time, cursor, index, stats, rid);
        }
        if (removeEvent) {
            // remove existing record
            removeAt(index, time);
            stats.updateRemoved(rid);
            return true;
        }
        // update existing record
        boolean changed = cursor.updateIntsTo(2, intValues, index * intStep + intOffset + 2, intStep - intOffset - 2);
        if (objStep != 0)
            changed |= cursor.updateObjsTo(0, objValues, index * objStep + objOffset, objStep - objOffset);
        if (changed) {
            setEventTimeSequence(index, cursor.getEventTimeSequence());
            stats.updateChanged(rid);
        }
        return changed;
    }

    private boolean putNewRecordAboveMax(RecordCursor cursor, QDStats stats, int rid) {
        ensureFreeSpace(1);
        putAt(max, cursor);
        payloadSize++;
        max = (max + 1) & mask;
        stats.updateAdded(rid);
        return true;
    }

    private boolean putNewRecordBelowMin(RecordCursor cursor, QDStats stats, int rid) {
        ensureFreeSpace(1);
        min = (min - 1) & mask;
        putAt(min, cursor);
        payloadSize++;
        stats.updateAdded(rid);
        return true;
    }

    private boolean putNewRecordInTheMiddle(long time, RecordCursor cursor, int index, QDStats stats, int rid) {
        index = insertAt(index, time);
        putAt(index, cursor);
        payloadSize++;
        stats.updateAdded(rid);
        compactIfNeeded();
        return true;
    }

    /**
     * Copies data from the given cursor to the specified index in the history buffer.
     *
     * @param index The index to copy data to
     * @param cursor The source cursor containing data to copy
     */
    private void putAt(int index, RecordCursor cursor) {
        setMarker(index, PAYLOAD_MARKER);
        cursor.getIntsTo(0, intValues, index * intStep + intOffset, intStep - intOffset);
        if (objStep != 0) {
            cursor.getObjsTo(0, objValues, index * objStep + objOffset, objStep - objOffset);
        }
        setEventTimeSequence(index, cursor.getEventTimeSequence());
    }

    // (9) is invoked from processRecordSource
    void enforceMaxRecordCount(int maxRecordCount, QDStats stats, int rid) {
        if (payloadSize <= maxRecordCount) {
            return;
        }
        // too many records
        if (maxRecordCount > 0) {
            int removeCount = payloadSize - maxRecordCount;
            int index = min;
            int count = removeCount;
            while (index != max) {
                boolean payload = isPayload(index);
                if (count <= 0 && payload) {
                    break;
                }
                if (payload) {
                    count--;
                }
                index = (index + 1) & mask;
            }
            clear(min, index);
            stats.updateRemoved(rid, removeCount);
            min = index;
            payloadSize -= removeCount;
        } else {
            clear(min, max);
            stats.updateRemoved(rid, payloadSize);
            min = 0;
            max = 0;
            payloadSize = 0;
        }
    }

    /**
     * Clears all records, trims memory and trims snapshot times to Long.MAX_VALUE.
     * Effect is similar to {@link #removeOldRecords removeOldRecords(Long.MAX_VALUE, QDStats, int)}
     * except memory footprint is trimmed.
     * <p>
     * Used when all subscription is removed and we need to keep state of HistoryBuffer without any data.
     * So we trim subscription time as if subscribed to "empty range" for proper state transition.
     */
    void clearAllRecords(QDStats stats, int rid) {
        int removeCount = size();
        allocInitial();
        trimSnapshotTimes(Long.MAX_VALUE);
        stats.updateRemoved(rid, removeCount);
        assert validTimes();
        if (Collector.TRACE_LOG)
            log.trace("clearAllRecords " + this);
    }

    /**
     * Removes old records with time less than specified.
     */
    void removeOldRecords(long time, QDStats stats, int rid) {
        int index = min;
        int removeCount = 0;
        while (index != max) {
            boolean payload = isPayload(index);
            if (time(index) >= time && payload) {
                break;
            }
            if (payload) {
                removeCount++;
            }
            index = (index + 1) & mask;
        }
        clear(min, index);
        min = index;
        payloadSize -= removeCount;
        trimSnapshotTimes(time);
        stats.updateRemoved(rid, removeCount);
        assert validTimes();
        if (Collector.TRACE_LOG)
            log.trace("removeOldRecords " + this);
    }

    private void trimSnapshotTimes(long time) {
        snapshotTime = Math.max(snapshotTime, time);
        everSnapshotTime = Math.max(everSnapshotTime, time);
    }

    private int overallSize() {
        return (max - min) & mask;
    }

    int size() {
        return payloadSize;
    }

    long getMinAvailableTime() {
        return min == max ? 0 : time(min);
    }

    long getMaxAvailableTime() {
        return min == max ? 0 : time((max - 1) & mask);
    }

    int getAvailableCount(long startTime, long endTime) {
        if (min == max) {
            return 0;
        }
        if (startTime > endTime) {
            long t = startTime;
            startTime = endTime;
            endTime = t;
        }
        int count = 0;
        int start = (getIndexExclusive(startTime) + 1) & mask;
        int end = (getIndexInclusive(endTime) + 1) & mask;
        for (int i = start; i != end; i = (i + 1) & mask) {
            if (isPayload(i)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Examines data to the right-to-left in the specified range (inclusive start and end).
     *
     * <p><b>This method may produce inconsistent snapshot</b>
     * (that may be being updated by transaction) without setting tx flags.
     * Only subscription using {@link QDAgent} provides transaction consistency information.
     *
     * @return true when sink has no more capacity.
     */
    boolean examineDataRangeRTL(DataRecord record, int cipher, String symbol,
        long startTime, long endTime, RecordSink sink, RecordCursorKeeper keeper, Object attachment)
    {
        assert startTime >= endTime;
        long inSnapshotTime = Math.max(endTime, everSnapshotTime);
        boolean inSnapshot = wasEverSnapshotMode() && inSnapshotTime <= startTime;
        RecordCursor.Owner owner = keeper.getForHistoryBufferReadOnly(this, record, cipher, symbol);
        if (min != max) {
            // there is some actual data in buffer -- see if there any data in range
            int startIndex = findSmallerPayload(getIndexInclusive(startTime));
            int endIndex = findSmallerPayload(getIndexExclusive(endTime));
            if (startIndex != endIndex) {
                // there's some data in range -- let's produce proper events
                long lastTime = Long.MAX_VALUE;
                for (int index = startIndex; index != endIndex; index = (index - 1) & mask) {
                    if (!isPayload(index)) {
                        continue;
                    }
                    lastTime = time(index);
                    if (examineOne(sink, attachment, owner, index, isTx() ? TX_PENDING : 0))
                        return true;
                }
                if (inSnapshot && lastTime > inSnapshotTime) {
                    // generate separate virtual snapshot time event after data if needed
                    if (examineSnapshotTime(sink, attachment, inSnapshotTime, owner))
                        return true;
                }
                return false;
            }
        }
        // No actual data, but there is a snapshot in range -- mark it as such
        if (inSnapshot)
            return examineSnapshotTime(sink, attachment, inSnapshotTime, owner);
        // just no data otherwise
        return false;
    }

    /**
     * Examines data to the left-to-right in the specified range (inclusive start and end).
     *
     * <p><b>This method may produce inconsistent snapshot</b>
     * (that may be being updated by transaction) without setting tx flags.
     * Only subscription using {@link QDAgent} provides transaction consistency information.
     *
     * @return true when sink has no more capacity.
     */
    boolean examineDataRangeLTR(DataRecord record, int cipher, String symbol,
        long startTime, long endTime, RecordSink sink, RecordCursorKeeper keeper, Object attachment)
    {
        assert startTime <= endTime;
        long inSnapshotTime = Math.max(startTime, everSnapshotTime);
        boolean inSnapshot = wasEverSnapshotMode() && inSnapshotTime <= endTime;
        RecordCursor.Owner owner = keeper.getForHistoryBufferReadOnly(this, record, cipher, symbol);
        if (min != max) {
            // there is some actual data in buffer -- see if there any data in range
            int startIndex = findLargerPayload((getIndexExclusive(startTime) + 1) & mask);
            int endIndex = findLargerPayload((getIndexInclusive(endTime) + 1) & mask);
            if (startIndex != endIndex) {
                // there's some data in range -- let's produce proper events
                if (inSnapshot && time(startIndex) > inSnapshotTime) {
                    // generate separate virtual snapshot time event before data if needed
                    if (examineSnapshotTime(sink, attachment, inSnapshotTime, owner)) {
                        return true;
                    }
                }
                for (int index = startIndex; index != endIndex; index = (index + 1) & mask) {
                    if (!isPayload(index)) {
                        continue;
                    }
                    if (examineOne(sink, attachment, owner, index, isTx() ? TX_PENDING : 0)) {
                        return true;
                    }
                }
                return false;
            }
        }
        // No actual data, but there is a snapshot in range -- mark it as such
        if (inSnapshot)
            return examineSnapshotTime(sink, attachment, inSnapshotTime, owner);
        // just no data otherwise
        return false;
    }

    private boolean examineSnapshotTime(RecordSink sink, Object attachment, long time, RecordCursor.Owner owner) {
        // Use max index as a temp location -- it is never occupied
        setTime(max, time);
        return examineOne(sink, attachment, owner, max, REMOVE_EVENT | (isTx() ? TX_PENDING : 0));
    }

    /**
     * Examines all records.
     * This method updates {@link #nExamined} and {@link #examinedTime} as a side-effect.
     *
     * <p>This method method uses
     * {@link EventFlag#SNAPSHOT_BEGIN SNAPSHOT_BEGIN}, {@link EventFlag#SNAPSHOT_END SNAPSHOT_END},
     * {@link EventFlag#SNAPSHOT_SNIP SNAPSHOT_SNIP},
     * {@link EventFlag#REMOVE_EVENT REMOVE_EVENT}, and {@link EventFlag#TX_PENDING TX_PENDING} flags appropriately
     * to describe the snapshot and transaction state of stored data, as if the fresh subscription
     * with {@link QDAgent.Builder#withHistorySnapshot(boolean) history snapshot} was created.
     *
     * @return true when sink has no more capacity
     */
    boolean examineDataSnapshot(DataRecord record, int cipher, String symbol, long toTime, RecordSink sink,
        RecordCursorKeeper keeper, Object attachment)
    {
        return examineDataRetrieve(record, cipher, symbol, Long.MAX_VALUE, Math.max(toTime, everSnapshotTime),
            sink, keeper, attachment, Integer.MAX_VALUE, SNAPSHOT_BEGIN,
            toTime < snipSnapshotTime ? SNAPSHOT_SNIP : SNAPSHOT_END, false, true);
    }

    /**
     * Examines data from higher timeKnown exclusive to lower toTime inclusive.
     * This method updates {@link #nExamined} and {@link #examinedTime} as a side-effect.
     * This method produces snapshot end at {@code toTime}.
     *
     * @param timeKnown start retrieve from (exclusive).
     * @param toTime end retrieve at (inclusive).
     * @param sink the sink.
     * @param nLimit the maximal number of records to examine.
     * @param eventFlags a combination of TX_PENDING and SNAPSHOT_BEGIN as needed.
     * @param snapshotEndFlag != 0 to retrieve record at toTime with
     *              {@link EventFlag#SNAPSHOT_END} or {@link EventFlag#SNAPSHOT_SNIP} and
     *              generate a new event with {@link EventFlag#REMOVE_EVENT} if needed.
     * @param txEnd true when TX_PENDING flag shall be reset while examining record with time == toTime,
     * @param useFlags true when flags shall be set.
     * @return true when sink has no more capacity or examined up to nLimit.
     */
    boolean examineDataRetrieve(DataRecord record, int cipher, String symbol, long timeKnown, long toTime,
        RecordSink sink, RecordCursorKeeper keeper, Object attachment, int nLimit,
        int eventFlags, int snapshotEndFlag, boolean txEnd, boolean useFlags)
    {
        nExamined = 0;
        RecordCursor.Owner owner = keeper.getForHistoryBufferReadOnly(this, record, cipher, symbol);
        if (!wasEverSnapshotMode()) {
            eventFlags &= ~SNAPSHOT_BEGIN; // never seen SNAPSHOT_BEGIN from upstream, so don't send it downstream
            snapshotEndFlag &= ~SNAPSHOT_END; // nor SNAPSHOT_END flags (but still forward SNAPSHOT_SNIP!)
        }
        if (isTx())
            eventFlags |= TX_PENDING; // we're retrieving from HB that is in transaction
        if (min != max) {
            // there's some data in buffer to retrieve from
            int index = getIndexExclusive(timeKnown);
            int stopIndex = (min - 1) & mask;
            for (; index != stopIndex; index = (index - 1) & mask) {
                if (!isPayload(index)) {
                    continue;
                }
                long time = time(index);
                if (time < toTime)
                    break;
                if (time == toTime) {
                    eventFlags |= snapshotEndFlag;
                    if (txEnd)
                        eventFlags &= ~TX_PENDING; // end transaction on this last time
                }
                if (examineOneRetrieve(sink, time, attachment, owner, index, nLimit, useFlags ? eventFlags : 0))
                    return true;
                eventFlags &= ~SNAPSHOT_BEGIN; // only first is marked with SNAPSHOT_BEGIN
            }
        }
        if (useFlags && snapshotEndFlag != 0 && (nExamined == 0 || examinedTime > toTime)) {
            // Use max index as a temp location -- it is never occupied
            eventFlags |= snapshotEndFlag; // flag for snapshot end
            if (txEnd)
                eventFlags &= ~TX_PENDING; // end transaction on this last time
            setTime(max, toTime);
            return examineOneRetrieve(sink, toTime, attachment, owner, max, nLimit, useFlags ? (eventFlags | REMOVE_EVENT) : 0);
        }
        return false;
    }

    private boolean examineOneRetrieve(RecordSink sink, long time, Object attachment, RecordCursor.Owner owner,
        int index, int nLimit, int eventFlags)
    {
        if (!sink.hasCapacity() || nExamined >= nLimit)
            return true;
        // update count and time first...
        nExamined++;
        examinedTime = time;
        // ... because examineOne may throw exception
        return examineOne(sink, attachment, owner, index, eventFlags);
    }

    void setupOwner(RecordCursor.Owner owner, DataRecord record, int cipher, String symbol) {
        owner.setReadOnly(true);
        owner.setRecord(record, withEventTimeSequence ? RecordMode.TIMESTAMPED_DATA : RecordMode.DATA);
        owner.setSymbol(cipher, symbol);
        owner.setArrays(intValues, objValues);
    }

    private boolean examineOne(RecordSink sink, Object attachment, RecordCursor.Owner owner, int index,
        int eventFlags)
    {
        if (!sink.hasCapacity())
            return true;
        owner.setOffsets(index * intStep + intOffset, index * objStep + objOffset);
        owner.setAttachment(attachment);
        owner.setEventFlags(eventFlags);
        sink.append(owner.cursor());
        return false;
    }

    // for debug
    @Override
    public String toString() {
        return "HistoryBuffer{size=" + payloadSize +
            ", snapshotTime=" + snapshotTime +
            ", everSnapshotTime=" + everSnapshotTime +
            ", snipSnapshotTime=" + snipSnapshotTime +
            ((flags & SNAPSHOT_BEGIN_SEEN_FLAG) != 0 ? ", SNAPSHOT_BEGIN_SEEN" : "") +
            ((flags & SNAPSHOT_END_SEEN_FLAG) != 0 ? ", SNAPSHOT_END_SEEN" : "") +
            ((flags & EVER_SNAPSHOT_MODE_FLAG) != 0 ? ", EVER_SNAPSHOT_MODE_FLAG" : "") +
            ((flags & SWEEP_TX_FLAG) != 0 ? ", SWEEP_TX" : "") +
            ((flags & EXPLICIT_TX_FLAG) != 0 ? ", EXPLICIT_TX" : "") +
            '}';
    }

    /**
     * Buffer self-validation for tests.
     */
    @Internal
    void selfValidate() {
        assert intValues.length == (mask + 1) * intStep;
        assert objStep == 0 || objValues.length == (mask + 1) * objStep;
        assert min >= 0 && min <= mask;
        assert max >= 0 && max <= mask;
        int payload = 0;
        if (min != max) {
            assert isPayload(min);
            assert isPayload((max - 1) & mask);
            long prevTime = time(min);
            payload++;
            for (int i = (min + 1) & mask; i != max; i = (i + 1) & mask) {
                long time = time(i);
                assert prevTime < time;
                prevTime = time;
                if (isPayload(i))
                    payload++;
            }
        }
        int i = max;
        do {
            assert !isPayload(i);
            // do not check assert time(i) == 0; because of sometimes method `examineSnapshotTime`
            // can write time outside range [min, max)
            for (int j = 3; j < intStep; j++) {
                assert intValues[i * intStep + j] == 0;
            }

            if (objStep != 0) {
                for (int j = 0; j < objStep; j++) {
                    assert objValues[i * objStep + j] == null;
                }
            }
            i = (i + 1) & mask;
        } while (i != min);
        assert payloadSize == payload;
        //assert available == mask + 1 - payload;
    }
}
