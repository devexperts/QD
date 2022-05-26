/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.ipf.test;

import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;
import com.dxfeed.ipf.InstrumentProfileWriter;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit test for {@link InstrumentProfileReader} and {@link com.dxfeed.ipf.InstrumentProfileWriter} classes.
 */
public class InstrumentProfileReaderWriterTest {

    @Test
    public void testReadWrite() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        InstrumentProfile profile = new InstrumentProfile();
        profile.setType("TEST");
        profile.setSymbol("A");

        InstrumentProfileWriter writer = new InstrumentProfileWriter();
        writer.write(out, Collections.singletonList(profile));
        String ipf = out.toString();
        assertTrue(ipf.contains("##COMPLETE"));

        InstrumentProfileReader reader = new InstrumentProfileReader();
        List<InstrumentProfile> profiles = reader.read(new ByteArrayInputStream(ipf.getBytes()), "test");

        assertNotNull(profiles);
        assertEquals(1, profiles.size());
        assertTrue(reader.wasComplete());
    }

    @Test
    public void testReadWithoutComplete() throws IOException {
        // Warning for now - will fail in the future
        String s = "#TEST::=TYPE,SYMBOL\r\nTEST,AAA\r\n";
        InstrumentProfileReader reader = new InstrumentProfileReader();
        reader.read(new ByteArrayInputStream(s.getBytes()), "test");
        assertFalse(reader.wasComplete());
    }
}
