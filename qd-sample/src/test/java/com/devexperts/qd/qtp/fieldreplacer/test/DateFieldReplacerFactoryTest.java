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

import com.devexperts.util.Timing;
import com.dxfeed.event.market.Profile;
import com.dxfeed.event.market.Summary;
import com.dxfeed.event.market.Trade;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

@SuppressWarnings("unchecked")
public class DateFieldReplacerFactoryTest extends AbstractFieldReplacerTest {

    private static final Timing TIMING = Timing.LOCAL;
    
    private static final int INITIAL_DAY_ID = TIMING.getByYmd(20240101).day_id;
    private static final int STRATEGY_DATE = 20241212;
    private static final int STRATEGY_DAY_ID = TIMING.getByYmd(STRATEGY_DATE).day_id;

    private static boolean checkCurrentDay(int dayId, int dayShift) {
        long now = System.currentTimeMillis();
        Timing.Day currentDay = TIMING.getByTime(now);
        int expectedDayId = currentDay.day_id + dayShift;

        // Do a non-strict comparison allowing for tests to run around midnight
        return (dayId == expectedDayId) || (now < currentDay.day_start + 600_000 && dayId == expectedDayId - 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidDate() {
        setReplacer("date:Trade*:DayId:ABC");

        Trade initialEvent = new Trade("IBM");
        initialEvent.setDayId(INITIAL_DAY_ID);

        testEvent(initialEvent, event -> false);
    }

    @Test
    public void testCurrentDate() {
        setReplacer("date:*:DayId:current");

        Trade initialEvent = new Trade("IBM");
        initialEvent.setDayId(INITIAL_DAY_ID);

        testEvent(initialEvent, event -> checkCurrentDay(event.getDayId(), 0));
    }

    @Test
    public void testCurrentDateWithTimeZone() {
        int[] dates = new int[2];

        {
            setReplacer("date:*:DayId:current:GMT+13:00");
            Trade initialEvent = new Trade("IBM");
            initialEvent.setDayId(INITIAL_DAY_ID);
            testEvent(initialEvent, event -> { dates[0] = event.getDayId(); return true; });
        }

        {
            setReplacer("date:*:DayId:current:GMT-13:00");
            Trade initialEvent = new Trade("IBM");
            initialEvent.setDayId(INITIAL_DAY_ID);
            testEvent(initialEvent, event -> { dates[1] = event.getDayId(); return true; });
        }

        assertTrue(dates[0] != dates[1]);
    }

    @Test
    public void testPreviousDate() {
        setReplacer("date:*:PrevDayId:previous");

        Summary initialEvent = new Summary("IBM");
        initialEvent.setPrevDayId(INITIAL_DAY_ID);

        testEvent(initialEvent, event -> checkCurrentDay(event.getPrevDayId(), -1));
    }

    @Test
    public void testIncreaseDate() {
        setReplacer("date:Profile:ExdDivDate:+3");

        Profile initialEvent = new Profile("IBM");
        initialEvent.setExDividendDayId(INITIAL_DAY_ID);

        testEvent(initialEvent, event -> event.getExDividendDayId() == INITIAL_DAY_ID + 3);
    }

    @Test
    public void testDecreaseDate() {
        setReplacer("date:Profile:ExdDivDate:-2");

        Profile initialEvent = new Profile("IBM");
        initialEvent.setExDividendDayId(INITIAL_DAY_ID);

        testEvent(initialEvent, event -> event.getExDividendDayId() == INITIAL_DAY_ID - 2);
    }

    @Test
    public void testSpecifyDate() {
        setReplacer("date:Summary:DayId:" + STRATEGY_DATE);

        Summary initialEvent = new Summary("IBM");
        initialEvent.setDayId(INITIAL_DAY_ID);

        testEvent(initialEvent, event -> event.getDayId() == STRATEGY_DAY_ID);
    }

    @Test
    public void testZeroDate() {
        setReplacer("date:Summary:DayId:0");

        Summary initialEvent = new Summary("IBM");
        initialEvent.setDayId(INITIAL_DAY_ID);

        testEvent(initialEvent, event -> event.getDayId() == 0);
    }
}
