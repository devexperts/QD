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
package com.dxfeed.ipf.filter.test;

import java.io.File;
import java.io.IOException;

import com.devexperts.qd.QDFactory;
import com.devexperts.test.ThreadCleanCheck;
import com.devexperts.test.TraceRunner;
import com.dxfeed.ipf.filter.IPFSymbolFilter;
import org.junit.*;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(TraceRunner.class)
public class IPFFilterStringTest {
    private static final File FILE = new File("IPFFilterStringTest.ipf");

    @Before
    public void setUp() {
        ThreadCleanCheck.before();
    }

    @After
    public void tearDown() throws Exception {
        assertTrue(FILE.delete());
        ThreadCleanCheck.after();
    }

    @Test
    public void testToString() throws IOException {
        IPFWriter writer = new IPFWriter(FILE);
        writer.writeIPFFile("TEST");
        assertEquals("ipf[" + FILE + "]", IPFSymbolFilter.create(QDFactory.getDefaultScheme(), "ipf[" + FILE + "]").toString());
        assertEquals("ipf[" + FILE + ",update=PT1S]", IPFSymbolFilter.create(QDFactory.getDefaultScheme(), "ipf[" + FILE + ",update=1s]").toString());
        // Note: user and password shall not show in string
        assertEquals("ipf[" + FILE + "]", IPFSymbolFilter.create(QDFactory.getDefaultScheme(), "ipf[" + FILE + ",user=secret,password=secret]").toString());
    }
}
