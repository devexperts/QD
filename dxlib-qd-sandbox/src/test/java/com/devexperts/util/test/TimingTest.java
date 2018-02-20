/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2018 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.util.test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.devexperts.util.Timing;
import junit.framework.TestCase;

public class TimingTest extends TestCase {
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
        GMT(TimeZone.getTimeZone("GMT"), Timing.GMT),
        CST(TimeZone.getTimeZone("America/Chicago"), Timing.CST),
        EST(TimeZone.getTimeZone("America/New_York"), Timing.EST);

        // Note: Moscow timezone will not pass the test, since it is buggy on days 8122-8123 transition
        //MSK(TimeZone.getTimeZone("Europe/Moscow"), new Timing(TimeZone.getTimeZone("Europe/Moscow")));

        TimeZone timezone;
        Timing timing;

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
        final int day_id;
        final long day_start;
        final long day_end;
        final int year_month_day_number;

        protected Operation() {
            ti = TestInstance.random();
            day_id = randomDayId();

            long utc_time = (long) day_id * MS_IN_DAY;
            Calendar utc_cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utc_cal.setTimeInMillis(utc_time);
            year_month_day_number =
                10000 * utc_cal.get(Calendar.YEAR) +
                100 * (utc_cal.get(Calendar.MONTH) + 1) +
                utc_cal.get(Calendar.DAY_OF_MONTH);

            Calendar cal = Calendar.getInstance(ti.timezone);
            cal.set(Calendar.YEAR, utc_cal.get(Calendar.YEAR));
            cal.set(Calendar.MONTH, utc_cal.get(Calendar.MONTH));
            cal.set(Calendar.DAY_OF_MONTH, utc_cal.get(Calendar.DAY_OF_MONTH));
            cal.set(Calendar.HOUR_OF_DAY, cal.getActualMinimum(Calendar.HOUR_OF_DAY));
            cal.set(Calendar.MINUTE, cal.getActualMinimum(Calendar.MINUTE));
            cal.set(Calendar.SECOND, cal.getActualMinimum(Calendar.SECOND));
            cal.set(Calendar.MILLISECOND, cal.getActualMinimum(Calendar.MILLISECOND));
            day_start = cal.getTimeInMillis();
            cal.set(Calendar.HOUR_OF_DAY, cal.getActualMaximum(Calendar.HOUR_OF_DAY));
            cal.set(Calendar.MINUTE, cal.getActualMaximum(Calendar.MINUTE));
            cal.set(Calendar.SECOND, cal.getActualMaximum(Calendar.SECOND));
            cal.set(Calendar.MILLISECOND, cal.getActualMaximum(Calendar.MILLISECOND));
            day_end = cal.getTimeInMillis();
            time = day_start + r.nextInt((int) (day_end - day_start + 1));
        }

        abstract Timing.Day invoke();

        String getName() {
            String cn = getClass().toString();
            return cn.substring(cn.lastIndexOf('$') + 1) + ": day=" + day_id + " (" + year_month_day_number + "), " +
                "[start=" + day_start + ", cur=" + time + ", end=" + day_end + "], " +
                "tz=" + ti.timezone.getID();
        }

        void verify(Timing.Day day) {
            if (day_id != day.day_id || day_start != day.day_start || day_end != day.day_end || year_month_day_number != day.year_month_day_number)
                fail(getName());
        }

        void run() {
            verify(invoke());
        }
    }

    private static class GetById extends Operation {
        GetById() {}

        Timing.Day invoke() {
            return ti.timing.getById(day_id);
        }
    }

    private static class GetByYmd extends Operation {
        GetByYmd() {}

        Timing.Day invoke() {
            return ti.timing.getByYmd(year_month_day_number);
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

    public void testContinuity() {
        for (TestInstance ti : TestInstance.values()) {
            Timing timing = ti.timing;
            Timing.Day prev = timing.getById(-1);
            for (int day_id = 0; day_id < 30000; day_id++) {
                Timing.Day cur = timing.getById(day_id);
                if (cur.day_start != prev.day_end + 1)
                    fail(ti + ": " + explain(prev) + ", " + explain(cur));
                prev = cur;
            }
        }
    }

    private static String explain(Timing.Day day) {
        return day + " [" + day.day_start + " - " + day.day_end + "]";
    }
}
