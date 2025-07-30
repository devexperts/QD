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
package com.devexperts.rmi.impl;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

public class PropertiesUtilTest {

    @Test
    public void testGetImmutableMapWithNull() {
        Map<String, String> result = PropertiesUtil.getImmutableMap(null);
        assertSame("Collections.emptyMap expected", Collections.emptyMap(), result);
    }

    @Test
    public void testGetImmutableMapWithEmptyMap() {
        Map<String, String> emptyMap = new HashMap<>();
        Map<String, String> result = PropertiesUtil.getImmutableMap(emptyMap);
        assertSame("Collections.emptyMap expected", Collections.emptyMap(), result);
    }

    @Test
    public void testGetImmutableMapWithSingleEntry() {
        Map<String, String> originalMap = new HashMap<>();
        originalMap.put("key", "value");
        
        Map<String, String> result = PropertiesUtil.getImmutableMap(originalMap);

        assertEquals("Original values expected", originalMap, result);
        assertThrows(UnsupportedOperationException.class, () -> result.put("anotherKey", "anotherValue"));
    }

    @Test
    public void testGetImmutableMapWithMultipleEntries() {
        Map<String, String> originalMap = new HashMap<>();
        originalMap.put("key1", "value1");
        originalMap.put("key2", "value2");
        originalMap.put("key3", "value3");
        
        Map<String, String> result = PropertiesUtil.getImmutableMap(originalMap);
        assertEquals("Original values expected", originalMap, result);
        assertThrows(UnsupportedOperationException.class, () -> result.put("anotherKey", "anotherValue"));
    }

    @Test
    public void testGetImmutableMapWithAlreadyImmutableMap() {
        // Create an immutable map using Collections.emptyMap()
        Map<String, String> immutableEmptyMap = Collections.emptyMap();
        Map<String, String> result1 = PropertiesUtil.getImmutableMap(immutableEmptyMap);
        assertSame("Should return the same instance", immutableEmptyMap, result1);
        
        // Create an immutable map using Collections.singletonMap()
        Map<String, String> immutableSingletonMap = Collections.singletonMap("key", "value");
        Map<String, String> result2 = PropertiesUtil.getImmutableMap(immutableSingletonMap);
        assertSame("Should return the same instance", immutableSingletonMap, result2);
        
        // Create an immutable map using Collections.unmodifiableMap()
        Map<String, String> originalMap = new HashMap<>();
        originalMap.put("key1", "value1");
        originalMap.put("key2", "value2");
        Map<String, String> unmodifiableMap = Collections.unmodifiableMap(originalMap);
        
        // Note: This cannot return the same instance because the unmodifiableMap still formally
        // mutable through a wrapped mutable map
        Map<String, String> result3 = PropertiesUtil.getImmutableMap(unmodifiableMap);
        assertNotSame("Should return a different instance", unmodifiableMap, result3);
        assertThrows(UnsupportedOperationException.class, () -> result3.put("anotherKey", "anotherValue"));
    }

    @Test
    public void testGetImmutableMapRetainsOrder() {
        // Using LinkedHashMap to ensure insertion order is preserved
        Map<String, String> orderedMap = new LinkedHashMap<>();
        orderedMap.put("k5", "v5");
        orderedMap.put("k1", "v1");
        orderedMap.put("k3", "v3");
        
        Map<String, String> result = PropertiesUtil.getImmutableMap(orderedMap);
        assertEquals("Original values expected", orderedMap, result);

        // Check if order is preserved
        Iterator<Map.Entry<String, String>> resultIt = result.entrySet().iterator();
        Iterator<Map.Entry<String, String>> originIt = orderedMap.entrySet().iterator();
        for (int i = 0; i < orderedMap.size(); i++) {
            assertEquals("Order should be preserved", originIt.next(), resultIt.next());
        }
    }
}
