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
 * An implementation of {@link IndexerFunction} that distinguishes and identifies elements using identity comparison of object keys.
 *
 * <p>It uses {@link System#identityHashCode(Object) System.identityHashCode(Object)} method instead of
 * {@link Object#hashCode() Object.hashCode()} method to compute hashcode and reference comparison
 * instead of {@link Object#equals(Object) Object.equals(Object)} method to determine identity.
 *
 * <p>The <b>IdentityIndexer</b> is {@link Serializable}, so that all concrete subclasses
 * shall be <b>serializable</b> in order to support serialization of indexed set and map.
 *
 * @deprecated Use {@link IndexerFunction.IdentityKey}
 */
@Deprecated
public abstract class IdentityIndexer<K, V> extends Indexer<K,V> implements IndexerFunction.IdentityKey<K, V> {
    private static final long serialVersionUID = 0L;

    /**
     * Sole constructor; for invocation by subclass constructors, typically implicit.
     *
     * <p>This implementation does nothing.
     */
    protected IdentityIndexer() {}
}
