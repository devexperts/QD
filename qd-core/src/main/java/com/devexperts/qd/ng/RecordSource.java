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
package com.devexperts.qd.ng;

import com.devexperts.qd.DataIterator;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.SubscriptionIterator;

/**
 * Read-only sequence of records that is available via
 * {@link RecordCursor} using {@link #next} method. It supports navigation
 * to arbitrary records by their position via {@link #getPosition getPosition}, {@link #setPosition(long) setPosition}, abd
 * {@link #cursorAt} methods and, thus, can be read multiple times.
 * It can be sliced with {@link #newSource(long, long) newSource} method.
 *
 * <p>This abstract class is replacing legacy interfaces {@link DataIterator} and {@link SubscriptionIterator}.
 */
public abstract class RecordSource implements DataIterator, SubscriptionIterator {
    /**
     * An empty record source.
     */
    public static final RecordSource VOID = new RecordBuffer(RecordMode.DATA, null, 0, 0, null, 0, 0);

    // can be extended only in this package
    RecordSource() {}

    /**
     * Returns mode of this record source. This is the {@link RecordCursor} {@link RecordCursor#getMode() mode}
     * for the results of {@link #next()}, {@link #cursorAt(long)}, and {@link #current()} methods.
     * @return mode of this record source.
     */
    public abstract RecordMode getMode();

    /**
     * Retrieves all records from {@link #getPosition() position} to {@link #getLimit() limit}
     * into the given sink and advances position while the sink {@link RecordSink#hasCapacity() has capacity}.
     *
     * @param sink the sink.
     * @return {@code true} if all data was retrieved,
     *         {@code false} if sink ran out of capacity and more records remain to retrieve.
     */
    public abstract boolean retrieve(RecordSink sink);

    /**
     * Returns read cursor at the current position and advances position to next record.
     * The result of this method is {@code null} when
     * {@link #getPosition() position} is equal to {@link #getLimit() limit}.
     *
     * <p>The mode of the resulting cursor is the same as returned by {@link #getMode()}.
     * @return read cursor at the current position.
     */
    public abstract RecordCursor next();

    /**
     * Returns read cursor at a specified position. This position should have been previously
     * returned by {@link #getPosition()} or {@link #getLimit()} method.
     * Invoking this method at the current {@link #getPosition() position} results in the same cursor as
     * the call {@link #current()} or call to {@link #next()} without the side effect of
     * advancing position.
     * The result of this method at the current {@link #getLimit() limit} is {@code null}.
     *
     * <p>The mode of the resulting cursor is the same as returned by {@link #getMode()}.
     * @return read cursor at the specified position.
     * @throws IndexOutOfBoundsException if position is invalid or above current limit.
     */
    public abstract RecordCursor cursorAt(long position);

    /**
     * Returns read cursor at the current position. Effect is same as
     * <code>{@link #cursorAt(long) cursorAt}({@link #getPosition() getPosition}())</code>, but faster.
     * It is the same as call to {@link #next()} without the side effect of
     * advancing position.
     *
     * <p>The mode of the resulting cursor is the same as returned by {@link #getMode()}.
     * @return read cursor at the current position.
     */
    public abstract RecordCursor current();

    /**
     * Returns position of the current record.
     * This record will be read by {@link #next} method.
     */
    public abstract long getPosition();

    /**
     * Changes position of the current record.
     * Only values that were previously returned by {@link #getPosition()}, {@link #getLimit()}
     * or <code>{@link #next next}().{@link RecordCursor#getPosition getPosition}()</code>
     * methods are allowed here.
     * @param position new position.
     * @throws IndexOutOfBoundsException if position is invalid or above current limit.
     */
    public abstract void setPosition(long position);

    /**
     * Returns limit of this source,
     * which is the position of after the last record returned by {@link #next()}.
     */
    public abstract long getLimit();

    /**
     * Returns new {@link RecordSource} that reads this record source from
     * its current {@link #getPosition position} to its {@link #getLimit limit}.
     * It is equivalent to <code>newSource(getPosition(), getLimit())</code>.
     * @see #newSource(long, long)
     */
    public abstract RecordSource newSource();

    /**
     * Returns new {@link RecordSource} that reads this record source from
     * the specified start position to the specified end position.
     * @see #newSource()
     */
    public abstract RecordSource newSource(long start, long end);

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #next()}.
     */
    public abstract int getCipher();

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #next()}.
     */
    public abstract String getSymbol();

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #next()}.
     */
    public abstract DataRecord nextRecord();

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #next()}.
     */
    public abstract int nextIntField();

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #next()}.
     */
    public abstract Object nextObjField();

    /**
     * {@inheritDoc}
     * @deprecated Use {@link #next()}.
     */
    public abstract long getTime();
}
