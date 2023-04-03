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

import com.dxfeed.api.DXEndpoint;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Trade;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

@RunWith(value = Parameterized.class)
public class SchemePropertiesTest extends AbstractDXPublisherTest {
    private static final long NANO_TIME = 123_456_789_012L;
    private final boolean withNanoTime;

    public SchemePropertiesTest(boolean withNanoTime) {
        super(DXEndpoint.Role.LOCAL_HUB);
        this.withNanoTime = withNanoTime;
    }

    @Override
    protected DXEndpoint.Builder endpointBuilder() {
        DXEndpoint.Builder builder = super.endpointBuilder();
        if (withNanoTime)
            builder = builder.withProperty(DXEndpoint.DXSCHEME_NANO_TIME_PROPERTY, "true");
        return builder;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> parameters() {
        return Arrays.asList(new Object[][] {
            { true },
            { false }
        });
    }

    @Test
    public void testNanosTrade() throws InterruptedException {
        String symbol = "Trade";
        Trade trade = new Trade(symbol);
        trade.setTimeNanos(NANO_TIME);
        testGetLastEvent(Trade.class, trade, new Trade(), (publishedEvent, receivedEvent) -> assertEquals(
            withNanoTime ? NANO_TIME : (NANO_TIME / 1_000_000) * 1_000_000,
            receivedEvent.getTimeNanos()
        ));
    }

    @Test
    public void testNanosQuote() throws InterruptedException {
        String symbol = "Quote";
        Quote quote = new Quote(symbol);
        quote.setAskTime(NANO_TIME / 1_000_000);
        quote.setBidTime(NANO_TIME / 1_000_000 - 1);
        quote.setTimeNanoPart((int) (NANO_TIME % 1_000_000));
        testGetLastEvent(Quote.class, quote, new Quote(), (publishedEvent, receivedEvent) -> {
            long expectedNanos = withNanoTime ? NANO_TIME : (NANO_TIME / 1_000_000_000) * 1_000_000_000;
            assertEquals(expectedNanos, receivedEvent.getTimeNanos());
            long expectedTime = withNanoTime ? NANO_TIME / 1_000_000 : (NANO_TIME / 1_000_000_000) * 1_000;
            assertEquals(expectedTime, receivedEvent.getTime());
        });
    }
}
