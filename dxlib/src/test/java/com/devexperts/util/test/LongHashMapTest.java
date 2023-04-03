/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2023 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.util.test;

import com.devexperts.util.LongHashMap;
import com.devexperts.util.LongMap;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongFunction;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

public class LongHashMapTest {

    private static final Object NO_VALUE = new Object() {
        @Override
        public String toString() {
            return "NO_VALUE";
        }
    };
    private static final String DEFAULT_INIT = "INIT"; // default initial value for initialized bucket
    public Map<Long, String> map;
    public LongMap<String> longMap;

    @Before
    public void setUp() throws Exception {
        map = new HashMap<>();
        longMap = new LongHashMap<>();
    }

    @Test
    public void testGetOrDefault() {
        standardChecks("getOrDefault", k -> map.getOrDefault(k, "B"), k -> longMap.getOrDefault(k, "B"));
        standardChecks("getOrDefault", k -> map.getOrDefault(k, "B"), k -> longMap.getOrDefault(Long.valueOf(k), "B"));
    }

    @Test
    public void testRemoveByKey() {
        standardChecks("remove", k -> map.remove(k), k -> longMap.remove(k));
        standardChecks("remove", k -> map.remove(k), k -> longMap.remove(Long.valueOf(k)));
    }

    @Test
    public void testComputeIfAbsent() {
        standardChecks("computeIfAbsent",
            k -> map.computeIfAbsent(k, lk -> "B"),
            k -> longMap.computeIfAbsent(k, lk -> "B"));
        standardChecks("computeIfAbsent",
            k -> map.computeIfAbsent(k, lk -> "B"),
            k -> longMap.computeIfAbsent(Long.valueOf(k), lk -> "B"));
        standardChecks("computeIfAbsent",
            k -> map.computeIfAbsent(k, lk -> null),
            k -> longMap.computeIfAbsent(k, lk -> null));
        standardChecks("computeIfAbsent",
            k -> map.computeIfAbsent(k, lk -> null),
            k -> longMap.computeIfAbsent(Long.valueOf(k), lk -> null));
    }

    @Test
    public void testPutIfAbsent() {
        standardChecks("putIfAbsent", k -> map.putIfAbsent(k, "B"), k -> longMap.putIfAbsent(k, "B"));
        standardChecks("putIfAbsent", k -> map.putIfAbsent(k, "B"), k -> longMap.putIfAbsent(Long.valueOf(k), "B"));
    }

    @Test
    public void testReplace() {
        standardChecks("replace", k -> map.replace(k, "B"), k -> longMap.replace(k, "B"));
        standardChecks("replace", k -> map.replace(k, "B"), k -> longMap.replace(Long.valueOf(k), "B"));
    }

    @Test
    public void testReplaceWithCheck() {
        // check matching
        checkMethod("replace", 1, "A", k -> map.replace(k, "A", "B"), k -> longMap.replace(k, "A", "B"));
        checkMethod("replace", 1, "A", k -> map.replace(k, "A", "B"), k -> longMap.replace(Long.valueOf(k), "A", "B"));
        checkMethod("replace", 2, null, k -> map.replace(k, null, "B"), k -> longMap.replace(k, null, "B"));
        checkMethod("replace", 2, null, k -> map.replace(k, null, "B"),
            k -> longMap.replace(Long.valueOf(k), null, "B"));

        // check no match
        assertNotEquals(DEFAULT_INIT, "A");
        standardChecks("replace", k -> map.replace(k, "A", "B"), k -> longMap.replace(k, "A", "B"));
        standardChecks("replace", k -> map.replace(k, "A", "B"), k -> longMap.replace(Long.valueOf(k), "A", "B"));
        standardChecksNoNull("replace", k -> map.replace(k, null, "B"), k -> longMap.replace(k, null, "B"));
        standardChecksNoNull("replace", k -> map.replace(k, null, "B"),
            k -> longMap.replace(Long.valueOf(k), null, "B"));
    }

    @Test
    public void testRemoveWithCheck() {
        // check matching
        checkMethod("remove", 1, "A", k -> map.remove(k, "A"), k -> longMap.remove(k, "A"));
        checkMethod("remove", 1, "A", k -> map.remove(k, "A"), k -> longMap.remove(Long.valueOf(k), "A"));
        checkMethod("remove", 2, null, k -> map.remove(k, null), k -> longMap.remove(k, null));
        checkMethod("remove", 2, null, k -> map.remove(k, null), k -> longMap.remove(Long.valueOf(k), null));

        // check no match
        assertNotEquals(DEFAULT_INIT, "A");
        standardChecks("remove", k -> map.remove(k, "A"), k -> longMap.remove(k, "A"));
        standardChecks("remove", k -> map.remove(k, "A"), k -> longMap.remove(Long.valueOf(k), "A"));
        standardChecksNoNull("remove", k -> map.remove(k, null), k -> longMap.remove(k, null));
        standardChecksNoNull("remove", k -> map.remove(k, null), k -> longMap.remove(Long.valueOf(k), null));
    }

    @Test
    public void testCompute() {
        standardChecks("compute", k -> map.compute(k, (lk, o) -> "B"), k -> longMap.compute(k, (lk, o) -> "B"));
        standardChecks("compute", k -> map.compute(k, (lk, o) -> "B"),
            k -> longMap.compute(Long.valueOf(k), (lk, o) -> "B"));
        standardChecks("compute", k -> map.compute(k, (lk, o) -> null), k -> longMap.compute(k, (lk, o) -> null));
        standardChecks("compute", k -> map.compute(k, (lk, o) -> null),
            k -> longMap.compute(Long.valueOf(k), (lk, o) -> null));
    }

    @Test
    public void testMerge() {
        standardChecks("compute", k -> map.merge(k, "B", String::concat), k -> longMap.merge(k, "B", String::concat));
        standardChecks("compute", k -> map.merge(k, "B", String::concat),
            k -> longMap.merge(Long.valueOf(k), "B", String::concat));
        standardChecks("compute", k -> map.merge(k, "B", (a, b) -> null), k -> longMap.merge(k, "B", (a, b) -> null));
        standardChecks("compute", k -> map.merge(k, "B", (a, b) -> null),
            k -> longMap.merge(Long.valueOf(k), "B", (a, b) -> null));
    }

    @Test
    public void testComputeIfPresent() {
        standardChecks("computeIfPresent",
            k -> map.computeIfPresent(k, (lk, o) -> "B"),
            k -> longMap.computeIfPresent(k, (lk, o) -> "B"));
        standardChecks("computeIfPresent",
            k -> map.computeIfPresent(k, (lk, o) -> "B"),
            k -> longMap.computeIfPresent(Long.valueOf(k), (lk, o) -> "B"));
        standardChecks("computeIfPresent",
            k -> map.computeIfPresent(k, (lk, o) -> null),
            k -> longMap.computeIfPresent(k, (lk, o) -> null));
        standardChecks("computeIfPresent",
            k -> map.computeIfPresent(k, (lk, o) -> null),
            k -> longMap.computeIfPresent(Long.valueOf(k), (lk, o) -> null));
    }

    @Test
    public void testForEach() {
        LongHashMap<Object> map = new LongHashMap<>();
        Object first = new Object();
        map.put(1L, first);
        Object second = new Object();
        map.put(2L, second);
        map.forEach((key, value) -> {
            if (first.equals(value)) {
                assertEquals(1L, (long) key);
            } else if (second.equals(value)) {
                assertEquals(2L, (long) key);
            } else {
                fail("Valid key-value pair not found: key = '" + key + "', value = '" + value + "'");
            }
        });
    }

    private void standardChecks(String op, Function<Long, Object> mapOp, LongFunction<Object> longMapOp) {
        standardChecks(op, mapOp, longMapOp, true);
    }

    private void standardChecksNoNull(String op, Function<Long, Object> mapOp, LongFunction<Object> longMapOp) {
        standardChecks(op, mapOp, longMapOp, false);
    }

    private void standardChecks(String op, Function<Long, Object> mapOp, LongFunction<Object> longMapOp,
        boolean checkNull)
    {
        checkMethod(op, 1, DEFAULT_INIT, mapOp, longMapOp);
        if (!checkNull)
            checkMethod(op, 2, null, mapOp, longMapOp);
        checkMethod(op, 3, NO_VALUE, mapOp, longMapOp);
    }

    private void checkMethod(String op, long key, Object init,
        Function<Long, Object> mapOp, LongFunction<Object> longMapOp)
    {
        // ensure that
        if (init == NO_VALUE) {
            map.remove(key);
            longMap.remove(key);
        } else {
            map.put(key, (String) init);
            longMap.put(key, (String) init);
        }

        String desc = op + "[key=" + key + ", init=" + init + "]: ";
        assertEquals(desc + "setup", map.containsKey(key), longMap.containsKey(key));
        assertEquals(desc + "setup", map.get(key), longMap.get(key));
        Object mapResult = mapOp.apply(key);
        Object longMapResult = longMapOp.apply(key);
        assertEquals(desc + "result", mapResult, longMapResult);
        assertEquals(desc + "containsKey", map.containsKey(key), longMap.containsKey(key));
        assertEquals(desc + "get", map.get(key), longMap.get(key));
    }
}
