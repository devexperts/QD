/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.fieldreplacer.test;

import com.devexperts.util.DayUtil;
import com.devexperts.util.TimeFormat;
import com.dxfeed.event.market.Order;
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.Quote;
import com.dxfeed.event.market.Summary;
import com.dxfeed.event.market.TimeAndSale;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class ValueFieldReplacerFactoryTest extends AbstractFieldReplacerTest {

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidField() {
        setReplacer("value:TimeAndSale*:Foobar:42");

        TimeAndSale initialEvent = new TimeAndSale("IBM");
        testEvent(initialEvent, event -> false);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidValueType() {
        setReplacer("value:Quote*:BidPrice:ABC");
        
        Quote initialEvent = new Quote("IBM");
        testEvent(initialEvent, event -> false);
    }

    @Test
    public void testIntegerField() {
        setReplacer("value:TimeAndSale*:Size:42");

        TimeAndSale initialEvent = new TimeAndSale("IBM");
        testEvent(initialEvent, event -> event.getSize() == 42);
    }

    @Test
    public void testWideDecimal() {
        setReplacer("value:Quote*:BidPrice:42.42");

        Quote initialEvent = new Quote("IBM");
        testEvent(initialEvent, event -> event.getBidPrice() == 42.42);
    }

    @Test
    public void testTimeMillis() {
        String time = "2024-12-06T12:34:56+03:00";
        long timeMillis = TimeFormat.DEFAULT.parse(time).getTime();
        setReplacer("value:Order*:ActionTime:" + time);

        Order initialEvent = new Order("IBM");
        testEvent(initialEvent, event -> event.getActionTime() == timeMillis);
    }

    @Test
    public void testDayId() {
        int ymd = 20241204;
        setReplacer("value:*:DayId:" + ymd);

        Summary initialEvent = new Summary("IBM");
        testEvent(initialEvent, event -> event.getDayId() == DayUtil.getDayIdByYearMonthDay(ymd));
    }

    @Test
    public void testString() {
        String message = "Error: bad mood";
        setReplacer("value:Profile:StatusReason:" + message);

        Profile initialEvent = new Profile("IBM");
        testEvent(initialEvent, event -> event.getStatusReason().equals(message));
    }

    @Test
    public void testEnum() {
        setReplacer("value:Order:MMID:ABCD");

        Order initialEvent = new Order("IBM");
        testEvent(initialEvent, event -> event.getMarketMaker().equals("ABCD"));
    }

    @Test
    public void testMultipleReplacers() {
        setReplacer("(value:Order:MMID:ABCD)(value:Order:Price:42.2)(value:Order:Size:100)");

        Order initialEvent = new Order("IBM");
        testEvent(initialEvent, event ->
            event.getMarketMaker().equals("ABCD") && event.getPrice() == 42.2 && event.getSize() == 100);
    }

    @Test
    public void testOverridingReplacers() {
        setReplacer("(value:Order:Size:100)(value:Order:Size:200)(value:Order:Size:300)");

        Order initialEvent = new Order("IBM");
        testEvent(initialEvent, event -> event.getSize() == 300);
    }
}
