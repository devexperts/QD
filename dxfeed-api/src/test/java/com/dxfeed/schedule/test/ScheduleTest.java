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
package com.dxfeed.schedule.test;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.dxfeed.schedule.*;
import junit.framework.TestCase;

import static com.devexperts.util.TimeUtil.*;

public class ScheduleTest extends TestCase {
    public void testParsing() {
        assertTrue(gmt("0=").getDayById(42).getStartTime() % DAY == 0);
        assertTrue(gmt("de=2300;0=").getDayById(42).getStartTime() % DAY == 23 * HOUR);
        assertTrue(gmt("0=01000200").getDayById(42).getNextDay(DayFilter.NON_TRADING).getStartTime() % DAY == 0);
        assertTrue(gmt("0=01000200").getDayById(42).getNextDay(DayFilter.NON_TRADING).getSessions().size() == 1);
        assertTrue(gmt("0=01000200").getNearestSessionByTime(System.currentTimeMillis(), SessionFilter.TRADING).getStartTime() % DAY == 1 * HOUR);

        check("0=", "000000", "NO_TRADING");
        check("0=01000200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=0100/0200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=+-0100-+0200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=+-0100/-+0200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r01000200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r0100/0200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r+-0100-+0200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r+-0100/-+0200", "000000,010000,020000", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=010002000300040005000600", "000000,010000,020000,030000,040000,050000,060000", "NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING");
        check("0=0100/02000300/04000500/0600", "000000,010000,020000,030000,040000,050000,060000", "NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING");
        check("0=+-0100-+0200+-0300-+0400+-0500-+0600", "000000,010000,020000,030000,040000,050000,060000", "NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING");
        check("0=+-0100/-+0200+-0300/-+0400+-0500/-+0600", "000000,010000,020000,030000,040000,050000,060000", "NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING,REGULAR,NO_TRADING");
        check("0=p01000200r03000400a05000600", "000000,010000,020000,030000,040000,050000,060000", "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");
        check("0=p0100/0200r0300/0400a0500/0600", "000000,010000,020000,030000,040000,050000,060000", "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");
        check("0=p+-0100-+0200r+-0300-+0400a+-0500-+0600", "000000,010000,020000,030000,040000,050000,060000", "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");
        check("0=p+-0100/-+0200r+-0300/-+0400a+-0500/-+0600", "000000,010000,020000,030000,040000,050000,060000", "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");

        check("0=010203020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=010203/020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=+-010203-+020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=+-010203/-+020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r010203020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r010203/020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r+-010203-+020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=r+-010203/-+020406", "000000,010203,020406", "NO_TRADING,REGULAR,NO_TRADING");
        check("0=p010203020406r030507040608a050709060810", "000000,010203,020406,030507,040608,050709,060810", "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");
        check("0=p010203/020406r030507/040608a050709/060810", "000000,010203,020406,030507,040608,050709,060810", "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");
        check("0=p+-010203-+020406r+-030507-+040608a+-050709-+060810", "000000,010203,020406,030507,040608,050709,060810", "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");
        check("0=p+-010203/-+020406r+-030507/-+040608a+-050709/-+060810", "000000,010203,020406,030507,040608,050709,060810", "NO_TRADING,PRE_MARKET,NO_TRADING,REGULAR,NO_TRADING,AFTER_MARKET,NO_TRADING");

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

        checkException("sd=20191224;sds=0330;0=p07000930p09301600a16001900", "unknown short day strategy for");
        checkException("sd=20191224;sds=ec-0330;0=p07000930p09301600a16001900", "unknown short day strategy for");
        checkException("sd=20191224;sds=ec+0330;0=p07000930p09301600a16001900", "unknown short day strategy for");
        checkException("sd=20191224;sds=ec0j60;0=p07000930p09301600a16001900", "unknown short day strategy for");
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

    private void checkException(String extra, String message) {
        boolean thrown = false;
        try {
            gmt(extra);
        }
        catch (Throwable t) {
            if (t instanceof IllegalArgumentException)
                thrown = t.getMessage().contains(message);
            else
                throw t;
        }
        assertTrue(message, thrown);
    }

    private Schedule gmt(String extra) {
        return Schedule.getInstance("(tz=GMT;" + extra + ")");
    }

    private void check(String extra, String times, String types) {
        List<Session> sessions = gmt(extra).getDayById(42).getSessions();
        String[] timeArray = times.split(",");
        String[] typeArray = types.split(",");
        assertTrue(sessions.size() == timeArray.length);
        assertTrue(sessions.size() == typeArray.length);
        for (int i = 0; i < sessions.size(); i++) {
            Session s = sessions.get(i);
            long time = Integer.parseInt(timeArray[i]);
            time = time / 10000 * HOUR + time / 100 % 100 * MINUTE + time % 100 * SECOND;
            assertTrue(s.getStartTime() % DAY == time);
            assertTrue(s.getType() == SessionType.valueOf(typeArray[i]));
        }
    }

    public void testDefaults() throws IOException {
        int goodHoliday = 20170111;
        int badHoliday = 20170118;
        String def = "date=30000101-000000+0000\n\n";
        def += "hd.GOOD=\\\n" + goodHoliday + ",\\\n\n";
        Schedule.setDefaults(def.getBytes());
        for (int i = goodHoliday - 1; i <= goodHoliday + 1; i++)
            assertEquals(i == goodHoliday, Schedule.getInstance("(tz=GMT;0=;hd=GOOD)").getDayByYearMonthDay(i).isHoliday());
        def += "hd.BAD=\\\n" + badHoliday + ",\\";
        Schedule.setDefaults(def.getBytes());
        for (int i = badHoliday - 1; i <= badHoliday + 1; i++)
            assertEquals(i == badHoliday, Schedule.getInstance("(tz=GMT;0=;hd=BAD)").getDayByYearMonthDay(i).isHoliday());
    }
}
