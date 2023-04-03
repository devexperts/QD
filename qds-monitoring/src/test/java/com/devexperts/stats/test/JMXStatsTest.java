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
package com.devexperts.stats.test;

import com.devexperts.qd.stats.JMXStats;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class JMXStatsTest {
    // [QD-353] JMXStat.createRoot hangs on attempt to create root with duplicate name
    @Test
    public void testDupName() {
        JMXStats.RootRegistration root0 = JMXStats.createRoot("dupNameTest", null);
        JMXStats.RootRegistration root1 = JMXStats.createRoot("dupNameTest", null);
        assertTrue(!root0.getRootStats().getFullKeyProperties().equals(root1.getRootStats().getFullKeyProperties()));
        root0.unregister();
        root1.unregister();
    }
}
