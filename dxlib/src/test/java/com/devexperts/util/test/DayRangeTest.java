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

import com.devexperts.util.DayRange;
import com.devexperts.util.DayUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DayRangeTest {

    @Test
    public void testRequestsForDayIdBceNotSupported() {
        int dayId = DayUtil.getDayIdByYearMonthDay(-1, 1, 1);
        assertThrows(IllegalArgumentException.class, () -> DayRange.getWeekRangeByDayId(dayId, 1));
        assertThrows(IllegalArgumentException.class, () -> DayRange.getMonthRangeByDayId(dayId, 1));
        assertThrows(IllegalArgumentException.class, () -> DayRange.getYearRangeByDayId(dayId, 1));
    }

    @Test
    public void testWeekDayRangeByDayId() {
        //start week
        int firstWeekStartDayId = DayUtil.getDayIdByYearMonthDay(19700105);
        for (int numberOfWeeks = 1; numberOfWeeks <= 10; numberOfWeeks++) {
            for (int offset = 0; offset <= 6; offset++) {
                DayRange range = DayRange.getWeekRangeByDayId(firstWeekStartDayId + offset, numberOfWeeks);
                assertEquals(firstWeekStartDayId, range.getStartDayId());
                assertEquals(firstWeekStartDayId + numberOfWeeks * 7, range.getEndDayId());
            }
        }
        //second week
        int secondWeekStartDayId = DayUtil.getDayIdByYearMonthDay(19700112);
        for (int dayOffset = 0; dayOffset <= 6; dayOffset++) {
            DayRange range = DayRange.getWeekRangeByDayId(secondWeekStartDayId + dayOffset, 1);
            assertEquals(secondWeekStartDayId, range.getStartDayId());
            assertEquals(secondWeekStartDayId + 7, range.getEndDayId());
        }
        for (int numberOfWeeks = 2; numberOfWeeks <= 10; numberOfWeeks++) {
            for (int offset = 0; offset <= 6; offset++) {
                DayRange range = DayRange.getWeekRangeByDayId(secondWeekStartDayId + offset, numberOfWeeks);
                assertEquals(firstWeekStartDayId, range.getStartDayId());
                assertEquals(firstWeekStartDayId + numberOfWeeks * 7, range.getEndDayId());
            }
        }
        //third week
        int thirdWeekStartDayId = DayUtil.getDayIdByYearMonthDay(19700119);
        for (int numberOfWeeks = 1; numberOfWeeks <= 2; numberOfWeeks++) {
            for (int dayOffset = 0; dayOffset <= 6; dayOffset++) {
                DayRange range = DayRange.getWeekRangeByDayId(thirdWeekStartDayId + dayOffset, 1);
                assertEquals(thirdWeekStartDayId, range.getStartDayId());
                assertEquals(thirdWeekStartDayId + 7, range.getEndDayId());
            }
        }
        for (int numberOfWeeks = 3; numberOfWeeks <= 10; numberOfWeeks++) {
            for (int offset = 0; offset <= 6; offset++) {
                DayRange range = DayRange.getWeekRangeByDayId(thirdWeekStartDayId + offset, numberOfWeeks);
                assertEquals(firstWeekStartDayId, range.getStartDayId());
                assertEquals(firstWeekStartDayId + numberOfWeeks * 7, range.getEndDayId());
            }
        }
        //weeks before 1st January 1970
        DayRange range = DayRange.getWeekRangeByDayId(DayUtil.getDayIdByYearMonthDay(19700104), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691229), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700105), range.getEndDayId());

        range = DayRange.getWeekRangeByDayId(DayUtil.getDayIdByYearMonthDay(19700104), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691222), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700105), range.getEndDayId());

        range = DayRange.getWeekRangeByDayId(DayUtil.getDayIdByYearMonthDay(19700104), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691215), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700105), range.getEndDayId());

        range = DayRange.getWeekRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691231), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691229), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700105), range.getEndDayId());

        range = DayRange.getWeekRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691231), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691222), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700105), range.getEndDayId());

        range = DayRange.getWeekRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691231), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691215), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700105), range.getEndDayId());

        range = DayRange.getWeekRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691229), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691229), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700105), range.getEndDayId());

        range = DayRange.getWeekRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691229), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691222), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700105), range.getEndDayId());

        range = DayRange.getWeekRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691229), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691215), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700105), range.getEndDayId());

        range = DayRange.getWeekRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691228), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691222), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691229), range.getEndDayId());

        range = DayRange.getWeekRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691228), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691222), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700105), range.getEndDayId());

        range = DayRange.getWeekRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691228), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691215), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700105), range.getEndDayId());

        range = DayRange.getWeekRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691216), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691208), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691222), range.getEndDayId());

        range = DayRange.getWeekRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691216), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691215), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700105), range.getEndDayId());
    }

    @Test
    public void testMonthStartDayIdByDayId() {
        // Start month
        int firstMonthStartDayId = DayUtil.getDayIdByYearMonthDay(19700101);
        for (int numberOfMonths = 1; numberOfMonths <= 10; numberOfMonths++) {
            for (int offset = 0; offset <= 28; offset++) {
                DayRange range = DayRange.getMonthRangeByDayId(firstMonthStartDayId + offset, numberOfMonths);
                assertEquals(firstMonthStartDayId, range.getStartDayId());
                for (int j = 0; j < 28 * numberOfMonths; j++) {
                    assertTrue(range.containsDayId(firstMonthStartDayId + j));
                }
            }
        }

        DayRange range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19700215), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700201), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700301), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19700215), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700301), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19700415), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700301), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700501), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19700415), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700401), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700701), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19700415), 36);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19730101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19740415), 36);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19730101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19760101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19730415), 11);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19721001), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19730901), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19740415), 11);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19730901), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19740801), range.getEndDayId());

        // Months before 1st January 1970
        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691201), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691201), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691230), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691201), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691230), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691230), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691001), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691230), 11);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19690201), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691130), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691201), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691130), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691130), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691001), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691130), 11);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19690201), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691030), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691001), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691030), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19690901), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691030), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19691001), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691030), 11);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19690201), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19690130), 11);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19680301), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19690201), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19690130), 22);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19680301), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19680130), 11);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19670401), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19680301), range.getEndDayId());

        range = DayRange.getMonthRangeByDayId(DayUtil.getDayIdByYearMonthDay(19680130), 22);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19660501), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19680301), range.getEndDayId());
    }

    @Test
    public void testYearStartDayIdByDayId() {
        DayRange range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19700215), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19710101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19700101), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19710101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19701231), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19710101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19700215), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19720101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19700101), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19720101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19701231), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19720101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19700215), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19730101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19700101), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19730101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19701231), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19730101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19710415), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19730101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19720415), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19730101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19740415), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19730101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19760101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19730415), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19730101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19760101), range.getEndDayId());

        //years before 1st January 1970
        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691231), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19690101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691201), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19690101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19690101), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19690101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19681101), 1);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19680101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19690101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691231), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19680101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691201), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19680101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19690101), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19680101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19671231), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19660101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19680101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19671201), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19660101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19680101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19670101), 2);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19660101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19680101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691231), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19670101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19691201), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19670101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());

        range = DayRange.getYearRangeByDayId(DayUtil.getDayIdByYearMonthDay(19690101), 3);
        assertEquals(DayUtil.getDayIdByYearMonthDay(19670101), range.getStartDayId());
        assertEquals(DayUtil.getDayIdByYearMonthDay(19700101), range.getEndDayId());
    }
}
