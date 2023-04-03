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

import com.devexperts.util.TypedKey;
import com.devexperts.util.TypedMap;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TypedMapTest {

    private static final TypedKey<Boolean> KEY1 = new TypedKey<>("toBe");
    private static final TypedKey<Boolean> KEY2 = new TypedKey<>("notToBe");

    private static final TypedKey<Boolean> TRUE_KEY = new TypedKey<>("key");
    private static final TypedKey<Boolean> FALSE_KEY = new TypedKey<>("key");

    private static final TypedKey<Boolean> UNNAMED_KEY = new TypedKey<>();

    @Test
    public void testNamedKeysToString() {
        TypedMap map = new TypedMap();
        map.set(KEY1, true);
        map.set(KEY2, false);

        assertTrue(map.toString().contains("toBe=true"));
        assertTrue(map.toString().contains("notToBe=false"));
    }

    @Test
    public void testSameNamedKeysString() {
        TypedMap map = new TypedMap();
        map.set(TRUE_KEY, true);
        map.set(FALSE_KEY, false);

        assertTrue(map.get(TRUE_KEY));
        assertFalse(map.get(FALSE_KEY));
        assertTrue(map.toString().contains("key=true"));
        assertTrue(map.toString().contains("key=false"));
    }

    @Test
    public void testUnnamedKeysString() {
        TypedMap map = new TypedMap();
        map.set(UNNAMED_KEY, true);
        assertTrue(map.toString().matches("\\{" + getClass().getName() + ".*=true\\}"));
    }
}
