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
package com.devexperts.util.test;

import com.devexperts.util.TimeUtil;
import com.devexperts.util.Timing;
import org.junit.Test;

import java.util.Calendar;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class TimingTest {
    private static final int MS_IN_DAY = 24 * 60 * 60000;
    private static final int OPERATIONS = 50000; // pre-generated random ops

    private static final int START_DAY_ID = 7305; // 1990-01-01
    private static final int END_DAY_ID = 14245; // 2009-01-01

    private static final int THREADS = 40;
    private static final long TIME_LIMIT = 500; // 500 msec
    private static final int PHASES = 3; // run 3 phases

    private static final Random r = new Random(20080918);

    private static int randomDayId() {
        // more recent -- more common;
        int days = END_DAY_ID - START_DAY_ID + 1;
        return END_DAY_ID - (int) Math.sqrt(r.nextInt(days * days));
    }

    private enum TestInstance {
        GMT(TimeUtil.getTimeZoneGmt(), Timing.GMT),
        CST(TimeUtil.getTimeZone("America/Chicago"), Timing.CST),
        EST(TimeUtil.getTimeZone("America/New_York"), Timing.EST),
        CET(TimeUtil.getTimeZone("Europe/Brussels"), new Timing(TimeUtil.getTimeZone("Europe/Brussels"))),
        MSK(TimeUtil.getTimeZone("Europe/Moscow"), new Timing(TimeUtil.getTimeZone("Europe/Moscow"))),
        CUSTOM1(TimeUtil.getTimeZone("GMT+08:00"), new Timing(TimeUtil.getTimeZone("GMT+08:00"))),
        CUSTOM2(TimeUtil.getTimeZone("GMT-08:00"), new Timing(TimeUtil.getTimeZone("GMT-08:00"))),
        ;

        final TimeZone timezone;
        final Timing timing;

        private TestInstance(TimeZone timezone, Timing timing) {
            this.timezone = timezone;
            this.timing = timing;
        }

        public static TestInstance random() {
            TestInstance[] vals = values();
            return vals[r.nextInt(vals.length)];
        }
    }

    private abstract static class Operation {
        final TestInstance ti;

        final long time;
        final int dayId;
        final long dayStart;
        final long dayEnd;
        final int yearMonthDayNumber;

        protected Operation() {
            ti = TestInstance.random();
            dayId = randomDayId();

            long utcTime = (long) dayId * MS_IN_DAY;
            Calendar utcCalendar = Calendar.getInstance(TimeUtil.getTimeZoneGmt());
            utcCalendar.setTimeInMillis(utcTime);
            yearMonthDayNumber =
                10000 * utcCalendar.get(Calendar.YEAR) +
                100 * (utcCalendar.get(Calendar.MONTH) + 1) +
                utcCalendar.get(Calendar.DAY_OF_MONTH);

            Calendar cal = Calendar.getInstance(ti.timezone);
            cal.set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR));
            cal.set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH));
            cal.set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH));
            cal.set(Calendar.HOUR_OF_DAY, cal.getActualMinimum(Calendar.HOUR_OF_DAY));
            cal.set(Calendar.MINUTE, cal.getActualMinimum(Calendar.MINUTE));
            cal.set(Calendar.SECOND, cal.getActualMinimum(Calendar.SECOND));
            cal.set(Calendar.MILLISECOND, cal.getActualMinimum(Calendar.MILLISECOND));
            dayStart = cal.getTimeInMillis();
            cal.set(Calendar.HOUR_OF_DAY, cal.getActualMaximum(Calendar.HOUR_OF_DAY));
            cal.set(Calendar.MINUTE, cal.getActualMaximum(Calendar.MINUTE));
            cal.set(Calendar.SECOND, cal.getActualMaximum(Calendar.SECOND));
            cal.set(Calendar.MILLISECOND, cal.getActualMaximum(Calendar.MILLISECOND));
            dayEnd = cal.getTimeInMillis();
            time = dayStart + r.nextInt((int) (dayEnd - dayStart + 1));
        }

        abstract Timing.Day invoke();

        String getName() {
            String cn = getClass().toString();
            return cn.substring(cn.lastIndexOf('$') + 1) + ": day=" + dayId + " (" + yearMonthDayNumber + "), " +
                "[start=" + dayStart + ", cur=" + time + ", end=" + dayEnd + "], " +
                "tz=" + ti.timezone.getID();
        }

        void verify(Timing.Day day) {
            assertEquals(getName(), dayId, day.day_id);
            assertEquals(getName(), dayStart, day.day_start);
            assertEquals(getName(), dayEnd, day.day_end);
            assertEquals(getName(), yearMonthDayNumber, day.year_month_day_number);
        }

        void run() {
            verify(invoke());
        }
    }

    private static class GetById extends Operation {
        GetById() {}

        Timing.Day invoke() {
            return ti.timing.getById(dayId);
        }
    }

    private static class GetByYmd extends Operation {
        GetByYmd() {}

        Timing.Day invoke() {
            return ti.timing.getByYmd(yearMonthDayNumber);
        }
    }

    private static class GetByTime extends Operation {
        GetByTime() {}

        Timing.Day invoke() {
            return ti.timing.getByTime(time);
        }
    }

    // ----------------------------------------------------------------------------

    private final Operation[] OP = new Operation[OPERATIONS];
    private final CyclicBarrier barrier = new CyclicBarrier(THREADS + 1);
    private volatile boolean done;
    private volatile boolean failed;
    private AtomicInteger counter;

    private class Worker extends Thread {
        int index;
        int shift;

        Worker() {
            index = r.nextInt(OPERATIONS);
            shift = r.nextBoolean() ? 3 : 1;
            if (r.nextBoolean())
                shift = OPERATIONS - shift;
        }

        public void run() {
            try {
                for (int phase = 0; phase < PHASES; phase++) {
                    barrier.await();
                    while (!done) {
                        OP[index].run();
                        index = (index + shift) % OPERATIONS;
                        counter.incrementAndGet();
                    }
                    barrier.await();
                }
            } catch (Throwable t) {
                failed = true;
                t.printStackTrace();
            }
        }
    }

    @Test
    public void testMutltiThread() throws BrokenBarrierException, InterruptedException, TimeoutException {
        // pre-initialize random ops and test them single-threaded
        for (int i = 0; i < OPERATIONS; i++) {
            int op = r.nextInt(3);
            OP[i] = op == 0 ? new GetById() : op == 1 ? new GetByYmd() : new GetByTime();
            OP[i].run();
        }
        // initialize
        for (int i = 0; i < THREADS; i++)
            new Worker().start();
        // go!
        for (int phase = 0; phase < PHASES && !failed; phase++) {
            failed = false;
            done = false;
            counter = new AtomicInteger(0);
            barrier.await();
            Thread.sleep(TIME_LIMIT);
            done = true;
            barrier.await(2 * TIME_LIMIT, TimeUnit.MILLISECONDS);
            System.out.println("Executed " + (counter.get() * 1000L / TIME_LIMIT) + " operations per second");
        }
        for (TestInstance ti : TestInstance.values())
            System.out.println(" --- " + ti.timezone.getID() + ": " + ti.timing.getStatistics());
        if (failed)
            fail("Test failed");
    }

    @Test
    public void testContinuity() {
        for (TestInstance ti : TestInstance.values()) {
            Timing timing = ti.timing;
            Timing.Day prev = timing.getById(-1);
            for (int dayId = 0; dayId < 30000; dayId++) {
                Timing.Day cur = timing.getById(dayId);
                if (cur.day_start != prev.day_end + 1)
                    fail(ti + ": " + explain(prev) + ", " + explain(cur));
                prev = cur;
            }
        }
    }

    @Test
    public void testZoneId() {
        for (TestInstance ti : TestInstance.values()) {
            Timing timing = ti.timing;
            TimeZone timezone = ti.timezone;
            assertEquals(timezone.toZoneId(), timing.getZoneId());
        }
    }

    private static String explain(Timing.Day day) {
        return day + " [" + day.day_start + " - " + day.day_end + "]";
    }
}
