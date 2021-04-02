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
package com.devexperts.util;

import java.io.Serializable;

/**
 * A subclass of {@link Indexer} that distinguishes and identifies elements using number keys.
 *
 * <p>It assumes that elements are fully identifiable by numeric ID and treats object keys as a mere wrappers.
 * The hash function is computed using <b>(int) (key&nbsp;^&nbsp;(key&nbsp;&gt;&gt;&gt;&nbsp;32))</b> formula.
 *
 * <p>The <b>NumberKeyIndexer</b> is {@link Serializable}, so that all concrete subclasses
 * shall be <b>serializable</b> in order to support serialization of indexed set and map..
 *
 * @deprecated Use functional interfaced {@link IndexerFunction.LongKey} or {@link IndexerFunction.IntKey} instead.
 */
@Deprecated
public abstract class NumberKeyIndexer<V> extends Indexer<Long, V> implements IndexerFunction.LongKey<V> {
    private static final long serialVersionUID = 0L;

    /**
     * Sole constructor; for invocation by subclass constructors, typically implicit.
     *
     * <p>This implementation does nothing.
     */
    protected NumberKeyIndexer() {}

    /**
     * Returns hash code for specified value; called when performing value-based operations, including <b>rehash</b>.
     *
     * <p>This implementation computes hash function of
     * {@code hashCodeByKey(getNumberKey(value))} expression.
     */
    @Override
    public int hashCodeByValue(V value) {
        return hashCodeByKey(getNumberKey(value));
    }

    /**
     * Determines if specified new value matches specified old value; called when performing value-based operations.
     *
     * <p>This implementation delegates to <code>({@link #getNumberKey(Object) getNumberKey}(newValue)&nbsp;==&nbsp;{@link #getNumberKey(Object) getNumberKey}(oldValue))</code> expression.
     */
    @Override
    public boolean matchesByValue(V newValue, V oldValue) {
        return getNumberKey(newValue) == getNumberKey(oldValue);
    }

    /**
     * Returns object key for specified value to be used for hashing and identification;
     * called when explicit object key is needed or when other methods delegate operations as specified.
     *
     * <p>This implementation delegates to <code>Long.{@link Long#valueOf(long) valueOf}({@link #getNumberKey(Object) getNumberKey}(value))</code> expression.
     */
    @Override
    public Long getObjectKey(V value) {
        return getNumberKey(value);
    }

    /**
     * Returns hash code for specified object key; called when performing operations using object keys.
     *
     * <p>This implementation computes hash function of
     * {@code hashCodeByKey(key.intValue())} expression.
     */
    @Override
    public int hashCodeByKey(Long key) {
        return hashCodeByKey(key.longValue());
    }

    /**
     * Determines if specified object key matches specified value; called when performing operations using object keys.
     *
     * <p>This implementation delegates to <code>(key.{@link Long#longValue() longValue}()&nbsp;==&nbsp;{@link #getNumberKey(Object) getNumberKey}(value))</code> expression.
     */
    @Override
    public boolean matchesByKey(Long key, V value) {
        return key == getNumberKey(value);
    }

    /**
     * Returns number key for specified value to be used for hashing and identification;
     * called when explicit number key is needed or when other methods delegate operations as specified.
     */
    @Override
    public abstract long getNumberKey(V value);
}
