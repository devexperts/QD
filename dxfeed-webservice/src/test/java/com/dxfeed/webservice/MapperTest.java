/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.webservice;

import java.io.IOException;
import java.util.Arrays;

import com.dxfeed.event.market.Quote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;

@RunWith(value = Parameterized.class)
public class MapperTest {
    @Parameterized.Parameters(name="{0}")
    public static Iterable<Object[]> parameters() {
        return Arrays.asList(new Object[][] {
            {DXFeedJson.MAPPER},
            {DXFeedJson.MAPPER_INDENT}
        });
    }

    private final ObjectMapper mapper;

    public MapperTest(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Test
    public void testQuoteMapper() throws IOException {
        long now = System.currentTimeMillis();
        Quote quote = new Quote("IBM");
        quote.setBidPrice(123.1);
        quote.setBidSize(100);
        quote.setBidTime(now - 1000);
        quote.setAskPrice(125.5);
        quote.setAskSize(200);
        quote.setAskTime(now - 2500);

        String jsonString = mapper.writeValueAsString(quote);
        Quote quote2 = mapper.readValue(jsonString, Quote.class);

        assertEquals(quote.toString(), quote2.toString());
    }
}