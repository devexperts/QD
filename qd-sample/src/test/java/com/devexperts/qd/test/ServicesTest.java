/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.test;

import com.devexperts.qd.DataScheme;
import com.devexperts.services.Services;
import junit.framework.TestCase;

public class ServicesTest extends TestCase {
    public void testCustomerScheme() {
        DataScheme scheme = Services.createService(DataScheme.class, null, TestDataScheme.class.getName());
        assertTrue(scheme instanceof TestDataScheme);
    }
}
