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
package com.devexperts.test.isolated;

import com.devexperts.util.WideDecimal;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(IsolatedRunner.class)
@Isolated({"com.devexperts.util.WideDecimal"})
public class IsolatedTest {

    @Test
    public void testIsolatedTest() {
        // Since IsolatedClassLoader class loads this test we cannot use instanceof directly
        assertTrue(getClass().getClassLoader().getClass().getName().endsWith("IsolatedClassLoader"));
    }

    @Test
    public void testNotIsolatedClass() {
        assertNull(Number.class.getClassLoader());
    }

    @Test
    public void testIsolatedClass() {
        // Since IsolatedClassLoader class loads this test we cannot use instanceof directly
        assertTrue(WideDecimal.class.getClassLoader().getClass().getName().endsWith("IsolatedClassLoader"));
    }
}
