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
package com.dxfeed.api.test;

import com.devexperts.logging.Logging;
import com.devexperts.qd.DataRecord;
import com.devexperts.qd.DataScheme;
import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFactory;
import com.devexperts.qd.ng.RecordBuffer;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.test.isolated.Isolated;
import com.devexperts.test.isolated.IsolatedParametersRunnerFactory;
import com.devexperts.util.SystemProperties;
import com.dxfeed.api.impl.EventDelegateFlags;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.OrderByMarketMakerAskDelegate;
import com.dxfeed.event.market.OrderByMarketMakerBidDelegate;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.QuoteDelegate;
import com.dxfeed.event.market.impl.MarketMakerMapping;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(Parameterized.class)
@Parameterized.UseParametersRunnerFactory(IsolatedParametersRunnerFactory.class)
@Isolated({"com.dxfeed.api", "com.dxfeed.event", "com.devexperts.qd"})
public class BidAskTimeMappingTest {

    public static final Logging log = Logging.getLogging(BidAskTimeMappingTest.class);

    @Parameter(0)
    public TimeUnit precision;

    @Parameter(1)
    public String property;
    private DataScheme scheme;

    @Parameters(name = "{0}")
    public static Iterable<Object[]> params() {
        return Arrays.asList(
            new Object[][]{
                {TimeUnit.SECONDS, ""},
                {TimeUnit.SECONDS, "dxscheme.bat=seconds"},
                {TimeUnit.MILLISECONDS, "dxscheme.bat=millis"}
            });
    }

    private Properties oldProps;

    @Before
    public void setup() {
        if (property.isEmpty())
            return;
        oldProps = System.getProperties();
        System.setProperties((Properties) oldProps.clone());
        String[] kv = property.split("=", 2);
        System.setProperty(kv[0], kv[1]);
    }

    @After
    public void tearDown() {
        if (oldProps != null)
            System.setProperties(oldProps);
        oldProps = null;
    }

    /**
     * Test generated API event mappings & delegates compatibility with various dxscheme.bat settings
     */
    @Test
    public void testMappings() {
        assertNull(SystemProperties.getProperty("scheme", null));
        scheme = QDFactory.createDefaultScheme(getClass().getClassLoader());
        log.info("Internal digest: " + scheme.getDigest());

        doTestQuoteMapping();
        doTestMarketMakerMapping();
    }

    private void doTestQuoteMapping() {
        DataRecord quoteRecord = scheme.findRecordByName("Quote");
        RecordBuffer buffer = new RecordBuffer();

        QuoteDelegate quoteDelegate =
            new QuoteDelegate(quoteRecord, QDContract.STREAM, EnumSet.of(EventDelegateFlags.PUB));
        Quote fromQuote = quoteDelegate.createEvent();
        fromQuote.setEventSymbol("AAPL");
        fromQuote.setAskExchangeCode('A');
        fromQuote.setBidExchangeCode('B');
        fromQuote.setAskTime(10_123);
        fromQuote.setBidTime(20_456);
        RecordCursor cursor = quoteDelegate.putEvent(fromQuote, buffer);
        Quote toQuote = quoteDelegate.createEvent(cursor);
        if (precision == TimeUnit.MILLISECONDS) {
            assertEquals(fromQuote.getAskTime(), toQuote.getAskTime());
            assertEquals(fromQuote.getBidTime(), toQuote.getBidTime());
        } else {
            assertEquals(10_000, toQuote.getAskTime());
            assertEquals(20_000, toQuote.getBidTime());
        }
    }

    private void doTestMarketMakerMapping() {
        DataRecord quoteRecord = scheme.findRecordByName("MarketMaker");
        RecordBuffer buffer = new RecordBuffer();

        OrderByMarketMakerAskDelegate askDelegate =
            new OrderByMarketMakerAskDelegate(quoteRecord, QDContract.STREAM, EnumSet.of(EventDelegateFlags.PUB));
        OrderByMarketMakerBidDelegate bidDelegate =
            new OrderByMarketMakerBidDelegate(quoteRecord, QDContract.STREAM, EnumSet.of(EventDelegateFlags.PUB));

        MarketMakerMapping mapping = quoteRecord.getMapping(MarketMakerMapping.class);
        String symbol = "XXX";
        RecordCursor cursor = buffer.add(quoteRecord, scheme.getCodec().encode(symbol), symbol);

        mapping.setAskTimeMillis(cursor, 10_123);
        mapping.setBidTimeMillis(cursor, 20_456);
        Order askOrder = askDelegate.createEvent(cursor);
        Order bidOrder = bidDelegate.createEvent(cursor);
        if (precision == TimeUnit.MILLISECONDS) {
            assertEquals(10_123, askOrder.getTime());
            assertEquals(20_456, bidOrder.getTime());
        } else {
            assertEquals(10_000, askOrder.getTime());
            assertEquals(20_000, bidOrder.getTime());
        }
    }
}
