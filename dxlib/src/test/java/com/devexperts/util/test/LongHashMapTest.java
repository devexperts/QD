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
package com.devexperts.util.test;

import com.devexperts.util.LongHashMap;
import junit.framework.TestCase;
import org.junit.Assert;

public class LongHashMapTest extends TestCase {

    public void testGetOrDefaultIntegerKey() {
        LongHashMap<Object> map = new LongHashMap<>();
        Object expected = new Object();
        Object def = new Object();
        int key = 42;
        map.put(key, expected);
        Object value = map.getOrDefault(key, def);
        Assert.assertEquals(expected, value);
    }

    public void testRemoveIntegerKey() {
        LongHashMap<Object> map = new LongHashMap<>();
        Object expected = new Object();
        int key = 42;
        map.put(key, expected);
        Object removed = map.remove(key);
        Assert.assertEquals(expected, removed);
    }

    public void testForEach() {
        LongHashMap<Object> map = new LongHashMap<>();
        Object first = new Object();
        map.put(1L, first);
        Object second = new Object();
        map.put(2L, second);
        map.forEach((key, value) -> {
            if (first.equals(value)) {
                Assert.assertEquals(1L, (long) key);
            } else if (second.equals(value)) {
                Assert.assertEquals(2L, (long) key);
            } else {
                Assert.fail("Valid key-value pair not found: key = '" + key + "', value = '" + value + "'");
            }
        });
    }

    public void testComputeIfAbsent() {
        LongHashMap<Object> map = new LongHashMap<>();
        Object first = new Object();
        map.put(1L, first);
        Object def = new Object();
        Object computed = map.computeIfAbsent(1L, aLong -> def);
        Assert.assertEquals(first, computed);
        computed = map.computeIfAbsent(2L, aLong -> def);
        Assert.assertEquals(def, computed);
    }

    public void testComputeIfAbsentIntegerKey() {
        LongHashMap<Object> map = new LongHashMap<>();
        Object first = new Object();
        int firstKey = 1;
        map.put(firstKey, first);
        Object def = new Object();
        Object computed = map.computeIfAbsent(firstKey, aLong -> def);
        Assert.assertEquals(first, computed);
        computed = map.computeIfAbsent(2L, aLong -> def);
        Assert.assertEquals(def, computed);
    }

    public void testPutIfAbsent() {
        LongHashMap<Object> map = new LongHashMap<>();
        Object first = new Object();
        map.put(1L, first);
        Object def = new Object();
        Object prevValue = map.putIfAbsent(1L, def);
        Assert.assertEquals(first, prevValue);
        prevValue = map.putIfAbsent(2L, def);
        Assert.assertNull(prevValue);
        Assert.assertEquals(def, map.get(2L));
    }

    public void testPutIfAbsentIntegerKey() {
        LongHashMap<Object> map = new LongHashMap<>();
        int key = 42;
        Object first = new Object();
        map.put(key, first);
        Object def = new Object();
        Object prevValue = map.putIfAbsent(key, def);
        Assert.assertEquals(first, prevValue);
    }

    public void testComputeIfPresentIntegerKey() {
        LongHashMap<Object> map = new LongHashMap<>();
        Object first = new Object();
        int key = 42;
        map.put(key, first);
        Object def = new Object();
        Object computed = map.computeIfPresent(key, (aLong, o) -> def);
        Assert.assertEquals(def, computed);
        key = 43;
        computed = map.computeIfPresent(key, (aLong, o) -> def);
        Assert.assertNull(computed);
    }
}
