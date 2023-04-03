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
package com.dxfeed.schedule.test;

import com.devexperts.util.DayUtil;
import com.dxfeed.schedule.Day;
import com.dxfeed.schedule.DayFilter;
import com.dxfeed.schedule.Schedule;
import com.dxfeed.schedule.Session;
import com.dxfeed.schedule.SessionFilter;
import com.dxfeed.schedule.SessionType;
import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.devexperts.util.TimeUtil.DAY;
import static com.devexperts.util.TimeUtil.HOUR;
import static com.devexperts.util.TimeUtil.MINUTE;
import static com.devexperts.util.TimeUtil.SECOND;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ScheduleTest {

    @Test
    public void testParsing() {
        assertEquals(0, gmt("0=").getDayById(42).getStartTime() % DAY);
        assertEquals(23 * HOUR, gmt("de=2300;0=").getDayById(42).getStartTime() % DAY);
        assertEquals(0, gmt("0=01000200").getDayById(42).getNextDay(DayFilter.NON_TRADING).getStartTime() % DAY);
        assertEquals(1, gmt("0=01000200").getDayById(42).getNextDay(DayFilter.NON_TRADING).getSessions().size());
        assertEquals(HOUR, gmt("0=01000200").getNearestSessionByTime(System.currentTimeMillis(),
            SessionFilter.TRADING).getStartTime() % DAY);

        check("0=", "000000", "NO_TRADING");
        check("0=01000200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=0100/0200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=+-0100-+0200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=+-0100/-+0200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r01000200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r0100/0200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r+-0100-+0200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r+-0100/-+0200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=010002000300040005000600", "000000,010000,020000,030000,040000,050000,060000",
            "NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING");
        check("0=0100/02000300/04000500/0600", "000000,010000,020000,030000,040000,050000,060000",
            "NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING");
        check("0=+-0100-+0200+-0300-+0400+-0500-+0600", "000000,010000,020000,030000,040000,050000,060000",
            "NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING");
        check("0=+-0100/-+0200+-0300/-+0400+-0500/-+0600", "000000,010000,020000,030000,040000,050000,060000",
            "NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING");
        check("0=p01000200r03000400a05000600", "000000,010000,020000,030000,040000,050000,060000",
            "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");
        check("0=p0100/0200r0300/0400a0500/0600", "000000,010000,020000,030000,040000,050000,060000",
            "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");
        check("0=p+-0100-+0200r+-0300-+0400a+-0500-+0600", "000000,010000,020000,030000,040000,050000,060000",
            "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");
        check("0=p+-0100/-+0200r+-0300/-+0400a+-0500/-+0600", "000000,010000,020000,030000,040000,050000,060000",
            "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");

        check("0=010203020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=010203/020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=+-010203-+020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=+-010203/-+020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r010203020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r010203/020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r+-010203-+020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r+-010203/-+020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=p010203020406r030507040608a050709060810",
            "000000,010203,020406,030507,040608,050709,060810",
            "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");
        check("0=p010203/020406r030507/040608a050709/060810",
            "000000,010203,020406,030507,040608,050709,060810",
            "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");
        check("0=p+-010203-+020406r+-030507-+040608a+-050709-+060810",
            "000000,010203,020406,030507,040608,050709,060810",
            "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");
        check("0=p+-010203/-+020406r+-030507/-+040608a+-050709/-+060810",
            "000000,010203,020406,030507,040608,050709,060810",
            "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");

        check("0=rt0300r05000600", "000000,050000,060000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=rt+-0300r-+0500-+0600", "000000,050000,060000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r05000600rt0300", "000000,050000,060000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r+-0500/0600rt+-0300", "000000,050000,060000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=05000600rt0300", "000000,050000,060000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=0500/0600rt-+0300", "000000,050000,060000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=rt030405r05000600", "000000,050000,060000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=05000600rt030405", "000000,050000,060000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=0500/0600rt-+030405", "000000,050000,060000", "NO_TRADING,REGULAR,NO_TRADING");

        check("0=d0100230005000600rt0300", "010000,050000,060000", "NO_TRADING,REGULAR,NO_TRADING");
    }

    @Test
    public void testShortDaysStrategyEarlyClose () {
        Map<Integer, String> map = new HashMap<>();
        map.put(1, "07000930");
        map.put(2, "09301230");
        map.put(3, "12301530");
        checkSessions("sd=20191224;sds=ec0330;0=p07000930r09301600a16001900", 20191224, map);

        map.clear();
        map.put(1, "07000930");
        map.put(2, "09301600");
        map.put(3, "16001900");
        checkSessions("sd=20191224;sds=ec0000;0=p07000930r09301600a16001900", 20191224, map);

        map.clear();
        map.put(1, "07000930");
        map.put(2, "09301600");
        map.put(3, "16001900");
        checkSessions("sd=20191224;sds=ec0330;0=p07000930r09301600a16001900", 20191223, map);

        map.clear();
        map.put(1, "07000930");
        map.put(2, "09301600");
        map.put(3, "16001900");
        checkSessions("sd=20191224;0=p07000930r09301600a16001900", 20191224, map);

        map.clear();
        map.put(1, "07000930");
        map.put(2, "09301600");
        map.put(3, "16001900");
        checkSessions("sd=20191224;sds=ec0330;0=p07000930p09301600a16001900", 20191224, map);

        map.clear();
        map.put(1, "07000800");
        map.put(2, "08001200");
        map.put(3, "12001300");
        map.put(4, "13001500");
        map.put(5, "15001700");
        checkSessions("sd=20191224;sds=ec0200;0=p07000800r08001200p12001300r13001700a17001900", 20191224, map);

        map.clear();
        map.put(1, "07000930");
        map.put(2, "09302030");
        map.put(3, "20300000");
        checkSessions("sd=20191224;sds=ec0330;0=p07000930r0930+0000", 20191224, map, true);

        map.clear();
        map.put(1, "10001700");
        map.put(2, "17001800");
        map.put(3, "18001900");
        checkSessions("sd=20191224;sds=ec0330;0=p10001700r17001800a18001900", 20191224, map);
    }

    @Test
    public void testBrokenStrategies() {
        checkException("(tz=GMT;sd=20191224;sds=0330;0=)", "broken sds strategy for ");
        checkException("(tz=GMT;sd=20191224;sds=ec;0=)", "broken sds strategy for ");
        checkException("(tz=GMT;sd=20191224;sds=ec-0330;0=)", "broken sds strategy for ");
        checkException("(tz=GMT;sd=20191224;sds=ec+0330;0=)", "broken sds strategy for ");
        checkException("(tz=GMT;sd=20191224;sds=ec0j60;0=)", "broken sds strategy for ");

        checkException("(tz=GMT;hd=20191224;hds=42;0=)", "broken hds strategy for ");
        checkException("(tz=GMT;hd=20191224;hds=jntd;0=)", "broken hds strategy for ");
        checkException("(tz=GMT;hd=20191224;hds=jntd-1;0=)", "broken hds strategy for ");
        checkException("(tz=GMT;hd=20191224;hds=jntd+1;0=)", "broken hds strategy for ");
        checkException("(tz=GMT;hd=20191224;hds=jntd2020;0=)", "broken hds strategy for ");
    }

    @Test
    public void testSessionsValidation() {
        // unordered session bounds
        checkException("(tz=GMT;0=10000900)", "illegal session period 10000900 in 10000900 in ");
        // invalid day fields
        checkException("(tz=GMT;0=10651200)", "illegal time 1065 in ");
        checkException("(tz=GMT;0=10002500)", "illegal time 2500 in ");
        checkException("(tz=GMT;0=100000120063)", "illegal time 120063 in ");
        // unordered sessions
        checkException("(tz=GMT;0=;1=10001100p09001000)", "illegal session order at 1 for 0:11:0:0 and 0:9:0:0 in ");
    }

    /**
     * Test day and session continuity for US holidays and disabled strategies
     */
    @Test
    public void testContinuityForDisabledStrategies() {
        String def = "dis(tz=America/Chicago;hd=US;sd=US;td=12345;de=1605;0=p06000830r08301515;sds=ec0000;hds=jntd0)";
        Schedule schedule = Schedule.getInstance(def);
        Schedule fallback = Schedule.getInstance(def.replaceAll(";sds=.*[)]", ")"));
        checkSame(def, schedule, fallback, 20100101, 20210101);
    }

    /**
     * Test day and session continuity for US holidays and related jntd strategies
     */
    @Test
    public void testContinuityForUSJntdStrategies() {
        for (int i : new int[] {1, 2, 3, 5, 9}) {
            String def = "con(tz=America/Chicago;hd=US;sd=US;td=12345;de=1605;0=p06000830r08301515;hds=jntd" + i + ")";
            Schedule schedule = Schedule.getInstance(def);
            Schedule backward = Schedule.getInstance("backward" + def);
            checkSame(def, schedule, backward, 20100101, 20210101);
        }
    }

    /**
     * Test day and session continuity for TR holidays and related jntd strategies
     */
    @Test
    public void testContinuityForTRJntdStrategies() {
        for (int i : new int[] {1, 2, 3, 5, 9}) {
            String def = "con(tz=America/Chicago;hd=TR;sd=TR;td=12345;de=1605;0=p06000830r08301515;hds=jntd" + i + ")";
            Schedule schedule = Schedule.getInstance(def);
            Schedule backward = Schedule.getInstance("backward" + def);
            checkSame(def, schedule, backward, 20100101, 20210101);
        }
    }

    /**
     * Test jntd strategy turning to fallback scenario (no jntd) when holiday is on a weekend
     */
    @Test
    public void testJntdInapplicable() {
        String def = "miss(tz=America/Chicago;hd=20200704,20201227;td=12345;de=1605;0=p06000830r08301515;hds=jntd3)";
        Schedule schedule = Schedule.getInstance(def);
        Schedule fallback = Schedule.getInstance(def.replaceAll(";hds=jntd[0-9]", ""));
        checkSame(def, schedule, fallback, 20200703, 20200706);
        checkSame(def, schedule, fallback, 20201225, 20201228);
    }

    /**
     * Test jntd strategy turning to fallback scenario (no jntd) when jntd duration is less than necessary
     */
    @Test
    public void testJntdTooShortToAct() {
        String def = "short(tz=America/Chicago;hd=20200703,20201225;td=12345;de=1605;0=p06000830r08301515;hds=jntd2)";
        Schedule schedule = Schedule.getInstance(def);
        Schedule fallback = Schedule.getInstance(def.replaceAll(";hds=jntd[0-9]", ""));
        checkSame(def, schedule, fallback, 20200703, 20200706);
        checkSame(def, schedule, fallback, 20201225, 20201228);
    }

    /**
     * Test jntd strategy when holiday is followed by trading day
     */
    @Test
    public void testJntdWithFollowingTradingDay() {
        String def = "trading(tz=America/Chicago;hd=20200120,20201126;td=12345;de=1605;0=p06000830r08301515;hds=jntd1)";
        checkJntdInActionAllPermutations(def, 20200120, 20200121);
        checkJntdInActionAllPermutations(def, 20201126, 20201127);
    }

    /**
     * Test jntd strategy when holiday is followed by more holidays and then by trading day
     */
    @Test
    public void testJntdWithFollowingHolidayAndTradingDay() {
        String def = "holidayTrading(tz=America/Chicago;hd=20200120,20200121,20200122;" +
            "td=12345;de=1605;0=p06000830r08301515;hds=jntd3)";
        checkJntdInActionAllPermutations(def, 20200120, 20200123);
    }

    /**
     * Test jntd strategy cases when holiday is followed by weekend
     */
    @Test
    public void testJntdOverWeekend() {
        String def = "weekend(tz=America/Chicago;hd=20200703,20201225;td=12345;de=1605;0=p06000830r08301515;hds=jntd3)";
        checkJntdInActionAllPermutations(def, 20200703, 20200706);
        checkJntdInActionAllPermutations(def, 20201225, 20201228);
    }

    /**
     * Test jntd strategy when holiday is followed by weekend which is also marked as holidays
     */
    @Test
    public void testJntdOverHolidayWeekend() {
        String def = "holidayWeekend(tz=America/Chicago;hd=20200703,20200704,20200705;" +
            "td=12345;de=1605;0=p06000830r08301515;hds=jntd3)";
        checkJntdInActionAllPermutations(def, 20200703, 20200706);
    }

    /**
     * Test jntd strategy when holiday is followed by more holidays and then by weekend
     */
    @Test
    public void testJntdDualHolidaysOverWeekend() {
        String def = "longHolidayWeekend(tz=America/Chicago;hd=20201126,20201127;" +
            "td=12345;de=1605;0=p06000830r08301515;hds=jntd5)";
        checkJntdInActionAllPermutations(def, 20201126, 20201130);
    }

    /**
     * Test hd sets union with default syntax
     */
    @Test
    public void testHdSetsUnion() {
        String def = "hd=US,CA;0=08001800;td=1234567";
        assertFalse("US and CA not holiday", gmt(def).getDayByYearMonthDay(20210606).isHoliday());
        assertTrue("US Holiday", gmt(def).getDayByYearMonthDay(20210118).isHoliday());
        assertTrue("CA Holiday", gmt(def).getDayByYearMonthDay(20210524).isHoliday());
        assertTrue("US and CA Holiday", gmt(def).getDayByYearMonthDay(20210215).isHoliday());
    }

    /**
     * Test hd sets and days union with default syntax
     */
    @Test
    public void testHdSetsAndDaysUnion() {
        String def = "hd=US,CA,20211010;0=08001800;td=1234567";
        assertFalse("US and CA not holiday", gmt(def).getDayByYearMonthDay(20210606).isHoliday());
        assertTrue("US Holiday", gmt(def).getDayByYearMonthDay(20210118).isHoliday());
        assertTrue("CA Holiday", gmt(def).getDayByYearMonthDay(20210524).isHoliday());
        assertTrue("US and CA Holiday", gmt(def).getDayByYearMonthDay(20210215).isHoliday());
        assertTrue("Custom Holiday", gmt(def).getDayByYearMonthDay(20211010).isHoliday());
    }

    /**
     * Test hd sets difference
     */
    @Test
    public void testHdSetsDiff() {
        String def = "hd=US,-CA;0=08001800;td=1234567";
        assertFalse("US and CA not holiday", gmt(def).getDayByYearMonthDay(20210606).isHoliday());
        assertTrue("US Only Holiday", gmt(def).getDayByYearMonthDay(20210118).isHoliday());
        assertFalse("CA Holiday", gmt(def).getDayByYearMonthDay(20210524).isHoliday());
        assertFalse("US and CA Holiday", gmt(def).getDayByYearMonthDay(20210215).isHoliday());
    }

    /**
     * Test hd sets and days difference
     */
    @Test
    public void testHdSetsDanDaysDiff() {
        String def = "hd=US,-CA,-20210531;0=08001800;td=1234567";
        assertFalse("US and CA not holiday", gmt(def).getDayByYearMonthDay(20210606).isHoliday());
        assertTrue("US Only Holiday", gmt(def).getDayByYearMonthDay(20210118).isHoliday());
        assertFalse("CA Holiday", gmt(def).getDayByYearMonthDay(20210524).isHoliday());
        assertFalse("US and CA Holiday", gmt(def).getDayByYearMonthDay(20210215).isHoliday());
        assertFalse("Custom removed holiday", gmt(def).getDayByYearMonthDay(20210531).isHoliday());
    }

    /**
     * Test hd sets intersection
     */
    @Test
    public void testHdSetsIntersect() {
        String def = "hd=US,*CA;0=08001800;td=1234567";
        assertFalse("US and CA not holiday", gmt(def).getDayByYearMonthDay(20210606).isHoliday());
        assertFalse("US Only Holiday", gmt(def).getDayByYearMonthDay(20210118).isHoliday());
        assertFalse("CA Holiday", gmt(def).getDayByYearMonthDay(20210524).isHoliday());
        assertTrue("US and CA Holiday", gmt(def).getDayByYearMonthDay(20210215).isHoliday());
    }

    /**
     * Test hd sets and days intersect
     */
    @Test
    public void testHdSetsAndDaysIntersect() {
        String def = "hd=20210101,*US,*CA;0=08001800;td=1234567";
        assertFalse("US and CA not holiday", gmt(def).getDayByYearMonthDay(20210606).isHoliday());
        assertFalse("US Only Holiday", gmt(def).getDayByYearMonthDay(20210118).isHoliday());
        assertFalse("CA Holiday", gmt(def).getDayByYearMonthDay(20210524).isHoliday());
        assertFalse("US and CA Holiday", gmt(def).getDayByYearMonthDay(20210215).isHoliday());
        assertTrue("Only one remain holiday", gmt(def).getDayByYearMonthDay(20210101).isHoliday());
    }

    /**
     * Test hd sets and days intersect with wrong syntax
     */
    @Test
    public void testHdSetsAndDaysIntersectDay() {
        String def = "hd=US,CA,*20210101;0=08001800;td=1234567";
        assertFalse("US and CA not holiday", gmt(def).getDayByYearMonthDay(20210606).isHoliday());
        assertFalse("US Only Holiday", gmt(def).getDayByYearMonthDay(20210118).isHoliday());
        assertFalse("CA Holiday", gmt(def).getDayByYearMonthDay(20210524).isHoliday());
        assertFalse("US and CA Holiday", gmt(def).getDayByYearMonthDay(20210215).isHoliday());
        assertTrue("Only one remain holiday", gmt(def).getDayByYearMonthDay(20210101).isHoliday());
    }

    // check jntd strategy in action for given holiday; generates all permutations to load days in that order
    private void checkJntdInActionAllPermutations(String def, int holidayYmd, int tradingYmd) {
        // fallback schedule has no holidays and no holiday strategy; use it to clearly see strategy effect
        String fallbackDef = def.replaceAll(";hd=[^;]*;", ";").replaceAll(";hds=jntd[0-9]", "");
        int[] permutation = new int[tradingYmd - holidayYmd + 1];
        // generate all permutations; but no more than a certain number to put a limit on max test duration
        for (int i = 0; i < 1000; i++) {
            // re-init permutation in ascending order
            for (int k = 0; k < permutation.length; k++)
                permutation[k] = holidayYmd + k;
            // use Fisher–Yates shuffle algorithm to build permutation by it's numeric ID (here ID == iteration index)
            // the numeric ID in a factorial number system is used by shuffle instead of random number generator
            // this algorithm produces all permutations, although in a weird order (not important here)
            int remainder = i;
            for (int k = permutation.length; k > 1; k--) {
                int r = remainder % k;
                remainder /= k;
                int swap = permutation[k - 1];
                permutation[k - 1] = permutation[r];
                permutation[r] = swap;
            }
            if (remainder != 0) {
                // an indication that all permutations were produced prior to this iteration and we are repeating them
                // starts to happen when (ID == n!) and produces same permutation as for (ID modulo n!)
                break;
            }

            // prepend identity of schedule with iteration number to effectively bypass caching in Schedule
            // this is needed to get clear start for each permutation for it to matter
            Schedule schedule = Schedule.getInstance(i + def);
            Schedule fallback = Schedule.getInstance(i + fallbackDef);
            checkJntdInAction(schedule, fallback, holidayYmd, tradingYmd, permutation);
        }
    }

    // check jntd strategy in action for given holiday; load days in order of given permutation
    private void checkJntdInAction(Schedule schedule, Schedule fallback, int holidayYmd, int tradingYmd,
        int... permutation)
    {
        for (int ymd : permutation) {
            int dayId = DayUtil.getDayIdByYearMonthDay(ymd);
            schedule.getDayById(dayId);
            fallback.getDayById(dayId);
        }
        int holidayDayId = DayUtil.getDayIdByYearMonthDay(holidayYmd);
        int tradingDayId = DayUtil.getDayIdByYearMonthDay(tradingYmd);
        long startTime = fallback.getDayById(holidayDayId).getStartTime(); // when longTradingDay shall start
        long endTime = fallback.getDayById(tradingDayId).getEndTime(); // when longTradingDay shall end
        Day longTradingDay = schedule.getDayById(tradingDayId);

        assertTrue(schedule.getDayById(holidayDayId).isHoliday());

        assertEquals(startTime, longTradingDay.getStartTime());
        assertEquals(endTime, longTradingDay.getEndTime());
        assertEquals(fallback.getDayById(holidayDayId).getResetTime(), longTradingDay.getResetTime());
        assertFalse(longTradingDay.isHoliday());
        assertTrue(longTradingDay.isTrading());

        int n = 0;
        for (Session session : fallback.getDayById(holidayDayId).getSessions()) {
            Session s = longTradingDay.getSessions().get(n++);
            assertEquals(session.getType(), s.getType());
            assertEquals(session.getStartTime(), s.getStartTime());
            assertEquals(session.getEndTime(), s.getEndTime());
        }
        for (int dayId = holidayDayId + 1; dayId < tradingDayId; dayId++) {
            Day day = fallback.getDayById(dayId);
            Session s = longTradingDay.getSessions().get(n++);
            assertEquals(SessionType.NO_TRADING, s.getType());
            assertEquals(day.getStartTime(), s.getStartTime());
            assertEquals(day.getEndTime(), s.getEndTime());
        }
        for (Session session : fallback.getDayById(tradingDayId).getSessions()) {
            Session s = longTradingDay.getSessions().get(n++);
            assertEquals(session.getType(), s.getType());
            assertEquals(session.getStartTime(), s.getStartTime());
            assertEquals(session.getEndTime(), s.getEndTime());
        }
        assertEquals(longTradingDay.getSessions().size(), n);

        for (int dayId = holidayDayId; dayId < tradingDayId; dayId++) {
            Day day = schedule.getDayById(dayId);
            assertEquals(startTime, day.getStartTime());
            assertEquals(startTime, day.getEndTime());
            assertEquals(1, day.getSessions().size());
            Session session = day.getSessions().get(0);
            assertEquals(SessionType.NO_TRADING, session.getType());
            assertEquals(startTime, session.getStartTime());
            assertEquals(startTime, session.getEndTime());
        }
    }

    // check that two schedules produce same days for specified day range; other schedule is tested in reverse order
    private void checkSame(String message, Schedule schedule, Schedule other, int startYmd, int endYmd) {
        List<Day> days = getDays(schedule, startYmd, endYmd);
        checkContinuity(message, days);
        checkSame(message, days, other);
    }

    // returns days from startYmd to endYmd
    private List<Day> getDays(Schedule schedule, int startYmd, int endYmd) {
        int startDayId = DayUtil.getDayIdByYearMonthDay(startYmd);
        int endDayId = DayUtil.getDayIdByYearMonthDay(endYmd);
        List<Day> days = new ArrayList<>();
        for (int dayId = startDayId; dayId <= endDayId; dayId++)
            days.add(schedule.getDayById(dayId));
        return days;
    }

    // check dayId and time continuity among specified days
    private void checkContinuity(String message, List<Day> days) {
        checkContinuity(message, days.get(0));
        for (int i = 1; i < days.size(); i++) {
            Day prev = days.get(i - 1);
            Day cur = days.get(i);
            checkContinuity(message, cur);
            if (cur.getDayId() != prev.getDayId() + 1)
                fail(message + " has a dayId gap or crossing between " + prev + " and " + cur);
            if (cur.getStartTime() != prev.getEndTime())
                fail(message + " has a time gap or crossing between " + prev + " and " + cur);
        }
    }

    // check time continuity among day sessions
    private void checkContinuity(String message, Day day) {
        if (day.getYearMonthDay() != DayUtil.getYearMonthDayByDayId(day.getDayId()))
            fail(message + " has " + day + " must have " + DayUtil.getYearMonthDayByDayId(day.getDayId()));
        Session firstSession = day.getSessions().get(0);
        if (day.getStartTime() != firstSession.getStartTime())
            fail(message + " has " + day + " with start time different from " + firstSession);
        Session lastSession = day.getSessions().get(day.getSessions().size() - 1);
        if (day.getEndTime() != lastSession.getEndTime())
            fail(message + " has " + day + " with end time different from " + lastSession);
        for (int i = 1; i < day.getSessions().size(); i++) {
            Session prev = day.getSessions().get(i - 1);
            Session cur = day.getSessions().get(i);
            if (cur.getStartTime() != prev.getEndTime())
                fail(message + " has " + day + " with a time hole or crossing between " + prev + " and " + cur);
        }
    }

    // compare specified days to same days taken in reverse order from specified separate schedule instance
    private void checkSame(String message, List<Day> days, Schedule schedule) {
        for (int i = days.size() - 1; i >= 0; i--) {
            Day day = days.get(i);
            Day other = schedule.getDayById(day.getDayId());
            checkSame(message, day, other);
        }
    }

    // checks that given days have same values for all parameters and sessions; works for separate schedule instances
    private void checkSame(String message, Day a, Day b) {
        if (a.getDayId() != b.getDayId() ||
            a.getYearMonthDay() != b.getYearMonthDay() ||
            a.isHoliday() != b.isHoliday() ||
            a.isShortDay() != b.isShortDay() ||
            a.getResetTime() != b.getResetTime() ||
            a.isTrading() != b.isTrading() ||
            a.getStartTime() != b.getStartTime() ||
            a.getEndTime() != b.getEndTime() ||
            a.getSessions().size() != b.getSessions().size()
        ) {
            fail(message + " has " + a + " different from alternative " + b);
        }
        for (int i = 0; i < a.getSessions().size(); i++) {
            Session as = a.getSessions().get(i);
            Session bs = b.getSessions().get(i);
            if (as.getType() != bs.getType() ||
                as.getStartTime() != bs.getStartTime() || as.getEndTime() != bs.getEndTime())
            {
                fail(message + " has " + a + " different from alternative " + b + " for session " + as + " vs " + bs);
            }
        }
    }

    private void checkSessions(String extra, int day, Map<Integer, String> map) {
        checkSessions(extra, day, map, false);
    }

    private void checkSessions(String extra, int day, Map<Integer, String> map, boolean extraSession) {
        LocalDate date = LocalDate.of(day / 10_000, (day % 10_000) / 100, day % 100);
        List<Session> sessions = Schedule.getInstance(gmtde(extra)).getDayByYearMonthDay(day).getSessions();
        for (Map.Entry<Integer, String> entry : map.entrySet()) {
            Session session = sessions.get(entry.getKey());
            long startTime = toMillis(date, entry.getValue().substring(0, 4), session.getStartTime());
            long endTime = toMillis(date, entry.getValue().substring(4), session.getEndTime());
            assertEquals(session.getStartTime(), startTime);
            assertEquals(session.getEndTime(),  endTime);
        }
        if (extraSession)
            assertEquals(sessions.get(sessions.size() - 1).getType(), SessionType.NO_TRADING);
        String nonShortExtra = Arrays.stream(extra.split(";"))
            .filter(s -> !s.startsWith("sds=")).collect(Collectors.joining(";"));
        Schedule nonShortSchedule = Schedule.getInstance(gmtde(nonShortExtra));
        List<Session> nonShortSessions = nonShortSchedule.getDayByYearMonthDay(day).getSessions();
        int size = extraSession ? nonShortSessions.size() + 1 : nonShortSessions.size();
        assertEquals(size, sessions.size());
        assertEquals(nonShortSessions.get(0).getStartTime(), sessions.get(0).getStartTime());
        long nonShortEndTime = nonShortSessions.get(nonShortSessions.size() - 1).getEndTime();
        long endTime = sessions.get(sessions.size() - 1).getEndTime();
        assertEquals(nonShortEndTime, endTime);
    }

    private String gmtde(String extra) {
        return "(tz=GMT;de=+0000;" + extra + ")";
    }

    private long toMillis(LocalDate date, String sTime, long datetime) {
        Instant instant = Instant.ofEpochMilli(datetime);
        if (ZonedDateTime.ofInstant(instant, ZoneId.of("GMT")).toLocalDate().compareTo(date) > 0)
            date = date.plusDays(1);
        int time = Integer.parseInt(sTime);
        ZonedDateTime zdt = ZonedDateTime.of(date, LocalTime.of(time / 100, time % 100), ZoneId.of("GMT"));
        return zdt.toInstant().toEpochMilli();
    }

    private void checkException(String def, String message) {
        try {
            Schedule.getInstance(def);
            fail("expected to get an IllegalArgumentException: " + message + def);
        } catch (IllegalArgumentException t) {
            assertEquals(message + def, t.getMessage());
        }
    }

    private Schedule gmt(String extra) {
        return Schedule.getInstance("(tz=GMT;" + extra + ")");
    }

    private void check(String extra, String times, String types) {
        List<Session> sessions = gmt(extra).getDayById(42).getSessions();
        String[] timeArray = times.split(",");
        String[] typeArray = types.split(",");
        assertEquals(timeArray.length, sessions.size());
        assertEquals(typeArray.length, sessions.size());
        for (int i = 0; i < sessions.size(); i++) {
            Session s = sessions.get(i);
            long time = Integer.parseInt(timeArray[i]);
            time = time / 10000 * HOUR + time / 100 % 100 * MINUTE + time % 100 * SECOND;
            assertEquals(time, s.getStartTime() % DAY);
            assertEquals(SessionType.valueOf(typeArray[i]), s.getType());
        }
    }
}
