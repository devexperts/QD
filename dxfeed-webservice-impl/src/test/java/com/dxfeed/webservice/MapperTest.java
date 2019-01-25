/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2019 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.webservice;

import java.io.IOException;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.io.ByteArrayOutput;
import com.dxfeed.event.candle.*;
import com.dxfeed.event.market.Quote;
import com.dxfeed.webservice.rest.Format;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Test mapping to/from XML/Json.
 *
 * Note that in order to create {@link Format#XML} full JAXB context must be created,
 * so any error in XML serialization (incorrect JAXB annotations) will show up in this test.
 */
public class MapperTest {
    @Test
    public void testQuote() throws IOException {
        testAll(makeQuote());
    }

    @Test
    public void testCandle() throws IOException {
        testAll(makeCandle());
    }

    private void testAll(Object event) throws IOException {
        testOne(event, Format.JSON, null);
        testOne(event, Format.JSON, "  ");
        testOne(event, Format.XML, null);
        testOne(event, Format.XML, "  ");
    }

    private void testOne(Object event, Format format, String indent) throws IOException {
        ByteArrayOutput bao = new ByteArrayOutput();
        format.writeTo(event, bao, indent);
        Object result = format.readFrom(new ByteArrayInput(bao.toByteArray()), event.getClass());
        assertEquals(event.toString(), result.toString());
    }

    private Quote makeQuote() {
        Quote quote = new Quote("IBM");
        quote.setBidPrice(123.1);
        quote.setBidSizeAsDouble(123.2);
        quote.setBidTime(System.currentTimeMillis() - 1000);
        quote.setAskPrice(125.5);
        quote.setAskSizeAsDouble(125.6);
        quote.setAskTime(System.currentTimeMillis() - 2500);
        return quote;
    }

    private Candle makeCandle() {
        Candle candle = new Candle(CandleSymbol.valueOf("IBM", CandlePeriod.DAY));
        candle.setTime(System.currentTimeMillis() - 1000);
        candle.setOpen(123.1);
        candle.setHigh(123.2);
        candle.setLow(123.0);
        candle.setClose(123.1);
        candle.setVolumeAsDouble(55.55);
        candle.setVWAP(77.77);
        return candle;
    }
}
