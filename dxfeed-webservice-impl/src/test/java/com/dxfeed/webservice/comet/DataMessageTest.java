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
package com.dxfeed.webservice.comet;

import com.devexperts.io.ByteArrayOutput;
import com.dxfeed.event.EventType;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.TimeAndSale;
import com.dxfeed.event.market.Trade;
import com.dxfeed.webservice.EventSymbolMap;
import com.dxfeed.webservice.rest.Format;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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

    @Test
    public void testJson() throws IOException {
        //FIXME Serialization performance problem: too many serializers in a singleton static Format.JSON (see QD-1388)
        ByteArrayOutput bao = new ByteArrayOutput();
        Format.JSON.writeTo(create(Quote.class, new Quote()), bao, null);
        Format.JSON.writeTo(create(Order.class, new Order()), bao, null);
        Format.JSON.writeTo(create(TimeAndSale.class, new TimeAndSale()), bao, null);
        Format.JSON.writeTo(create(Trade.class, new Trade()), bao, null);
        Format.JSON.writeTo(create(Profile.class, new Profile()), bao, null);

        String jsonResult = new String(bao.getBuffer(), 0, bao.getPosition());
        assertNotNull(jsonResult);
    }

    private <T extends EventType<String>> DataMessage create(Class<T> klass, T event) {
        event.setEventSymbol("IBM");
        return new DataMessage(true, klass, Collections.singletonList(event), new EventSymbolMap());
    }
}
