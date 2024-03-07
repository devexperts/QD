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
import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class TimeUtilTest {

    private static final ZoneId BERLIN_ZONE_ID = ZoneId.of("Europe/Berlin");
    private static final ZoneOffset SUMMER_OFFSET = ZoneOffset.of("+02");
    private static final ZoneOffset WINTER_OFFSET = ZoneOffset.of("+01");

    @Test
    public void testSimpleZones() {
        assertEquals("GMT", TimeUtil.getTimeZoneGmt().getID());
        assertEquals("UTC", TimeUtil.getTimeZone("UTC").getID());
        assertEquals("PST", TimeUtil.getTimeZone("PST").getID());
        assertEquals("EST", TimeUtil.getTimeZone("EST").getID());

        assertEquals("America/Chicago", TimeUtil.getTimeZone("America/Chicago").getID());
        assertEquals("America/New_York", TimeUtil.getTimeZone("America/New_York").getID());
    }

    @Test
    public void testOffsetZones() {
        assertEquals("GMT+00:00", TimeUtil.getTimeZone("GMT+00:00").getID());
        assertEquals("GMT-00:00", TimeUtil.getTimeZone("GMT-00:00").getID());
        assertEquals("GMT+06:00", TimeUtil.getTimeZone("GMT+06:00").getID());
        assertEquals("GMT-06:00", TimeUtil.getTimeZone("GMT-06:00").getID());
    }

    @Test
    public void testInvalidZones() {
        assertThrows(IllegalArgumentException.class, () -> TimeUtil.getTimeZone("Atlantis/Underwater_Town"));
        assertThrows(IllegalArgumentException.class, () -> TimeUtil.getTimeZone("GMT+6:00"));
        assertThrows(IllegalArgumentException.class, () -> TimeUtil.getTimeZone("Z"));
    }

    @Test
    public void testCheckDelay() {
        TimeZone defaultTimeZone = TimeZone.getDefault();
        Calendar calendar = Calendar.getInstance(defaultTimeZone);
        calendar.set(2023, Calendar.JUNE, 9);
        calendar.set(Calendar.HOUR_OF_DAY, 1);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 1);
        calendar.set(Calendar.MILLISECOND, 0);

        long currentTime = calendar.getTimeInMillis();
        IntStream.range(0, 24).forEach(hour ->
            IntStream.iterate(0, operand -> operand + 10)
                .limit(6)
                .forEach(minutes -> {
                    Calendar currentCalendar = (Calendar) calendar.clone();
                    currentCalendar.set(Calendar.HOUR_OF_DAY, hour);
                    currentCalendar.set(Calendar.MINUTE, minutes);
                    currentCalendar.set(Calendar.SECOND, 0);

                    if (currentCalendar.before(calendar)) {
                        currentCalendar.add(Calendar.DATE, 1);
                    }
                    long dailyTime1 = currentCalendar.getTimeInMillis();
                    try {
                        long dailyTime2 = TimeUtil.computeDailyTime(
                            currentTime, LocalTime.of(hour, minutes, 0), defaultTimeZone.toZoneId());
                        assertEquals(dailyTime1, dailyTime2);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
        );
    }

    @Test
    public void testWinterToSummerTimeShift() {
        ZonedDateTime zonedDateTime = ZonedDateTime.ofStrict(
            LocalDateTime.parse("2023-03-25T03:00:00"), WINTER_OFFSET, BERLIN_ZONE_ID);
        long epochMillis = zonedDateTime.toInstant().toEpochMilli();

        check(epochMillis, LocalTime.of(2, 15, 0), 23, 0);
        check(epochMillis, LocalTime.of(2, 20, 0), 23, 0);
        check(epochMillis, LocalTime.of(2, 50, 0), 23, 0);

        zonedDateTime = ZonedDateTime.ofStrict(
            LocalDateTime.parse("2023-03-26T00:00:00"), WINTER_OFFSET, BERLIN_ZONE_ID);
        epochMillis = zonedDateTime.toInstant().toEpochMilli();

        check(epochMillis, LocalTime.of(0, 0, 0), 23, 0);
        check(epochMillis, LocalTime.of(1, 0, 0), 1, 0);
        check(epochMillis, LocalTime.of(2, 0, 0), 2, 0);

        // absent hour
        check(epochMillis, LocalTime.of(2, 15, 0), 2, 0);
        check(epochMillis, LocalTime.of(2, 30, 0), 2, 0);
        check(epochMillis, LocalTime.of(2, 45, 0), 2, 0);

        // switch to summer time
        check(epochMillis, LocalTime.of(3, 0, 0), 2, 0);
        check(epochMillis, LocalTime.of(4, 15, 0), 3, 15);
        check(epochMillis, LocalTime.of(23, 15, 0), 22, 15);

        zonedDateTime = ZonedDateTime.ofStrict(
            LocalDateTime.parse("2023-03-26T03:00:00"), SUMMER_OFFSET, BERLIN_ZONE_ID);
        epochMillis = zonedDateTime.toInstant().toEpochMilli();

        check(epochMillis, LocalTime.of(2, 0, 0), 23, 0);
        check(epochMillis, LocalTime.of(2, 15, 0), 23, 15);
        check(epochMillis, LocalTime.of(2, 30, 0), 23, 30);
        check(epochMillis, LocalTime.of(2, 45, 0), 23, 45);

        zonedDateTime = ZonedDateTime.ofStrict(
            LocalDateTime.parse("2023-03-26T03:30:00"), SUMMER_OFFSET, BERLIN_ZONE_ID);
        epochMillis = zonedDateTime.toInstant().toEpochMilli();

        check(epochMillis, LocalTime.of(2, 15, 0), 22, 45);
    }

    @Test
    public void testSummerToWinterTimeShift() {
        ZonedDateTime zonedDateTime = ZonedDateTime.ofStrict(
            LocalDateTime.parse("2023-10-29T00:00:00"), SUMMER_OFFSET, BERLIN_ZONE_ID);
        long epochMillis = zonedDateTime.toInstant().toEpochMilli();

        check(epochMillis, LocalTime.of(0, 0, 0), 25, 0);
        check(epochMillis, LocalTime.of(1, 0, 0), 1, 0);
        check(epochMillis, LocalTime.of(2, 0, 0), 2, 0);

        // additional hour
        check(epochMillis, LocalTime.of(2, 15, 0), 2, 15);
        check(epochMillis, LocalTime.of(2, 30, 0), 2, 30);
        check(epochMillis, LocalTime.of(2, 45, 0), 2, 45);

        // switch to winter time
        check(epochMillis, LocalTime.of(3, 0, 0), 4, 0);
        check(epochMillis, LocalTime.of(4, 0, 0), 5, 0);
        check(epochMillis, LocalTime.of(23, 15, 0), 24, 15);

        zonedDateTime = ZonedDateTime.ofStrict(
            LocalDateTime.parse("2023-10-29T02:00:00"), SUMMER_OFFSET, BERLIN_ZONE_ID);
        epochMillis = zonedDateTime.toInstant().toEpochMilli();

        check(epochMillis, LocalTime.of(2, 0, 0), 25, 0);
        check(epochMillis, LocalTime.of(2, 15, 0), 0, 15);
        check(epochMillis, LocalTime.of(2, 30, 0), 0, 30);
        check(epochMillis, LocalTime.of(2, 45, 0), 0, 45);

        zonedDateTime = ZonedDateTime.ofStrict(
            LocalDateTime.parse("2023-10-29T02:30:00"), SUMMER_OFFSET, BERLIN_ZONE_ID);
        epochMillis = zonedDateTime.toInstant().toEpochMilli();

        check(epochMillis, LocalTime.of(2, 15, 0), 24, 45);
        check(epochMillis, LocalTime.of(2, 30, 0), 25, 0);

        zonedDateTime = ZonedDateTime.ofStrict(
            LocalDateTime.parse("2023-10-29T02:00:00"), WINTER_OFFSET, BERLIN_ZONE_ID);
        epochMillis = zonedDateTime.toInstant().toEpochMilli();

        check(epochMillis, LocalTime.of(2, 0, 0), 24, 0);
        check(epochMillis, LocalTime.of(2, 15, 0), 24, 15);
        check(epochMillis, LocalTime.of(2, 30, 0), 24, 30);
        check(epochMillis, LocalTime.of(2, 45, 0), 24, 45);
    }

    private void check(long timeMiles, LocalTime localTime, long hours, long minutes) {
        long dailyTime = TimeUtil.computeDailyTime(timeMiles, localTime, BERLIN_ZONE_ID);
        long actualMinutes =
            TimeUnit.MILLISECONDS.toMinutes(Instant.ofEpochMilli(dailyTime).toEpochMilli() - timeMiles);
        assertEquals(TimeUnit.HOURS.toMinutes(hours) + minutes, actualMinutes);
    }

    @Test
    public void testComputeDaily() {
        LocalTime _00_00 = LocalTime.of(0, 0, 0);
        LocalTime _01_30 = LocalTime.of(1, 30, 0);
        LocalTime _02_00 = LocalTime.of(2, 0, 0);
        LocalTime _02_01 = LocalTime.of(2, 1, 0);
        LocalTime _02_59 = LocalTime.of(2, 59, 0);
        LocalTime _03_00 = LocalTime.of(3, 0, 0);
        LocalTime _08_30 = LocalTime.of(8, 30, 0);
        LocalTime _23_59 = LocalTime.of(23, 59, 0);

        // winter time, no shift
        checkComputeDaily(_00_00, "2023-01-09 00:00", "2023-01-10 00:00", "2023-01-11 00:00", "2023-01-12 00:00");
        checkComputeDaily(_01_30, "2023-01-09 01:30", "2023-01-10 01:30", "2023-01-11 01:30", "2023-01-12 01:30");
        checkComputeDaily(_02_00, "2023-01-09 02:00", "2023-01-10 02:00", "2023-01-11 02:00", "2023-01-12 02:00");
        checkComputeDaily(_02_01, "2023-01-09 02:01", "2023-01-10 02:01", "2023-01-11 02:01", "2023-01-12 02:01");
        checkComputeDaily(_02_59, "2023-01-09 02:59", "2023-01-10 02:59", "2023-01-11 02:59", "2023-01-12 02:59");
        checkComputeDaily(_03_00, "2023-01-09 03:00", "2023-01-10 03:00", "2023-01-11 03:00", "2023-01-12 03:00");
        checkComputeDaily(_08_30, "2023-01-09 08:30", "2023-01-10 08:30", "2023-01-11 08:30", "2023-01-12 08:30");
        checkComputeDaily(_23_59, "2023-01-09 23:59", "2023-01-10 23:59", "2023-01-11 23:59", "2023-01-12 23:59");

        // winter-to-summer time shift - forward jump from 02:00 to 03:00 on 2023-03-26
        checkComputeDaily(_00_00, "2023-03-24 00:00", "2023-03-25 00:00", "2023-03-26 00:00", "2023-03-27 00:00");
        checkComputeDaily(_01_30, "2023-03-24 01:30", "2023-03-25 01:30", "2023-03-26 01:30", "2023-03-27 01:30");
        // 4 tests below are "inside" forward jump on 2023-03-26 thus execute at 02:00 as a moment of jump
        checkComputeDaily(_02_00, "2023-03-24 02:00", "2023-03-25 02:00", "2023-03-26 02:00", "2023-03-27 02:00");
        checkComputeDaily(_02_01, "2023-03-24 02:01", "2023-03-25 02:01", "2023-03-26 02:00", "2023-03-27 02:01");
        checkComputeDaily(_02_59, "2023-03-24 02:59", "2023-03-25 02:59", "2023-03-26 02:00", "2023-03-27 02:59");
        checkComputeDaily(_03_00, "2023-03-24 03:00", "2023-03-25 03:00", "2023-03-26 02:00", "2023-03-27 03:00");
        // 2 tests below happen after forward jump on 2023-03-26 thus execute "1 hour earlier"
        checkComputeDaily(_08_30, "2023-03-24 08:30", "2023-03-25 08:30", "2023-03-26 07:30", "2023-03-27 08:30");
        checkComputeDaily(_23_59, "2023-03-24 23:59", "2023-03-25 23:59", "2023-03-26 22:59", "2023-03-27 23:59");

        // summer time, no shift
        checkComputeDaily(_00_00, "2023-07-09 00:00", "2023-07-10 00:00", "2023-07-11 00:00", "2023-07-12 00:00");
        checkComputeDaily(_01_30, "2023-07-09 01:30", "2023-07-10 01:30", "2023-07-11 01:30", "2023-07-12 01:30");
        checkComputeDaily(_02_00, "2023-07-09 02:00", "2023-07-10 02:00", "2023-07-11 02:00", "2023-07-12 02:00");
        checkComputeDaily(_02_01, "2023-07-09 02:01", "2023-07-10 02:01", "2023-07-11 02:01", "2023-07-12 02:01");
        checkComputeDaily(_02_59, "2023-07-09 02:59", "2023-07-10 02:59", "2023-07-11 02:59", "2023-07-12 02:59");
        checkComputeDaily(_03_00, "2023-07-09 03:00", "2023-07-10 03:00", "2023-07-11 03:00", "2023-07-12 03:00");
        checkComputeDaily(_08_30, "2023-07-09 08:30", "2023-07-10 08:30", "2023-07-11 08:30", "2023-07-12 08:30");
        checkComputeDaily(_23_59, "2023-07-09 23:59", "2023-07-10 23:59", "2023-07-11 23:59", "2023-07-12 23:59");

        // summer-to-winter time shift - backward jump from 03:00 to 02:00 on 2023-10-29
        checkComputeDaily(_00_00, "2023-10-27 00:00", "2023-10-28 00:00", "2023-10-29 00:00", "2023-10-30 00:00");
        checkComputeDaily(_01_30, "2023-10-27 01:30", "2023-10-28 01:30", "2023-10-29 01:30", "2023-10-30 01:30");
        // 3 tests below are "inside" backward jump on 2023-10-29 thus execute during first pass of the jump period
        checkComputeDaily(_02_00, "2023-10-27 02:00", "2023-10-28 02:00", "2023-10-29 02:00", "2023-10-30 02:00");
        checkComputeDaily(_02_01, "2023-10-27 02:01", "2023-10-28 02:01", "2023-10-29 02:01", "2023-10-30 02:01");
        checkComputeDaily(_02_59, "2023-10-27 02:59", "2023-10-28 02:59", "2023-10-29 02:59", "2023-10-30 02:59");
        // 1 test below is tricky - time 03:00 is considered "after backward jump" thus execute "1 hour later"
        checkComputeDaily(_03_00, "2023-10-27 03:00", "2023-10-28 03:00", "2023-10-29 04:00", "2023-10-30 03:00");
        // 2 tests below happen after backward jump on 2023-10-29 thus execute "1 hour later"
        checkComputeDaily(_08_30, "2023-10-27 08:30", "2023-10-28 08:30", "2023-10-29 09:30", "2023-10-30 08:30");
        checkComputeDaily(_23_59, "2023-10-27 23:59", "2023-10-28 23:59", "2023-10-29 24:59", "2023-10-30 23:59");
    }

    private void checkComputeDaily(LocalTime localTime, String... points) {
        // "points" are valid daily points that shall be computed for times between them
        // the hour-minute part of point is absolute offset from that day midnight
        long[] times = Arrays.stream(points).mapToLong(TimeUtilTest::parsePoint).toArray();
        for (int i = 1; i < times.length; i++) {
            long delta = times[i] - times[i - 1];
            assertTrue("bad test points", delta >= 23 * 3600_000 && delta <= 25 * 3600_000);
            for (long currentTime = times[i - 1]; currentTime < times[i]; currentTime += 600_000) {
                long computed = TimeUtil.computeDailyTime(currentTime, localTime, BERLIN_ZONE_ID);
                if (computed != times[i]) {
                    assertEquals("computeDailyTime(" + formatTime(currentTime) + ", " + localTime + ")",
                        formatTime(times[i]), formatTime(computed));
                }
            }
        }
    }

    private static long parsePoint(String point) {
        assertTrue("bad point format " + point, point.matches("[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}"));
        int[] v = Arrays.stream(point.split("[^0-9]")).mapToInt(Integer::parseInt).toArray();
        ZonedDateTime z = ZonedDateTime.of(v[0], v[1], v[2], 0, 0, 0, 0, BERLIN_ZONE_ID);
        return (z.toEpochSecond() + v[3] * 3600 + v[4] * 60) * 1000;
    }

    private static String formatTime(long time) {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), BERLIN_ZONE_ID).toString();
    }
}
