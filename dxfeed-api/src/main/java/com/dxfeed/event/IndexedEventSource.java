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
package com.dxfeed.event;

/**
 * Source identifier for {@link IndexedEvent}.
 * See {@link IndexedEvent#getSource() IndexedEvent.getSource}.
 */
public class IndexedEventSource {
    /**
     * The default source with zero {@link #id() identifier}
     * for all events that do not support multiple sources.
     */
    public static final IndexedEventSource DEFAULT = new IndexedEventSource(0, "DEFAULT");

    protected final int id;
    protected final String name;


    public IndexedEventSource(int id, String name) {
        this.id = id;
        this.name = name;
    }

    /**
     * Returns a source identifier. Source identifier is non-negative.
     * @return a source identifier.
     */
    public final int id() {
        return id;
    }

    /**
     * Returns a string representation of the object.
     * @return a string representation of the object.
     */
    public final String name() {
        return name;
    }

    /**
     * Returns a string representation of the object.
     * @return a string representation of the object.
     */
    @Override
    public final String toString() {
        return name;
    }

    /**
     * Indicates whether some other indexed event source has the same id.
     * @return {@code true} if this object is the same id as the obj argument; {@code false} otherwise.
     */
    @Override
    public final boolean equals(Object o) {
        return o == this || o instanceof IndexedEventSource && id() == ((IndexedEventSource) o).id();
    }

    /**
     * Returns a hash code value for this object.
     * The result of this method is equal to {@link #id() id}.
     * @return a hash code value for this object.
     */
    @Override
    public final int hashCode() {
        return id();
    }
}
