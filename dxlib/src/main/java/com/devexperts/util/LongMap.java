/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.util;

import java.util.Map;

/**
 * This class extends {@link Map} with methods that are specific
 * for <code>long</code> values.
 */
public interface LongMap<V> extends Map<Long,V> {
	/**
	 * Returns <tt>true</tt> if this map contains a mapping for the specified
	 * key.
	 * @see #containsKey(Object)
	 */
	public boolean containsKey(long key);

	/**
	 * Returns the value to which this map maps the specified key.  Returns
	 * <tt>null</tt> if the map contains no mapping for this key.
	 * @see #get(Object)
	 */
	public V get(long key);

	/**
	 * Associates the specified value with the specified key in this map.
	 * If the map previously contained a mapping for this key,
	 * the old value is replaced.
	 * @see #put(Object, Object)
	 */
	public V put(long key, V value);

	/**
	 * Removes the mapping for this key from this map if present.
	 * @see #remove(Object)
	 */
	public V remove(long key);

	/**
	 * Returns a set view of the keys contained in this map.
	 * @see #keySet()
	 */
	public LongSet longKeySet();

	/**
	 * A map entry (key-value pair).
	 * @see java.util.Map.Entry
	 */
	public interface Entry<V> extends Map.Entry<Long,V> {
		/**
		 * Returns the key corresponding to this entry.
		 * @see java.util.Map.Entry#getKey()
		 */
		public long getLongKey();
	}
}
