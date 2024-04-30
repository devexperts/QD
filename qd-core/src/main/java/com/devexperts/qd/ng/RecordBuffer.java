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
package com.devexperts.qd.ng;

import com.devexperts.qd.DataBuffer;
import com.devexperts.qd.DataConsumer;
import com.devexperts.qd.DataIntField;
import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataListener;
import com.devexperts.qd.DataObjField;
import com.devexperts.qd.DataProvider;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataVisitor;
import com.devexperts.qd.SubscriptionBuffer;
import com.devexperts.qd.SubscriptionConsumer;
import com.devexperts.qd.SubscriptionFilter;
import com.devexperts.qd.SubscriptionIterator;
import com.devexperts.qd.SubscriptionListener;
import com.devexperts.qd.SubscriptionProvider;
import com.devexperts.qd.SubscriptionVisitor;
import com.devexperts.qd.ng.RecordCursor.Owner;
import com.devexperts.qd.util.DataIterators;
import com.devexperts.util.ArrayUtil;
import com.devexperts.util.SystemProperties;
import com.devexperts.util.ThreadLocalPool;

import java.util.Arrays;
import java.util.Objects;

/**
 * Buffers int and obj values of data records. This class has two {@link RecordCursor cursors} of its own --
 * one is used for read and is returned by {@link #cursorAt}, {@link #current},
 * and {@link #next} methods, the other is used for write and is returned
 * by {@link #add} method. This class is faster replacement for deprecated
 * {@link DataBuffer} and {@link SubscriptionBuffer} classes.
 *
 * <p><b>This class is not synchronized and is not thread-safe without external synchronization.</b>
 */
public final class RecordBuffer extends RecordSource implements
    RecordProvider, RecordSink,
    DataConsumer, SubscriptionConsumer, RecordConsumer
{
    /*
     * Default pool limit is 16K ints and 8K objs = 96-128KB of memory per buffer.
     * with 3 buffers per thread that is at most 288-384KB of memory per thread.
     * Default pooled buffer can hold 4096 regular subscription items
     * (symbol + record = 2 objs, cipher + flags + [time] = 2/4 ints per sub item).
     * Note, that CollectorManagement.DEFAULT_SUBSCRIPTION_BUCKET is 10000,
     * so each pooled buffer's worth of subscription is proceed in one batch.
     */

    private static final int MAX_POOLED_INTS =
        SystemProperties.getIntProperty(RecordBuffer.class, "maxPooledInts", 16384);
    private static final int MAX_POOLED_OBJS =
        SystemProperties.getIntProperty(RecordBuffer.class, "maxPooledObjs", 8192);

    private static int maxIntFields =
        SystemProperties.getIntProperty(RecordBuffer.class, "maxIntFields", 64);
    private static int maxObjFields =
        SystemProperties.getIntProperty(RecordBuffer.class, "maxObjFields", 32);

    // These constants are linked with same ones in DXFeedSubscription - OPTIMAL_BATCH_LIMIT and MAX_BATCH_LIMIT.
    private static final int POOLED_CAPACITY = 0;
    private static final int UNLIMITED_CAPACITY = Integer.MAX_VALUE;

    static final int INT_CIPHER = 0;
    static final int INT_FIELDS = 1;

    static final int OBJ_RECORD = 0;
    static final int OBJ_SYMBOL = 1;
    static final int OBJ_FIELDS = 2;

    private static final ThreadLocalPool<RecordBuffer> THREAD_LOCAL_POOL =
        new ThreadLocalPool<>(RecordBuffer.class.getName(), 3, 1024);

    /**
     * Returns instance from thread-local pool with a default mode of {@link RecordMode#DATA}.
     * This instance can be returned to pool with {@link #release} method.
     * The recommended usage pattern is:
     * <p><code>
     * RecordBuffer buf = RecordBuffer.getInstance();<br>
     * // do some local data transfer via buf<br>
     * buf.release();<br>
     * </code>
     *
     * <p>This instance has the same state as returned by a {@link #RecordBuffer() default} constructor.
     * In particular, it is not {@link #isCapacityLimited() capacity-limited} by default.
     */
    public static RecordBuffer getInstance() {
        return getInstance(RecordMode.DATA);
    }

    /**
     * Returns instance from thread-local pool with a specified mode.
     * This instance can be returned to pool with {@link #release} method.
     * The recommended usage pattern is:
     * <p><code>
     * RecordBuffer buf = RecordBuffer.getInstance(mode);<br>
     * // do some local data transfer via buf<br>
     * buf.release();<br>
     * </code>
     *
     * <p>This instance has the same state as returned by a
     * {@link #RecordBuffer(RecordMode) RecordBuffer(mode)} constructor.
     * In particular, it is not {@link #isCapacityLimited() capacity-limited} by default.
     */
    public static RecordBuffer getInstance(RecordMode mode) {
        RecordBuffer result = THREAD_LOCAL_POOL.poll();
        if (result == null)
            return new RecordBuffer(mode);
        result.released = false; // no longer released
        result.setMode(mode);
        result.setCapacityLimited(false);
        return result;
    }

    // ------------- instance fields -------------

    private final RecordCursor readCursor;
    private final RecordCursor writeCursor;

    private RecordMode mode;
    private int capacityLimit = UNLIMITED_CAPACITY;
    private boolean released;

    private int[] intFlds;
    private Object[] objFlds;
    private int intPosition;
    private int objPosition;
    private int intLimit;
    private int objLimit;
    private int size;

    // this is for legacy DataVisitor and DataIterator implementations only
    private int intFieldIndex;
    private int objFieldIndex;

    // ------------- constructor and public instance methods -------------

    /**
     * Creates new <code>RecordBuffer</code> with a default mode of {@link RecordMode#DATA DATA}.
     * The new record buffer is not {@link #isCapacityLimited() capacity-limited} by default.
     */
    public RecordBuffer() {
        this(RecordMode.DATA);
    }

    /**
     * Creates new <code>RecordBuffer</code> with a specified mode.
     * The new record buffer is not {@link #isCapacityLimited() capacity-limited} by default.
     */
    public RecordBuffer(RecordMode mode) {
        this.mode = Objects.requireNonNull(mode);
        intFlds = new int[32];
        objFlds = new Object[32];
        readCursor = new RecordCursor(true, mode);
        writeCursor = new RecordCursor(false, mode);
        reinitCursorArraysInternal();
    }

    /**
     * Releases this <code>RecordBuffer</code> to a thread-local pool. Many operations
     * on this buffer throw {@link IllegalStateException} when invoked on an object
     * that was released into pool (especially modifying ones). Read and write
     * cursors of this buffer become unusable, too.
     *
     * <p>There is a limit on how big can be instances (in terms of memory they consume)
     * that are stored in the pool. Instances that are too big are left for GC.
     *
     * @see #getInstance()
     * @throws IllegalStateException if object was already released.
     */
    public void release() {
        clear(); // will throw IllegalStateException if already released
        // mark as released to fail-fast on other modifying operations
        released = true;
        // put released buffer into pool
        if (intFlds.length > MAX_POOLED_INTS || objFlds.length > MAX_POOLED_OBJS)
            return; // too big. do not pool
        THREAD_LOCAL_POOL.offer(this);
    }

    /**
     * Returns {@code true} when this buffer is empty,
     * that is when its {@link #size() size} is zero.
     * @return {@code true} this buffer is empty.
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * Returns the number of records that were added to this RecordBuffer.
     */
    public int size() {
        return size;
    }

    /**
     * Returns current mode of this record buffer.
     * @return current mode of this record buffer.
     */
    @Override
    public RecordMode getMode() {
        return mode;
    }

    /**
     * Changes current mode of this record buffer. Invocation of this method with the current mode has no effect.
     * Mode can be changed only when buffer is {@link #isEmpty() empty}.
     * @param mode new mode.
     * @throws NullPointerException if mode is null.
     * @throws IllegalStateException if attempting to change mode of non-empty buffer.
     */
    public void setMode(RecordMode mode) {
        if (mode == null)
            Throws.throwNullPointerException();
        if (mode == this.mode)
            return; // no change
        if (size != 0)
            Throws.throwModeChangeNonEmpty(this.mode, mode);
        this.mode = mode;
    }

    /**
     * Returns {@code true} when this record buffer is capacity-limited.
     * When buffer is capacity-limited, then {@link #hasCapacity()} will
     * return {@code false} when adding more records into this buffer
     * may make it ineligible to be {@link #release() released} into pool.
     */
    public boolean isCapacityLimited() {
        return capacityLimit != UNLIMITED_CAPACITY;
    }

    /**
     * Changes capacity-limited flag of this record buffer.
     * @see #isCapacityLimited()
     */
    public void setCapacityLimited(boolean capacityLimited) {
        this.capacityLimit = capacityLimited ? POOLED_CAPACITY : UNLIMITED_CAPACITY;
    }

    /**
     * Sets the capacity limit in the number of records for this record buffer.
     *
     * @param capacityLimit the capacity limit
     * @throws IllegalArgumentException if capacityLimit < 0 (see {@link #POOLED_CAPACITY} or
     *     {@link #UNLIMITED_CAPACITY})
     */
    public void setCapacityLimit(int capacityLimit) {
        if (capacityLimit < 0)
            throw new IllegalArgumentException();
        this.capacityLimit = capacityLimit;
    }

    /**
     * Returns {@code true} when this record buffer is not {@link #isCapacityLimited() capacity-limited}
     * (which is default) or, when buffer is capacity-limited, when more records can be added to it.
     */
    @Override
    public boolean hasCapacity() {
        return size < capacityLimit ||
            (capacityLimit == POOLED_CAPACITY &&
                intLimit < MAX_POOLED_INTS - maxIntFields &&
                objLimit < MAX_POOLED_OBJS - maxObjFields);
    }

    /**
     * Returns <code>true</code> if this record buffer has an estimated capacity to hold a result
     * of parsing a specified number of bytes in binary encoding before becoming too big to be
     * {@link #release() released} into a pool.
     * It assumes that each int or object item takes at least one bytes.
     *
     * @param bytes the number of bytes.
     * @return <code>true</code> if this record buffer has an estimated capacity to hold a result
     *         of parsing a specified number of bytes in binary encoding.
     */
    public boolean hasEstimatedCapacityForBytes(int bytes) {
        return intLimit + bytes <= MAX_POOLED_INTS && objLimit + bytes <= MAX_POOLED_OBJS;
    }

    /**
     * Clears this record buffers completely, invalidating read and write cursors, resetting its
     * {@link #size() size}, {@link #getPosition() position} and {@link #getLimit() limit} to zero.
     * The {@link #getMode() mode} and {@link #isCapacityLimited() capacity-limiting}
     * of this record buffer remains the same.
     * @throws IllegalStateException if object was already {@link #release() released} into pool.
     */
    public void clear() {
        if (released)
            Throws.throwReleased();
        Arrays.fill(intFlds, 0, intLimit, 0);
        Arrays.fill(objFlds, 0, objLimit, null);
        resetCursorsAccess();
        intPosition = 0;
        objPosition = 0;
        intLimit = 0;
        objLimit = 0;
        size = 0;
    }

    /**
     * Sets {@link #getPosition() position} of this buffer to the beginning, so that added record can be retrieved again.
     * @throws IllegalStateException if object was already {@link #release() released} into pool.
     */
    public void rewind() {
        if (released)
            Throws.throwReleased();
        intPosition = 0;
        objPosition = 0;
    }

    /**
     * Returns new {@link RecordSource} that reads this record buffer from
     * current {@link #getPosition position} to its current {@link #getLimit limit} with the same
     * {@link #getMode() mode}.
     * It is equivalent to <code>newSource(getPosition(), getLimit())</code>.
     * @throws IllegalStateException if object was already {@link #release() released} into pool.
     * @see #newSource(long, long)
     */
    @Override
    public RecordSource newSource() {
        if (released)
            Throws.throwReleased();
        return new RecordBuffer(mode, intFlds, intLimit, intPosition, objFlds, objLimit, objPosition);
    }

    /**
     * Returns new {@link RecordSource} that reads this record buffer from
     * the specified start position to the specified end position.
     * @throws IllegalStateException if object was already {@link #release() released} into pool.
     * @see #newSource()
     */
    @Override
    public RecordSource newSource(long start, long end) {
        if (released)
            Throws.throwReleased();
        int ilim = (int) end;
        int olim = (int) (end >>> 32);
        if (ilim < 0 || ilim > intLimit || olim < 0 || olim > objLimit)
            throw new IndexOutOfBoundsException("end");
        int ipos = (int) start;
        int opos = (int) (start >>> 32);
        if (ipos < 0 || ipos > ilim || opos < 0 || opos > olim)
            throw new IndexOutOfBoundsException("start");
        return new RecordBuffer(mode, intFlds, ilim, ipos, objFlds, olim, opos);
    }

    /**
     * Returns limit of this buffer,
     * which is the position of the record that will be added with {@link #add} method.
     */
    @Override
    public long getLimit() {
        return RecordCursor.makePosition(intLimit, objLimit);
    }

    /**
     * Returns position of the current record.
     * This record will be read by {@link #next} and {@link #current} methods.
     */
    @Override
    public long getPosition() {
        return RecordCursor.makePosition(intPosition, objPosition);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setPosition(long position) {
        int ipos = (int) position;
        int opos = (int) (position >>> 32);
        if (ipos < 0 || ipos > intLimit || opos < 0 || opos > objLimit)
            throw new IndexOutOfBoundsException();
        intPosition = ipos;
        objPosition = opos;
    }

    /**
     * Sets new buffer limit and discards everything that written after that point.
     * {@link #size() Size} is reduced by the number of discarded records.
     * This method also resets write cursor to avoid accidental corruption of data.
     * Only values that were previously returned by {@link #getPosition()} or {@link #getLimit()} methods
     * are allowed here.
     * @throws IndexOutOfBoundsException if limit is invalid or above current limit.
     */
    public void setLimit(long limit) {
        int ilim = (int) limit;
        int olim = (int) (limit >>> 32);
        setLimitInternal(ilim, olim);
        readCursor.resetAccessInternal(intLimit, objLimit);
        writeCursor.resetAccessInternal(intLimit, objLimit);
    }

    /**
     * Returns record at the specified position.
     * This position should have been previously returned by {@link #getPosition()} method.
     *
     * @param position position of the record to return.
     * @return record at the specified position.
     * @throws IndexOutOfBoundsException if position is invalid or above current limit.
     */
    public DataRecord getRecordAt(long position) {
        int opos = (int) (position >>> 32);
        if (opos < 0 || opos >= objLimit)
            Throws.throwIndexOutOfBoundsException(opos, objLimit);
        return (DataRecord) objFlds[opos + OBJ_RECORD];
    }

    /**
     * Returns cipher at the specified position.
     * This position should have been previously returned by {@link #getPosition()} method.
     *
     * @param position position of the cipher to return.
     * @return cipher at the specified position.
     * @throws IndexOutOfBoundsException if position is invalid or above current limit.
     */
    public int getCipherAt(long position) {
        int ipos = (int) position;
        if (ipos < 0 || ipos >= intLimit)
            Throws.throwIndexOutOfBoundsException(ipos, intLimit);
        return intFlds[ipos + INT_CIPHER];
    }

    /**
     * Returns symbol at the specified position.
     * This position should have been previously returned by {@link #getPosition()} method.
     *
     * @param position position of the symbol to return.
     * @return symbol at the specified position.
     * @throws IndexOutOfBoundsException if position is invalid or above current limit.
     */
    public String getSymbolAt(long position) {
        int opos = (int) (position >>> 32);
        if (opos < 0 || opos >= objLimit)
            Throws.throwIndexOutOfBoundsException(opos, objLimit);
        return (String) objFlds[opos + OBJ_SYMBOL];
    }

    /**
     * Returns event time sequence at the specified position.
     * This position should have been previously returned by {@link #getPosition()} method.
     * This method returns zero when {@link #getMode()}.{@link RecordMode#hasEventTimeSequence() hasEventTimeSequence()} returns {@code false}.
     *
     * @param position position of the event time sequence to return.
     * @return event time sequence at the specified position.
     * @throws IndexOutOfBoundsException if position is invalid or above current limit.
     */
    public long getEventTimeSequenceAt(long position) {
        if (mode.eventTimeSequenceOfs == 0)
            return 0;
        int ipos = (int) position;
        if (ipos < 0 || ipos >= intLimit)
            Throws.throwIndexOutOfBoundsException(ipos, intLimit);
        int index = ipos + mode.intBufOffset + mode.eventTimeSequenceOfs;
        return ((long) intFlds[index] << 32) | ((long) intFlds[index + 1] & 0xFFFFFFFFL);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecordCursor cursorAt(long position) {
        int ipos = (int) position;
        int opos = (int) (position >>> 32);
        if (ipos < 0 || ipos >= intLimit) {
            if (ipos == intLimit && opos == objLimit)
                return null; // return null cursor at limit.
            Throws.throwIndexOutOfBoundsException(ipos, intLimit);
        }
        if (opos < 0 || opos >= objLimit)
            Throws.throwIndexOutOfBoundsException(opos, objLimit);
        return setCursorInternal(readCursor, ipos, opos);
    }

    /**
     * Returns write cursor at a specified position. This position should have been previously
     * returned by {@link #getPosition()} method.
     * @return Write cursor at the specified position.
     * @throws IndexOutOfBoundsException if position is invalid or above current limit.
     */
    public RecordCursor writeCursorAt(long position) {
        int ipos = (int) position;
        int opos = (int) (position >>> 32);
        if (ipos < 0 || ipos >= intLimit)
            Throws.throwIndexOutOfBoundsException(ipos, intLimit);
        if (opos < 0 || opos >= objLimit)
            Throws.throwIndexOutOfBoundsException(opos, objLimit);
        return setCursorInternal(writeCursor, ipos, opos);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecordCursor current() {
        if (intPosition >= intLimit)
            return null;
        return setCursorInternal(readCursor, intPosition, objPosition);
    }

    /**
     * Returns write cursor at the current position. Effect is same as
     * <code>writeCursorAt(getPosition())</code>, but faster.
     * @return Write cursor at the current position.
     */
    public RecordCursor writeCurrent() {
        if (intPosition >= intLimit)
            return null;
        return setCursorInternal(writeCursor, intPosition, objPosition);
    }

    /**
     * Returns {@code true} when more records can be read from this buffer,
     * that is when {@link #getPosition() position} is less than {@link #getLimit() limit}.
     * @return {@code true} when more records can be read from this buffer.
     */
    public boolean hasNext() {
        return intPosition < intLimit;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public RecordCursor next() {
        if (intPosition >= intLimit)
            return null;
        RecordCursor cursor = setCursorInternal(readCursor, intPosition, objPosition);
        incPosition(cursor);
        return cursor;
    }

    /**
     * Returns write cursor at the current position and advances position to next record.
     * The result of this method is {@code null} when
     * {@link #getPosition() position} is equal to {@link #getLimit() limit}.
     * @return write cursor at the current position.
     */
    public RecordCursor writeNext() {
        if (intPosition >= intLimit)
            return null;
        RecordCursor cursor = setCursorInternal(writeCursor, intPosition, objPosition);
        incPosition(cursor);
        return cursor;
    }

    /**
     * Returns write cursor at the current limit and advances limit.
     * @return write cursor to the recently added record.
     * @throws IllegalStateException if object was already {@link #release() released} into pool.
     */
    public RecordCursor add(DataRecord record, int cipher, String symbol) {
        if (released)
            Throws.throwReleased();
        initWriteCursorInternal(record, cipher, symbol);
        int iInc = mode.intBufOffset + writeCursor.intCount;
        int oInc = mode.objBufOffset + writeCursor.objCount;
        growIfNeeded(iInc, oInc);
        appendInternal(writeCursor, iInc, oInc);
        return writeCursor;
    }

    /**
     * Adds a record to this buffer from the specified {@link RecordCursor}
     * and returns write cursor to the recently added record.
     * This method is a shortcut to <pre>
     * add(from.getRecord(), from.getCipher(), from.getSymbol()).
     *     copyFrom(from)</pre>
     *
     * <p>Use faster {@link #append(RecordCursor)} method if you do not need to change added data.
     *
     * <p>This method also copies all extra information ({@link RecordMode#hasEventFlags() event flags},
     * {@link RecordMode#hasTimeMark() time marks}, {@link RecordMode#hasEventTimeSequence() time sequence},
     * and {@link RecordMode#hasAttachment() attachment}) that can be
     * stored under this buffer's mode (note that {@link RecordMode#hasLink() links} are not copied),
     * including cursor-local event flags, time mark and attachment that can be set via its owner's
     * {@link Owner#setEventFlags(int) Owner.setEventFlags},
     * {@link Owner#setTimeMark(int) Owner.setTimeMark}, and
     * {@link Owner#setAttachment(Object) Owner.setAttachment} methods.
     *
     * @return write cursor to the recently added record.
     * @throws IllegalStateException if object was already {@link #release() released} into pool.
     */
    public RecordCursor add(RecordCursor from) {
        add(from.getRecord(), from.getCipher(), from.getSymbol()).copyFrom(from);
        return writeCursor;
    }

    /**
     * Adds a record to this buffer from the specified {@link RecordCursor}.
     * The minimal subset of fields based on current {@link #getMode() mode} and
     * {@link RecordCursor#getMode() cursor mode} is copied.
     * All values are copied when both modes are {@link RecordMode#DATA DATA}, only time if at least one
     * of them is {@link RecordMode#HISTORY_SUBSCRIPTION HISTORY_SUBSCRIPTION}, and nothing is copied
     * if at least one of them is {@link RecordMode#SUBSCRIPTION SUBSCRIPTION} (without time).
     *
     * <p>This method also copies all extra information ({@link RecordMode#hasEventFlags() event flags},
     * {@link RecordMode#hasTimeMark() time marks}, {@link RecordMode#hasEventTimeSequence() time sequence},
     * and {@link RecordMode#hasAttachment() attachment}) that can be
     * stored under this buffer's mode (note that {@link RecordMode#hasLink() links} are not copied),
     * including cursor-local event flags, time mark and attachment that can be set via its owner's
     * {@link Owner#setEventFlags(int) Owner.setEventFlags},
     * {@link Owner#setTimeMark(int) Owner.setTimeMark}, and
     * {@link Owner#setAttachment(Object) Owner.setAttachment} methods.
     *
     * @param from the cursor to append from.
     * @throws IllegalStateException if object was already {@link #release() released} into pool.
     */
    @Override
    public void append(RecordCursor from) {
        if (released)
            Throws.throwReleased();
        DataRecord record = from.getRecord();
        int intFieldCount = mode.intFieldCount(record);
        int objFieldCount = mode.objFieldCount(record);
        int iInc = mode.intBufOffset + intFieldCount;
        int oInc = mode.objBufOffset + objFieldCount;
        growIfNeeded(iInc, oInc);
        appendInternal(from, iInc, oInc);
        int iCount = Math.min(intFieldCount, from.intCount);
        int oCount = Math.min(objFieldCount, from.objCount);
        from.copyDataInternalTo(iCount, oCount,
            intFlds, intLimit - intFieldCount, objFlds, objLimit - objFieldCount);
        from.copyExtraInternalTo(mode,
            intFlds, intLimit - intFieldCount, objFlds, objLimit - objFieldCount);
    }

    /**
     * Adds record with data from the specified {@link RecordCursor}.
     * If the specified cursor has mode that does not have full data
     * ({@link RecordMode#HISTORY_SUBSCRIPTION} or {@link RecordMode#SUBSCRIPTION}),
     * then the missing data fields are left at default values.
     *
     * <p>Buffer is {@link #compact() compacted} if it runs out of capacity.
     * After compaction all previously queried long positions (via {@link #getPosition} or
     * {@link #getLimit} methods) or read cursors become invalid.
     *
     * <p>Extra information from the cursor (like event time marks, flags, etc) is <b>NOT</b> added.
     *
     * @param from the cursor to copy data from.
     * @return write cursor to the recently added record.
     * @throws IllegalStateException if object was already {@link #release() released} into pool.
     * @see RecordCursor#copyDataFrom(RecordCursor)
     */
    public RecordCursor addDataAndCompactIfNeeded(RecordCursor from) {
        if (released)
            Throws.throwReleased();
        DataRecord record = from.getRecord();
        int intFieldCount = mode.intFieldCount(record);
        int objFieldCount = mode.objFieldCount(record);
        int iInc = mode.intBufOffset + intFieldCount;
        int oInc = mode.objBufOffset + objFieldCount;
        growOrCompactIfNeeded(iInc, oInc);
        readCursor.resetAccessInternal();
        initWriteCursorInternal(record, from.getCipher(), from.getSymbol());
        appendInternal(from, iInc, oInc);
        int iCount = Math.min(intFieldCount, from.intCount);
        int oCount = Math.min(objFieldCount, from.objCount);
        from.copyDataInternalTo(iCount, oCount,
            intFlds, intLimit - intFieldCount, objFlds, objLimit - objFieldCount);
        return writeCursor;
    }

    /**
     * Replaces record at the specified position.
     * @param position the position.
     * @param newRecord new record.
     * @throws IllegalArgumentException if replacing cannot be performed because different number of fields
     *         needs to be stored between old and new record, which depends on {@link #getMode() mode}.
     * @throws IndexOutOfBoundsException if position is invalid or above current limit.
     */
    public void replaceRecordAt(long position, DataRecord newRecord) {
        int opos = (int) (position >>> 32);
        if (opos < 0 || opos >= objLimit)
            Throws.throwIndexOutOfBoundsException(opos, objLimit);
        DataRecord oldRecord = (DataRecord) objFlds[opos + OBJ_RECORD];
        if (mode.differentIntFieldCount(oldRecord, newRecord) || mode.differentObjFieldCount(oldRecord, newRecord))
            Throws.throwDifferentNumberOfFields(newRecord, oldRecord);
        objFlds[opos + OBJ_RECORD] = newRecord;

        if (readCursor.getObjPositionInternal() == opos) {
            readCursor.setRecordInternal(newRecord, mode);
        }
        if (writeCursor.getObjPositionInternal() == opos) {
            writeCursor.setRecordInternal(newRecord, mode);
        }
    }

    /**
     * Replaces symbol at the specified position.
     * @param position the position.
     * @param cipher new cipher.
     * @param symbol new symbol.
     * @throws IndexOutOfBoundsException if position is invalid or above current limit.
     */
    public void replaceSymbolAt(long position, int cipher, String symbol) {
        int ipos = (int) position;
        int opos = (int) (position >>> 32);
        if (ipos < 0 || ipos >= intLimit)
            Throws.throwIndexOutOfBoundsException(ipos, intLimit);
        if (opos < 0 || opos >= objLimit)
            Throws.throwIndexOutOfBoundsException(opos, objLimit);
        intFlds[ipos + INT_CIPHER] = cipher;
        objFlds[opos + OBJ_SYMBOL] = symbol;

        if (readCursor.getIntPositionInternal() == ipos && readCursor.getObjPositionInternal() == opos) {
            readCursor.setSymbolInternal(cipher, symbol);
        }
        if (writeCursor.getIntPositionInternal() == ipos && writeCursor.getObjPositionInternal() == opos) {
            writeCursor.setSymbolInternal(cipher, symbol);
        }
    }

    /**
     * Removes record at the specified position. All records to the right are shifted.
     * Limit is correspondingly decreased, position have correct value.
     * This method also resets both cursors to avoid accidental corruption of data.
     * @throws IndexOutOfBoundsException if position is invalid or above current limit.
     */
    public void removeAt(long position) {
        int ipos = (int) position;
        int opos = (int) (position >>> 32);

        if (ipos < 0 || ipos >= intLimit) {
            Throws.throwIndexOutOfBoundsException(ipos, intLimit);
        }
        if (opos < 0 || opos >= objLimit) {
            Throws.throwIndexOutOfBoundsException(opos, objLimit);
        }

        DataRecord record = (DataRecord) objFlds[opos + OBJ_RECORD];
        int iremove = mode.intBufOffset + mode.intFieldCount(record);
        int oremove = mode.objBufOffset + mode.objFieldCount(record);

        removeImpl(ipos, opos, iremove, oremove, 1);
    }

    /**
     * Removes records from the specified fromPosition inclusive and toPosition exclusive.
     * All records to the right are shifted. Limit is correspondingly decreased, position have correct value.
     * This method also corrects both cursors or resets them in case the cursors belong to a remove range.
     * @param fromPosition start position
     * @param toPosition end position, exclusive
     * @throws IndexOutOfBoundsException if position is invalid or above current limit.
     * @throws IllegalArgumentException if input positions and internal state of buffer are mismatched.
     */
    public void removeRange(long fromPosition, long toPosition) {
        int iFromPos = (int) fromPosition;
        int oFromPos = (int) (fromPosition >>> 32);

        int iToPos = (int) toPosition;
        int oToPos = (int) (toPosition >>> 32);

        if (iFromPos < 0 || iToPos > intLimit || iFromPos > iToPos) {
            Throws.throwIndexOutOfBoundsRangeCheckException(iFromPos, iToPos, intLimit);
        }
        if (oFromPos < 0 || oToPos > objLimit || oFromPos > oToPos) {
            Throws.throwIndexOutOfBoundsRangeCheckException(oFromPos, oToPos, objLimit);
        }

        int count = 0;
        int iPos = iFromPos;
        int oPos = oFromPos;
        while (iPos < iToPos && oPos < oToPos) {
            DataRecord record = (DataRecord) objFlds[oPos + OBJ_RECORD];
            iPos += mode.intBufOffset + mode.intFieldCount(record);
            oPos += mode.objBufOffset + mode.objFieldCount(record);
            count++;
        }

        // additional check integrity of internal structure and input params
        if (iPos != iToPos || oPos != oToPos) {
            Throws.throwInvalidStateOrParameters(iFromPos, oFromPos, iToPos, oToPos);
        }

        if (count > 0) {
            removeImpl(iFromPos, oFromPos, iToPos - iFromPos, oToPos - oFromPos, count);
        }
    }

    /**
     * Adds all records from the specified source to this buffer.
     * This is a shortcut for <code>{@link #process(RecordSource, SubscriptionFilter) process}(source, null)</code>.
     * @param source the source.
     * @throws IllegalStateException when adding records to an object that was already {@link #release() released} into pool.
     */
    @Override
    public void process(RecordSource source) {
        process(source, null);
    }

    /**
     * Adds all records that match a given filter from the specified source to this buffer.
     * @param source the source.
     * @param filter the filter.
     * @throws IllegalStateException when adding records to an object that was already {@link #release() released} into pool.
     */
    public void process(RecordSource source, SubscriptionFilter filter) {
        if (released)
            Throws.throwReleased();
        if (source instanceof RecordBuffer && filter == null) {
            if (fastCopyFrom((RecordBuffer) source))
                return;
        }
        for (RecordCursor cursor; (cursor = source.next()) != null;)
            if (filter == null || filter.acceptRecord(cursor.getRecord(), cursor.getCipher(), cursor.getSymbol()))
                append(cursor);
    }

    /**
     * Adds all records from the specified source to this buffer.
     * @param source the source.
     * @deprecated Use {@link #process(RecordSource)}.
     * @throws IllegalStateException when adding records to an object that was already {@link #release() released} into pool.
     */
    public void addAll(RecordSource source) {
        process(source, null);
    }

    /**
     * Adds all records that match a given filter from the specified source to this buffer.
     * @param source the source.
     * @param filter the filter.
     * @deprecated Use {@link #process(RecordSource, SubscriptionFilter)}.
     * @throws IllegalStateException when adding records to an object that was already {@link #release() released} into pool.
     */
    public void addAll(RecordSource source, SubscriptionFilter filter) {
        process(source, filter);
    }

    /**
     * Processes data from specified iterator and adds to this record buffer all records that are
     * accepted by the specified filter.
     * When iterator implements {@link RecordSource} interface, this method calls
     * {@link #addAll(RecordSource, SubscriptionFilter) addAll((RecordSource) it, filter)}.
     * Use {@link #addAll(RecordSource)} or {@link #addAll(RecordSource, SubscriptionFilter)} directly when
     * iterator is statically known to be {@link RecordSource} in the calling code.
     * @throws IllegalStateException when adding records to an object that was already {@link #release() released} into pool.
     */
    public void processData(DataIterator it, SubscriptionFilter filter) {
        if (it instanceof RecordSource) {
            process((RecordSource) it, filter);
            return;
        }
        DataRecord record;
        while ((record = it.nextRecord()) != null) {
            int cipher = it.getCipher();
            String symbol = it.getSymbol();
            if (filter == null || filter.acceptRecord(record, cipher, symbol))
                add(record, cipher, symbol).copyFrom(it);
            else
                DataIterators.skipRecord(record, it);
        }
    }

    /**
     * Processes subscription from specified iterator and adds to this record buffer all records that are
     * accepted by the specified filter.
     * When iterator implements {@link RecordSource} interface, this method calls
     * {@link #addAll(RecordSource, SubscriptionFilter) addAll((RecordSource) it, filter)}.
     * Use {@link #addAll(RecordSource)} or {@link #addAll(RecordSource, SubscriptionFilter)} directly when
     * iterator is statically known to be {@link RecordSource} in the calling code.
     * @throws IllegalStateException when adding records to an object that was already {@link #release() released} into pool.
     */
    public void processSubscription(SubscriptionIterator it, SubscriptionFilter filter) {
        if (it instanceof RecordSource) {
            process((RecordSource) it, filter);
            return;
        }
        DataRecord record;
        while ((record = it.nextRecord()) != null) {
            int cipher = it.getCipher();
            String symbol = it.getSymbol();
            if (filter == null || filter.acceptRecord(record, cipher, symbol))
                add(record, cipher, symbol).setTime(it.getTime());
        }
    }

    /**
     * Returns {@link DataVisitor} that adds all records to this buffer.
     * This implementation returns {@code this}.
     * @deprecated Use this implementation of {@link DataVisitor} interface directly if absolutely needed
     *             and reconsider this use completely, because {@link DataVisitor} is a legacy interface.
     */
    public DataVisitor visitor() {
        return this;
    }

    /**
     * Compacts data by copying everything between position and limit to the beginning of the buffer.
     * {@link #size() Size} is set to the number of remaining records.
     * After compaction all previously queried long positions (via {@link #getPosition} or
     * {@link #getLimit} methods) or read/write cursors become invalid. This method has the same effect as
     * {@link #compact(RecordFilter) compact(null)}.
     * @throws IllegalStateException if object was already {@link #release() released} into pool.
     */
    public void compact() {
        if (released)
            Throws.throwReleased();
        if (intPosition == 0 && objPosition == 0)
            return; // nothing to do
        compactArrays();
        resetCursorsAccess();
        // recompute size
        size = computeIteratorSize();
    }

    /**
     * Compacts data by copying all records that are accepted by filter between position and limit to
     * the beginning of the buffer.
     * {@link #size() Size} is set to the number of remaining records.
     * After compaction all previously queried long positions (via {@link #getPosition} or
     * {@link #getLimit} methods) or read/write cursors become invalid.
     * @param filter Filter for records. Only accepted records are retained.
     *        null filter is assumed to accept everything.
     * @return true if any data was filtered out (not accepted by the filter)
     * @throws IllegalStateException if object was already {@link #release() released} into pool.
     * @throws IllegalArgumentException if filter is not null and
     *         this buffer's {@link #getMode() mode} {@link RecordMode#hasLink() hasLink}.
     */
    public boolean compact(RecordFilter filter) {
        if (released)
            Throws.throwReleased();
        if (filter == null) {
            compact();
            return false;
        }
        if (mode.linkOfs != 0) {
            throw new IllegalArgumentException("Filtered compaction is not supported with link mode");
        }
        int intPtr = 0;
        int objPtr = 0;
        int filtered = 0;
        while (intPosition < intLimit && objPosition < objLimit) {
            RecordCursor cursor = setCursorInternal(readCursor, intPosition, objPosition);
            int intLength = mode.intBufOffset + cursor.intCount;
            int objLength = mode.objBufOffset + cursor.objCount;
            if (filter.accept(cursor)) {
                System.arraycopy(intFlds, intPosition, intFlds, intPtr, intLength);
                System.arraycopy(objFlds, objPosition, objFlds, objPtr, objLength);
                intPtr += intLength;
                objPtr += objLength;
                filtered++;
            }
            intPosition += intLength;
            objPosition += objLength;
        }
        Arrays.fill(intFlds, intPtr, intLimit, 0);
        Arrays.fill(objFlds, objPtr, objLimit, null);
        resetCursorsAccess();
        intPosition = 0;
        objPosition = 0;
        intLimit = intPtr;
        objLimit = objPtr;
        boolean result = size != filtered;
        size = filtered;
        return result;
    }

    /**
     * Cleanups the data at the corresponding cursor and reduces {@link #size()} by one.
     * The cursor should have been retrieved via one of the following methods:
     * {@link RecordBuffer#next next},
     * {@link RecordBuffer#current current},
     * {@link RecordBuffer#add(com.devexperts.qd.DataRecord, int, String) add(record,cipher,symbol)},
     * {@link RecordBuffer#add(RecordCursor) add(cursor)},
     * {@link RecordBuffer#cursorAt cursorAt},
     * {@link RecordBuffer#writeCursorAt writeCursorAt}
     * @throws IndexOutOfBoundsException if cursor's position is invalid or above current limit.
     * @throws IllegalStateException if the data at the corresponding cursor was already cleared.
     */
    public void cleanup(RecordCursor cursor) {
        int ipos = cursor.getIntPositionInternal();
        int opos = cursor.getObjPositionInternal();
        if (ipos < 0 || ipos >= intLimit)
            Throws.throwIndexOutOfBoundsException(ipos, intLimit);
        if (opos < 0 || opos >= objLimit)
            Throws.throwIndexOutOfBoundsException(opos, objLimit);
        if (objFlds[opos + OBJ_RECORD] == null)
            Throws.throwCleared();
        Arrays.fill(intFlds, ipos, ipos + mode.intBufOffset + cursor.intCount, 0);
        Arrays.fill(objFlds, opos, opos + mode.objBufOffset + cursor.objCount, null);
        // TODO probably shall call resetCursorsAccess() here
        size--;
    }

    /**
     * Unlinks records by following back a chain of {@link RecordCursor#setLinkTo(long) links}
     * from the given {@code position} up until (but not before) this buffer's {@link #getPosition() position}.
     * Unlinked records are marked with <code>true</code> result of
     * {@link RecordCursor#isUnlinked() RecordCursor.isUnlinked()} method.
     * If the record at the given position was not previously linked with
     * {@link RecordCursor#setLinkTo(long) RecordCursor.setLinkTo} method, then only one record is unlinked,
     * otherwise the chain of links is followed till the head of the record buffer.
     *
     * @param position the position to start at.
     * @throws IllegalStateException if the record at the specified position was already unlinked or
     *         if this buffer's {@link #getMode() mode} does not have {@link RecordMode#hasLink() links}.
     */
    public void unlinkFrom(long position) {
        if (mode.linkOfs == 0)
            throw new IllegalStateException("unlinkFrom(...) requires mode with link");
        int ipos = (int) position;
        if (ipos < 0 || ipos >= intLimit)
            Throws.throwIndexOutOfBoundsException(ipos, intLimit);
        while (ipos >= intPosition) {
            int oldLink = intFlds[ipos + mode.intBufOffset + mode.linkOfs];
            if (oldLink == RecordCursor.UNLINKED)
                throw new IllegalStateException("Already unlinked at " + ipos);
            intFlds[ipos + mode.intBufOffset + mode.linkOfs] = RecordCursor.UNLINKED;
            if (oldLink == 0)
                break; // that was last item in link chain
            ipos -= oldLink;
        }
    }

    /**
     * Flags records that are found by following back a chain of {@link RecordCursor#setLinkTo(long) links}
     * from the given {@code position} up until (but not before) this buffer's {@link #getPosition() position}.
     * Record's {@link RecordCursor#getEventFlags() event flags} are <b>OR-ed</b> with a give {@code eventFlags}.
     *
     * @param position the position to start at.
     * @throws IllegalStateException if the record at the specified position was {@link #unlinkFrom(long) unlinked} or
     *         if this buffer's {@link #getMode() mode} does not have {@link RecordMode#hasLink() links}
     *         or does not have {@link RecordMode#hasEventFlags() event flags}.
     */
    public void flagFrom(long position, int eventFlags) {
        if (mode.linkOfs == 0)
            throw new IllegalStateException("flagFrom(...) requires mode with link");
        if (mode.eventFlagsOfs == 0)
            throw new IllegalStateException("flagFrom(...) requires mode with event flags");
        int ipos = (int) position;
        if (ipos < 0 || ipos >= intLimit)
            Throws.throwIndexOutOfBoundsException(ipos, intLimit);
        while (ipos >= intPosition) {
            int link = intFlds[ipos + mode.intBufOffset + mode.linkOfs];
            if (link == RecordCursor.UNLINKED)
                throw new IllegalStateException("Already unlinked at " + ipos);
            intFlds[ipos + mode.intBufOffset + mode.eventFlagsOfs] |= eventFlags;
            if (link == 0)
                break; // that was last item in link chain
            ipos -= link;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean retrieve(RecordSink sink) {
        while (sink.hasCapacity()) {
            RecordCursor cursor = next();
            if (cursor == null)
                return false;
            sink.append(cursor);
        }
        return hasNext();
    }

    /**
     * This method throws {@link UnsupportedOperationException}.
     * @param listener the listener.
     */
    @Override
    public void setRecordListener(RecordListener listener) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     * <p>This implementation does nothing.
     */
    @Override
    public void flush() {}

    //------ DataConsumer interface implementation

    /**
     * Processes data from specified iterator and adds to this record buffer all records.
     * This implementation calls {@link #processData(DataIterator, SubscriptionFilter) processData(it, null)}.
     * Use {@link #addAll(RecordSource)} or {@link #addAll(RecordSource, SubscriptionFilter)} when
     * iterator is statically known to be {@link RecordSource} in the calling code.
     * @throws IllegalStateException when adding records to an object that was already {@link #release() released} into pool.
     */
    @Override
    public void processData(DataIterator it) {
        processData(it, null);
    }

    //------ SubscriptionConsumer interface implementation

    /**
     * Processes subscription from specified iterator and adds to this record buffer all records.
     * This implementation calls {@link #processSubscription(SubscriptionIterator, SubscriptionFilter) processSubscription(it, null)}.
     * Use {@link #addAll(RecordSource)} or {@link #addAll(RecordSource, SubscriptionFilter)} when
     * iterator is statically known to be {@link RecordSource} in the calling code.
     * @throws IllegalStateException when adding records to an object that was already {@link #release() released} into pool.
     */
    @Override
    public void processSubscription(SubscriptionIterator it) {
        processSubscription(it, null);
    }

    //------ DataIterator & SubscriptionIterator interface implementations

    /**
     * @deprecated Use {@link #next()}
     */
    @Override
    public DataRecord nextRecord() {
        if (next() == null) {
            readCursor.resetAccessInternal(); // prevent further access
            return null;
        }
        intFieldIndex = 0;
        objFieldIndex = 0;
        return readCursor.getRecord();
    }

    /**
     * @deprecated Use {@link #next()} and {@link RecordCursor#getCipher()}
     */
    @Override
    public int getCipher() {
        return readCursor.getCipher();
    }

    /**
     * @deprecated Use {@link #next()} and {@link RecordCursor#getSymbol()}
     */
    @Override
    public String getSymbol() {
        return readCursor.getSymbol();
    }

    /**
     * @deprecated Use {@link #next()} and {@link RecordCursor#getTime()}
     */
    @Override
    public long getTime() {
        return readCursor.getTime();
    }

    /**
     * @deprecated Use {@link #next()} and {@link RecordCursor#getInt}
     */
    @Override
    public int nextIntField() {
        return readCursor.getInt(intFieldIndex++);
    }

    /**
     * @deprecated Use {@link #next()} and {@link RecordCursor#getObj}
     */
    @Override
    public Object nextObjField() {
        return readCursor.getObj(objFieldIndex++);
    }

    //------ DataVisitor interface implementation

    /**
     * @deprecated Use {@link #add} to add records into this buffer.
     * @throws IllegalStateException if this cursor mode is not {@link RecordMode#DATA DATA}.
     */
    @Override
    public void visitRecord(DataRecord record, int cipher, String symbol) {
        if (!mode.hasData())
            Throws.throwWrongMode(mode);
        add(record, cipher, symbol);
        intFieldIndex = 0;
        objFieldIndex = 0;
    }

    /**
     * @deprecated Use {@link #add} to add records into this buffer.
     */
    @Override
    public void visitRecord(DataRecord record, int cipher, String symbol, long time) {
        add(record, cipher, symbol).setTime(time);
    }

    /**
     * @deprecated Use {@link #add} to add records into this buffer.
     */
    @Override
    public void visitIntField(DataIntField field, int value) {
        writeCursor.setInt(intFieldIndex++, value);
    }

    /**
     * @deprecated Use {@link #add} to add records into this buffer.
     */
    @Override
    public void visitObjField(DataObjField field, Object value) {
        writeCursor.setObj(objFieldIndex++, value);
    }

    //------ DataProvider interface implementation

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #retrieve(RecordSink)}
     */
    @Override
    public boolean retrieveData(DataVisitor visitor) {
        if (visitor instanceof RecordSink)
            return retrieve((RecordSink) visitor);
        while (visitor.hasCapacity()) {
            RecordCursor cursor = next();
            if (cursor == null)
                return false;
            if (cursor.examineData(visitor))
                throw new IllegalStateException("Second capacity check returns different result.");
        }
        return true;
    }

    /**
     * This method is provided for compatibility with a legacy {@link DataProvider} interface
     * and throws {@link UnsupportedOperationException}.
     */
    @Override
    public void setDataListener(DataListener listener) {
        throw new UnsupportedOperationException();
    }

    //------ SubscriptionProvider interface implementation

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #retrieve(RecordSink)}
     */
    @Override
    public boolean retrieveSubscription(SubscriptionVisitor visitor) {
        if (visitor instanceof RecordSink)
            return retrieve((RecordSink) visitor);
        while (visitor.hasCapacity()) {
            RecordCursor cursor = next();
            if (cursor == null)
                return false;
            if (cursor.examineSubscription(visitor) )
                throw new IllegalStateException("Second capacity check returns different result.");
        }
        return true;
    }

    /**
     * This method is provided for compatibility with a legacy {@link SubscriptionProvider} interface
     * and throws {@link UnsupportedOperationException}.
     */
    @Override
    public void setSubscriptionListener(SubscriptionListener listener) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns string representation of this buffer for debugging purposes.
     * @return string representation of this buffer for debugging purposes.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(mode).append(" buffer, size=").append(size);
        sb.append(", position=");
        RecordCursor.formatPosition(sb, intPosition, objPosition);
        sb.append(", limit=");
        RecordCursor.formatPosition(sb, intLimit, objLimit);
        if (capacityLimit == POOLED_CAPACITY) {
            sb.append(", capacityLimited");
        } else if (isCapacityLimited()) {
            sb.append(", capacityLimit=").append(capacityLimit);
        }
        return sb.toString();
    }

    //----------------------- internal API (package-private) -----------------------

    // internal constructor for newSource methods and for RecordSource.VOID constant
    RecordBuffer(RecordMode mode, int[] intFlds, int intLimit, int intPosition, Object[] objFlds, int objLimit, int objPosition) {
        this.mode = Objects.requireNonNull(mode);
        this.intFlds = intFlds;
        this.intLimit = intLimit;
        this.intPosition = intPosition;
        this.objFlds = objFlds;
        this.objLimit = objLimit;
        this.objPosition = objPosition;
        readCursor = new RecordCursor(true, mode);
        writeCursor = null; // "newSource" object are read-only and they don't need write cursors
        reinitCursorArraysInternal();
    }

    void reinitCursorArraysInternal() {
        readCursor.setArraysInternal(intFlds, objFlds);
        if (writeCursor != null)
            writeCursor.setArraysInternal(intFlds, objFlds);
    }

    RecordCursor setCursorInternal(RecordCursor cursor, int ipos, int opos) {
        cursor.setRecordInternal((DataRecord) objFlds[opos + OBJ_RECORD], mode);
        cursor.setSymbolInternal(intFlds[ipos + INT_CIPHER], (String) objFlds[opos + OBJ_SYMBOL]);
        cursor.setOffsetsInternal(ipos + mode.intBufOffset, opos + mode.objBufOffset);
        return cursor;
    }

    void setLimitInternal(int ilim, int olim) {
        if (ilim < 0 || ilim > intLimit || olim < 0 || olim > objLimit)
            throw new IndexOutOfBoundsException();
        size -= computeIteratorSize(olim, objLimit); // compute removed size before clearing data
        Arrays.fill(intFlds, ilim, intLimit, 0);
        Arrays.fill(objFlds, olim, objLimit, null);
        intLimit = ilim;
        objLimit = olim;
    }

    //----------------------- private utility methods -----------------------

    private boolean fastCopyFrom(RecordBuffer source) {
        if (source.intPosition >= source.intLimit)
            return true;
        if (source.mode != mode)
            return false;
        int iInc = source.intLimit - source.intPosition;
        int oInc = source.objLimit - source.objPosition;
        growIfNeeded(iInc, oInc);
        System.arraycopy(source.intFlds, source.intPosition, intFlds, intLimit, iInc);
        System.arraycopy(source.objFlds, source.objPosition, objFlds, objLimit, oInc);
        intLimit += iInc;
        objLimit += oInc;
        size += source.intPosition == 0 ? source.size : source.computeIteratorSize();
        source.intPosition = source.intLimit;
        source.objPosition = source.objLimit;
        return true;
    }

    private int computeIteratorSize() {
        return computeIteratorSize(objPosition, objLimit);
    }

    private int computeIteratorSize(int opos, int olim) {
        int s = 0;
        while (opos < olim) {
            opos += mode.objBufOffset + mode.objFieldCount((DataRecord) objFlds[opos + OBJ_RECORD]);
            s++;
        }
        return s;
    }

    private void removeImpl(int ipos, int opos, int iremove, int oremove, int count) {
        System.arraycopy(intFlds, ipos + iremove, intFlds, ipos, intLimit - iremove - ipos);
        System.arraycopy(objFlds, opos + oremove, objFlds, opos, objLimit - oremove - opos);
        Arrays.fill(intFlds, intLimit - iremove, intLimit, 0);
        Arrays.fill(objFlds, objLimit - oremove, objLimit, null);

        correctCursor(readCursor, ipos, opos, iremove, oremove);
        correctCursor(writeCursor, ipos, opos, iremove, oremove);

        if (intPosition >= ipos + iremove) {
            intPosition -= iremove;
        } else if (intPosition > ipos) {
            intPosition = ipos;
        }
        if (objPosition >= opos + oremove) {
            objPosition -= oremove;
        } else if (objPosition > opos) {
            objPosition = opos;
        }

        intLimit -= iremove;
        objLimit -= oremove;
        size -= count;
    }

    private void correctCursor(RecordCursor cursor, int ipos, int opos, int iremove, int oremove) {
        int intCursorPosition = cursor.getIntPositionInternal();
        int objCursorPosition = cursor.getObjPositionInternal();

        if (intCursorPosition >= ipos + iremove && objCursorPosition >= opos + oremove) {
            cursor.setOffsetsInternal(
                intCursorPosition - iremove + mode.intBufOffset,
                objCursorPosition - oremove + mode.objBufOffset);
        } else if (intCursorPosition >= ipos || objCursorPosition >= opos) {
            cursor.resetAccessInternal();
        }
    }

    private void incPosition(RecordCursor cursor) {
        intPosition += mode.intBufOffset + cursor.intCount;
        objPosition += mode.objBufOffset + cursor.objCount;
    }

    private void appendInternal(RecordCursor cursor, int iInc, int oInc) {
        objFlds[objLimit + OBJ_RECORD] = cursor.getRecord();
        intFlds[intLimit + INT_CIPHER] = cursor.getCipher();
        objFlds[objLimit + OBJ_SYMBOL] = cursor.getSymbol();
        intLimit += iInc;
        objLimit += oInc;
        size++;
    }

    private void initWriteCursorInternal(DataRecord record, int cipher, String symbol) {
        writeCursor.setRecordInternal(record, mode);
        writeCursor.setSymbolInternal(cipher, symbol);
        writeCursor.setOffsetsInternal(intLimit + mode.intBufOffset, objLimit + mode.objBufOffset);
    }

    private void growIfNeeded(int iInc, int oInc) {
        if (intLimit > intFlds.length - iInc)
            growInts(iInc);
        if (objLimit > objFlds.length - oInc)
            growObjs(oInc);
    }

    private void growInts(int iInc) {
        // Move rarely used large code into separate method for better code inlining.
        if (intLimit + iInc < 0)
            throw new ArrayIndexOutOfBoundsException(intLimit + iInc);
        intFlds = ArrayUtil.grow(intFlds, intLimit + iInc);
        if (capacityLimit == POOLED_CAPACITY && intFlds.length > MAX_POOLED_INTS) {
            // oops.. capacity limited buffer grew over its pooled size... maybe record has too many fields?
            maxIntFields = Math.max(maxIntFields, iInc);
        }
        reinitCursorArraysInternal();
    }

    private void growObjs(int oInc) {
        // Move rarely used large code into separate method for better code inlining.
        if (objLimit + oInc < 0)
            throw new ArrayIndexOutOfBoundsException(objLimit + oInc);
        objFlds = ArrayUtil.grow(objFlds, objLimit + oInc);
        if (capacityLimit == POOLED_CAPACITY && objFlds.length > MAX_POOLED_OBJS) {
            // oops.. capacity limited buffer grew over its pooled size... maybe record has too many fields?
            maxObjFields = Math.max(maxObjFields, oInc);
        }
        reinitCursorArraysInternal();
    }

    private void growOrCompactIfNeeded(int iInc, int oInc) {
        if (intLimit > intFlds.length - iInc || objLimit > objFlds.length - oInc) {
            if (intPosition > intFlds.length / 2 || objPosition > objFlds.length / 2)
                compactArrays();
            growIfNeeded(iInc, oInc);
        }
    }

    private void compactArrays() {
        int intLength = intLimit - intPosition;
        int objLength = objLimit - objPosition;
        System.arraycopy(intFlds, intPosition, intFlds, 0, intLength);
        System.arraycopy(objFlds, objPosition, objFlds, 0, objLength);
        Arrays.fill(intFlds, intLength, intLimit, 0);
        Arrays.fill(objFlds, objLength, objLimit, null);
        intPosition = 0;
        objPosition = 0;
        intLimit = intLength;
        objLimit = objLength;
    }

    private void resetCursorsAccess() {
        readCursor.resetAccessInternal();
        writeCursor.resetAccessInternal();
    }
}
