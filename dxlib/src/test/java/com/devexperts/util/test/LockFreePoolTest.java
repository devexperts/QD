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

import com.devexperts.util.LockFreePool;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LockFreePoolTest {

    @Test
    public void testMaxCapacitySizeReporting() {
        int n = 1000000;
        LockFreePool<Object> pool = new LockFreePool<Object>(n);
        assertEquals(0, pool.size());
        for (int i = 1; i <= n; i++) {
            assertTrue(pool.offer(new Object()));
            assertEquals(i, pool.size());
        }
        assertFalse(pool.offer(new Object()));
        assertEquals(n, pool.size());
        for (int i = n; --i >= 0;) {
            assertNotNull(pool.poll());
            assertEquals(i, pool.size());
        }
    }
}
