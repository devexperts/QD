/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2020 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.webservice.comet;

import com.devexperts.io.ByteArrayOutput;
import com.dxfeed.event.market.Quote;
import com.dxfeed.webservice.EventSymbolMap;
import com.dxfeed.webservice.rest.Format;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test DataMessage serialization (which does not use JAXB default serialization).
 */
public class DataMessageTest {
    @Test
    public void testSchemeWriter() throws IOException {
        DataMessage message = new DataMessage(true, Quote.class, Collections.emptyList(), new EventSymbolMap());

        ByteArrayOutput bao = new ByteArrayOutput();
        Format.JSON.writeTo(message, bao, null);
        String jsonResult = new String(bao.getBuffer());

        assertTrue("Should start from \"eventSymbol\" field", jsonResult.contains("[\"eventSymbol"));
        assertFalse("Should show renamed field \"bidSize\"", jsonResult.contains("bidSizeAsDouble"));
    }
}
