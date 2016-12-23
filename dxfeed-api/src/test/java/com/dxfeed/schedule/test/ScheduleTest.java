/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.dxfeed.schedule.test;

import java.util.List;

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
}
