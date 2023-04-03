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
package com.devexperts.qd.qtp.text.test;

import com.devexperts.io.ByteArrayInput;
import com.devexperts.qd.DataIterator;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.qd.qtp.MessageConsumer;
import com.devexperts.qd.qtp.text.TextQTPParser;
import com.dxfeed.event.market.impl.ProfileMapping;
import com.dxfeed.event.market.impl.TimeAndSaleMapping;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class TextByteArrayParserTest {

    @Test
    public void testParseWithNulls() {
        String text =
            "==QD_STREAM_DATA\n" +
            "=TimeAndSale  Symbol  Time                  Sequence  Exchange  Price   Size    Bid.Price  Ask.Price  ExchangeSaleConditions  Flags\n" +
            "TimeAndSale   AAPL    20120503-083000-0500  59:19465  \\0        577.93  0       NaN        NaN        \\NULL                   4\n" +
            "=Profile Symbol Description\n" +
            "Profile   HABA    \"\\uaF23 Haba!\\0\\t\\n\\r\\f \"\n";
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);

        TextQTPParser parser = new TextQTPParser(QDFactory.getDefaultScheme());
        parser.setInput(new ByteArrayInput(bytes));
        final RecordBuffer buf = RecordBuffer.getInstance();
        parser.parse((MessageConsumer) Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class[] { MessageConsumer.class },
            (proxy, method, args) -> {
                if (!method.getName().equals("processStreamData"))
                    fail("Unexpected method " + method);
                buf.processData((DataIterator) args[0]);
                return null;
            }
        ));

        assertEquals(2, buf.size());

        // event #1
        RecordCursor cur = buf.next();
        assertNotNull(cur);
        assertEquals("TimeAndSale", cur.getRecord().getName());
        assertEquals("AAPL", cur.getSymbol());

        TimeAndSaleMapping tsm = cur.getRecord().getMapping(TimeAndSaleMapping.class);
        assertEquals(0, tsm.getExchange(cur));
        assertEquals(577.93, tsm.getPrice(cur), 0.0);
        assertEquals(0, tsm.getExchangeSaleConditions(cur));
        assertEquals(4, tsm.getFlags(cur));

        // event #2
        cur = buf.next();
        assertEquals("Profile", cur.getRecord().getName());
        assertEquals("HABA", cur.getSymbol());

        ProfileMapping pm = cur.getRecord().getMapping(ProfileMapping.class);
        assertEquals("\uaF23 Haba!\0\t\n\r\f ", pm.getDescription(cur));
    }
}
