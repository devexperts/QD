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
package com.devexperts.util.test;

import com.devexperts.util.DayUtil;
import com.devexperts.util.TimeUtil;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Random;

import static org.junit.Assert.assertEquals;

public class DayUtilTest {

    @Test
    public void testDayIds() {
        // Test epoch
        assertEquals(0, DayUtil.getDayIdByYearMonthDay(1970, 1, 1));
        assertEquals(0, DayUtil.getDayIdByYearMonthDay(19700101));
        assertEquals(19700101, DayUtil.getYearMonthDayByDayId(0));

        // Test with a lot of random dates
        Random r = new Random(1);
        int minYear = -9999;
        int maxYear = 9999;
        for (int i = 0; i < 100000; i++) {
            int year = minYear + r.nextInt(maxYear - minYear + 1);
            int month = 1 + r.nextInt(12);

            GregorianCalendar cal = new GregorianCalendar(TimeUtil.getTimeZoneGmt());
            // use pure Gregorian calendar
            cal.setGregorianChange(new Date(Long.MIN_VALUE));
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.set(Calendar.YEAR, year);
            cal.set(Calendar.MONTH, month - 1);
            cal.set(Calendar.DAY_OF_MONTH, 1); // otherwise get actual max will not work
            int day = 1 + r.nextInt(cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            cal.set(Calendar.DAY_OF_MONTH, day);

            int yearSign = year < 0 ? -1 : 1;
            int ymd = yearSign * (Math.abs(year) * 10000 + month * 100 + day);
            String ymdString = "day " + ymd;

            long millis = cal.getTimeInMillis();
            assertEquals(year, cal.get(Calendar.ERA) == GregorianCalendar.AD ?
                cal.get(Calendar.YEAR) : 1 - cal.get(Calendar.YEAR));
            assertEquals(month, cal.get(Calendar.MONTH) + 1);
            assertEquals(day, cal.get(Calendar.DAY_OF_MONTH));
            assertEquals(0, millis % TimeUtil.DAY);
            int dayId = (int) (millis / TimeUtil.DAY);
            assertEquals(ymdString, dayId, DayUtil.getDayIdByYearMonthDay(year, month, day));
            assertEquals(ymdString, dayId, DayUtil.getDayIdByYearMonthDay(ymd));
            assertEquals(ymdString, ymd, DayUtil.getYearMonthDayByDayId(dayId));
        }
    }
}
