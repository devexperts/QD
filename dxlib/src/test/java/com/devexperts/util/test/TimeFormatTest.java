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

import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimePeriod;
import com.devexperts.util.TimeUtil;
import org.junit.Test;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TimeFormatTest {

    private final TimeFormat GMT = TimeFormat.GMT;
    private final TimeFormat MSK = TimeFormat.getInstance(TimeUtil.getTimeZone("GMT+03:00"));

    @Test
    public void testZoneStability() {
        // try to parse in GMT a time with an explicit time-zone
        assertEquals(1197450000000L, GMT.parse("2007-12-12T12+0300").getTime());
        // make sure it does not break the default formatting of GMT timezone
        assertEquals("20140618-130218+0000", GMT.withTimeZone().format(new Date(1403096538000L)));
    }

    @Test
    public void testTimePeriods() throws InvalidFormatException {
        assertEquals(0L, TimePeriod.valueOf("0").getTime());
        assertEquals(1000L, TimePeriod.valueOf("1s").getTime());
        assertEquals(123L, TimePeriod.valueOf(".123456789").getTime());
        assertEquals("PT1.235S", TimePeriod.valueOf("1.23456789").toString());
        assertEquals(TimePeriod.valueOf("1.23456789"), TimePeriod.valueOf("0d0h0m1.235s"));
    }

    @Test
    public void testEqualTimePeriods() throws InvalidFormatException {
        ArrayList<TimePeriod> equalPeriods = new ArrayList<>();

        equalPeriods.add(TimePeriod.valueOf(((10L * 24 + 2) * 60 + 30) * 60 * 1000));
        equalPeriods.add(TimePeriod.valueOf(Long.valueOf(((10L * 24 + 2) * 60 + 30) * 60).toString()));
        equalPeriods.add(TimePeriod.valueOf("P10DT2H30M"));
        equalPeriods.add(TimePeriod.valueOf("10DT2H29M60.00"));
        equalPeriods.add(TimePeriod.valueOf("p10DT1H90M"));
        equalPeriods.add(TimePeriod.valueOf("9DT26H1800S"));
        equalPeriods.add(TimePeriod.valueOf("P10DT2H30M.0"));
        equalPeriods.add(TimePeriod.valueOf("p10d2H29m59.9995s"));

        assertPeriodsAreEqual(equalPeriods, "P10DT2H30M0S");
    }

    @Test
    public void testBadTimePeriods() {
        String[] badValues = {
            "t1d",
            "p",
            "",
            "P2D3T",
            "P10DT2H30MS",
            ".",
            "p1mt",
            "239e-3",
            " PT1S",
            "pt1s2m",
            "PT1s ",
            "239ss",
            "t1,5s",
            "1,5",
        };

        for (String s : badValues) {
            try {
                TimePeriod.valueOf(s);
                fail("Parsed bad value: " + s);
            } catch (InvalidFormatException expected) {
            }
        }
    }

    @Test
    public void testGoodTimePeriods() {
        String[] goodValues = {
            "P1234DT12H30M0S",
            "p1",
            "t239",
            "PT0S",
            "1.5",
            "436243.2346235275477676256255256",
            "1m",
            "2h",
            "3d",
            "1h2s",
            "p1dt",
        };

        for (String s : goodValues) {
            try {
                TimePeriod.valueOf(s);
            } catch (InvalidFormatException e) {
                fail("Couldn't parse: " + s);
            }
        }
    }

    // WARNING: Following tests will pass only when running in MST time zone.
    @Test
    public void testEqualDateTimes1() {
        assertEqual(GMT.parse("20071114-170523"), "20071114-170523+0000", GMT);
        assertEqual(GMT.parse("2007-11-14 17:05:23"), "20071114-170523+0000", GMT);
        assertEqual(MSK.parse("20071114-200523+0300"), "20071114-170523+0000", GMT);
        assertEqual(MSK.parse("20071114-170523 GMT"), "20071114-170523+0000", GMT);
        assertEqual(GMT.parse("20071114 060523-1100"), "20071114-170523+0000", GMT);
        assertEqual(MSK.parse("20071115t000523GMT+07:00"), "20071114-170523+0000", GMT);
        assertEqual(GMT.parse("2007-11-14T060523-1100"), "20071114-170523+0000", GMT);
        assertEqual(MSK.parse("20071114-12:05:23GMT-05:00"), "20071114-170523+0000", GMT);
        assertEqual(MSK.parse("2007-11-14T12:05:23.000-05"), "20071114-170523+0000", GMT);
        assertEqual(MSK.parse("2007-11-14T12:05:23.000-05:00"), "20071114-170523+0000", GMT);
        assertEqual(MSK.parse("2007-11-14T17:05:23Z"), "20071114-170523+0000", GMT);
        assertEqual(MSK.parse("1195059923000"), "20071114-170523+0000", GMT);
    }

    // WARNING: This test will pass only when running in MST time zone.
    @Test
    public void testEqualDateTimes2() {
        assertEqual(GMT.parse("20060101"), "20060101-000000+0000", GMT);
        assertEqual(GMT.parse("2006-01-01"), "20060101-000000+0000", GMT);
        assertEqual(GMT.parse("2006-01-01 00:00:00"), "20060101-000000+0000", GMT);
        assertEqual(MSK.parse("2006-01-01-03:00:00+0300"), "20060101-000000+0000", GMT);
        assertEqual(GMT.parse("2005-12-31T24:00:00"), "20060101-000000+0000", GMT);
        assertEqual(MSK.parse("2005-12-31 24:00:00GMT"), "20060101-000000+0000", GMT);
        assertEqual(MSK.parse("20051231t190000GMT-05:00"), "20060101-000000+0000", GMT);
        assertEqual(GMT.parse("1136073600000"), "20060101-000000+0000", GMT);
    }

    @Test
    public void testEqualDateTimes3() {
        Date parsedToday = MSK.parse("t12:34:56");
        Calendar today = Calendar.getInstance(MSK.getTimeZone());
        today.setTime(parsedToday);
        assertEquals(12, today.get(Calendar.HOUR_OF_DAY));
        assertEquals(34, today.get(Calendar.MINUTE));
        assertEquals(56, today.get(Calendar.SECOND));
        assertEqual(parsedToday, MSK.withTimeZone().format(today.getTime()), MSK);

        DateFormat df = new SimpleDateFormat("yyyyMMdd");
        df.setTimeZone(MSK.getTimeZone());
        String curDateInMSK = df.format(new Date());

        assertEqual(GMT.parse(curDateInMSK + "-123456+0300"), MSK.withTimeZone().format(today.getTime()), MSK);
        assertEqual(GMT.parse(curDateInMSK + "T12:34:56+0300"), MSK.withTimeZone().format(today.getTime()), MSK);
    }

    @Test
    public void testBadDateTimes() {
        String[] badValues = {
            "2007-1102-12:34:56",
            "20070101-1234:56",
            "200711-02-12:34",
            "t12:34:5",
            "12:3456",
            "1234:56",
            "2008-1-10",
            "2004-12-12t",
            "2005-12-31 210",
            "-P10DT2H30MS",
            "1234567",
            "20010101t",
            "t1234567",
            "-",
            "",
            "1",
            "t12::34:56",
            "t12:",
            "123",
            "T",
            "P1234DT12H30M0S",
        };

        for (String s : badValues) {
            try {
                GMT.parse(s);
                fail("Parsed bad value: " + s);
            } catch (InvalidFormatException expected) {
            }
        }
    }

    @Test
    public void testGoodDateTimes() {
        String[] goodValues = {
            "2007-11-02-12:34:56",
            "20070101-123456",
            "2007-11-02-123456",
            "t12:34:56",
            "2005-12-31 21:00:00",
            "2007-11-02",
            "2007-12-12t123456",
            "20071212-12:34",
            "2007-12-12T12MSK",
            "20000101 2200",
            "01:01",
            "12:12:12 +0300",
            "-123456",   // period
            "12",        // hh
            "1234",      // hhmm
            "123456",    // hhmmss
            "20010101",  // yyyymmdd
            "123456789", // long
            "12+1200",
            "1234+1234", // 12:34:00 +1234
            "-P1234DT12H30M0S",

        };

        for (String s : goodValues) {
            try {
                GMT.parse(s);
            } catch (InvalidFormatException e) {
                fail("Couldn't parse: " + s);
            }
        }
    }

    @Test
    public void testDateAsLong() {
        Date a1 = GMT.parse("20010101"); // yyyymmdd
        Date a2 = GMT.parse("2001-01-01");
        assertEquals(a1, a2);

        Date b1 = GMT.parse("121212"); // hhmmss
        Date b2 = GMT.parse("T12:12:12");
        assertEquals(b1, b2);

        Date c1 = GMT.parse("1234567890"); // long;
        Date c2 = new Date(1234567890);
        assertEquals(c1, c2);
    }

    @Test
    public void testNPE() {
        assertThrows("NPE expected", NullPointerException.class, () -> GMT.parse(null));
        assertThrows("NPE expected", NullPointerException.class, () -> TimePeriod.valueOf(null));
        assertThrows("NPE expected", NullPointerException.class, () -> ((TimePeriod) null).toString());
        assertThrows("NPE expected", NullPointerException.class, () -> GMT.format(null));
        assertThrows("NPE expected", NullPointerException.class, () -> GMT.withTimeZone().format(null));
    }

    @Test
    public void testNoTimeZoneFormat() {
        Date d1 = new Date();
        String s = TimeFormat.DEFAULT.format(d1);
        s += TimeZone.getDefault().getDisplayName(TimeZone.getDefault().inDaylightTime(d1), TimeZone.SHORT);
        Date d2 = GMT.parse(s);
        assertEquals(GMT.withTimeZone().format(d1), GMT.withTimeZone().format(d2));
    }

    @Test
    public void testDateOutsideIsoRangeFormat() {
        Date d1 = new Date(Long.MIN_VALUE);
        assertEquals(Long.toString(Long.MIN_VALUE), TimeFormat.GMT.format(d1));

        Date d2 = new Date(Long.MAX_VALUE);
        assertEquals(Long.toString(Long.MAX_VALUE), TimeFormat.GMT.format(d2));

        Date d3 = new Date(TimeUtil.DAY << 32);
        assertEquals(Long.toString(d3.getTime()), TimeFormat.GMT.format(d3));
    }

    @Test
    public void testIsoFormat() {
        // Samples from http://www.w3schools.com/schema/schema_dtypes_date.asp
        // Test parsing
        assertEqual(TimeFormat.GMT.parse("2002-05-30T09:30:10-06:00"), "20020530-153010+0000", TimeFormat.GMT);
        assertEqual(TimeFormat.GMT.parse("2002-05-30T09:30:10+06:00"), "20020530-033010+0000", TimeFormat.GMT);
        assertEqual(TimeFormat.GMT.parse("2002-05-30T09:30:10Z"), "20020530-093010+0000", TimeFormat.GMT);
        // test format
        assertEquals("2002-05-30T09:30:10.000-06:00",
            TimeFormat.getInstance(TimeUtil.getTimeZone("GMT-06:00")).asFullIso()
                .format(TimeFormat.GMT.parse("20020530-153010")));
        assertEquals("2002-05-30T09:30:10.000+06:00",
            TimeFormat.getInstance(TimeUtil.getTimeZone("GMT+06:00")).asFullIso()
                .format(TimeFormat.GMT.parse("20020530-033010")));
        assertEquals("2002-05-30T09:30:10.000Z", TimeFormat.GMT.asFullIso().format(
            TimeFormat.GMT.parse("20020530-093010")));
    }

    private void assertEqual(Date d, String canonicalValue, TimeFormat format) {
        assertEquals(canonicalValue, format.withTimeZone().format(d));
    }

    private void assertPeriodsAreEqual(List<TimePeriod> equalPeriods, String canonicalValue) {
        for (TimePeriod t : equalPeriods) {
            assertEquals(canonicalValue, t.toString());
        }
    }

    @Test
    public void testTimeWithoutTimeZone() {
        TimeFormat MSD = TimeFormat.getInstance(TimeUtil.getTimeZone("Europe/Moscow"));
        Date withoutTZ = MSD.parse("20120406-182355");
        assertEquals(MSD.parse("20120406-182355+0400"), withoutTZ);
    }

    @Test
    public void testTimeWithMillis() {
        TimeFormat MSD = TimeFormat.getInstance(TimeUtil.getTimeZone("Europe/Moscow"));
        assertEquals(1333737666231L, MSD.parse("20120406-224106.231").getTime());
        assertEquals(1333737666231L, MSD.parse("20120406-224106.231+0400").getTime());
    }

    @Test
    public void testZero() {
        assertEquals(0, GMT.parse("0").getTime());
        assertEquals("0", GMT.withTimeZone().format(new Date(0)));
        assertEquals("0", GMT.withMillis().format(new Date(0)));
        assertEquals("0", GMT.withTimeZone().withMillis().format(new Date(0)));
        assertEquals("0", GMT.format(new Date(0)));
    }

    @Test
    public void testEquivalence() throws ParseException {
        for (String tzName : "GMT,GMT+01:30,GMT-01:30,Europe/Moscow,America/Chicago".split(",")) {
            TimeZone tz = TimeUtil.getTimeZone(tzName);
            TimeFormat format = TimeFormat.getInstance(tz);

            assertSame(format.withMillis().withTimeZone(), format.withTimeZone().withMillis());
            assertSame(format.asFullIso(), format.withMillis().asFullIso());
            assertSame(format.asFullIso(), format.withTimeZone().asFullIso());
            assertSame(format.asFullIso(), format.withMillis().withTimeZone().asFullIso());

            doTestEquivalence(format, "yyyyMMdd-HHmmss", false);
            doTestEquivalence(format.withMillis(), "yyyyMMdd-HHmmss.SSS", true);
            doTestEquivalence(format.withTimeZone(), "yyyyMMdd-HHmmssZ", false);
            doTestEquivalence(format.withMillis().withTimeZone(), "yyyyMMdd-HHmmss.SSSZ", true);
            doTestEquivalence(format.asFullIso(), "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", true);
        }
    }

    private void doTestEquivalence(TimeFormat format, String pattern, boolean withMillis) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        TimeZone tz = format.getTimeZone();
        sdf.setTimeZone(tz);
        Random random = new Random(20170608);
        for (int i = 0; i < 1000; i++) {
            long expected = random.nextLong() >> 20; // 1970 +/- 278 years
            if (!withMillis)
                expected = (expected >> 10) * 1000;
            checkConversionEquivalence(format, pattern, sdf, tz, expected);
        }
    }

    // test equivalence around daylight saving switches
    @Test
    public void testDSTEquivalence() throws ParseException {
        TimeZone chicagoTz = TimeUtil.getTimeZone("America/Chicago");
        TimeFormat format = TimeFormat.getInstance(chicagoTz);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
        sdf.setTimeZone(chicagoTz);

        assertSame(format.withMillis().withTimeZone(), format.withTimeZone().withMillis());
        assertSame(format.asFullIso(), format.withMillis().asFullIso());
        assertSame(format.asFullIso(), format.withTimeZone().asFullIso());
        assertSame(format.asFullIso(), format.withMillis().withTimeZone().asFullIso());

        // 8 March 2020, 02:00:00 : 1 hour forward
        long begin = sdf.parse("20200307-230000").getTime();
        long end = sdf.parse("20200308-040000").getTime();
        assertTrue(chicagoTz.getOffset(begin) != chicagoTz.getOffset(end));

        int step = 30_000;
        checkEquivalenceOnRange(format, "yyyyMMdd-HHmmss", false, begin, end, step);
        checkEquivalenceOnRange(format.withMillis(), "yyyyMMdd-HHmmss.SSS", true, begin, end, step);
        checkEquivalenceOnRange(format.withTimeZone(), "yyyyMMdd-HHmmssZ", false, begin, end, step);
        checkEquivalenceOnRange(format.withMillis().withTimeZone(), "yyyyMMdd-HHmmss.SSSZ", true, begin, end, step);
        checkEquivalenceOnRange(format.asFullIso(), "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", true, begin, end, step);

        // 1 November 2020, 02:00:00 : 1 hour backward
        begin = sdf.parse("20201101-000000").getTime();
        end = sdf.parse("20201101-040000").getTime();
        assertTrue(chicagoTz.getOffset(begin) != chicagoTz.getOffset(end));

        checkEquivalenceOnRange(format, "yyyyMMdd-HHmmss", false, begin, end, step);
        checkEquivalenceOnRange(format.withMillis(), "yyyyMMdd-HHmmss.SSS", true, begin, end, step);
        checkEquivalenceOnRange(format.withTimeZone(), "yyyyMMdd-HHmmssZ", false, begin, end, step);
        checkEquivalenceOnRange(format.withMillis().withTimeZone(), "yyyyMMdd-HHmmss.SSSZ", true, begin, end, step);
        checkEquivalenceOnRange(format.asFullIso(), "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", true, begin, end, step);
    }

    @Test
    public void testWeirdOffset() throws ParseException {
        TimeZone moscowTz = TimeUtil.getTimeZone("Europe/Moscow");
        TimeFormat format = TimeFormat.getInstance(moscowTz);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
        sdf.setTimeZone(moscowTz);

        // https://en.wikipedia.org/wiki/Moscow_Time :
        // ...
        // From 1 Jan 1880 UTC+02:30:17
        // From 3 Jul 1916 UTC+02:31:19
        // From 1 Jul 1917 UTC+02:31:19 with DST
        // From 1 Jul 1919 UTC+03:00 with DST
        // ...
        long begin = sdf.parse("19190701-000000").getTime();
        assertNotEquals(0, moscowTz.getOffset(begin) % 60_000);
        long end = sdf.parse("19190701-040000").getTime();
        assertEquals(0, moscowTz.getOffset(end) % 60_000);

        int step = 5_000;
        checkEquivalenceOnRange(format, "yyyyMMdd-HHmmss", false, begin, end, step);
        checkEquivalenceOnRange(format.withMillis(), "yyyyMMdd-HHmmss.SSS", true, begin, end, step);
        checkEquivalenceOnRange(format.withTimeZone(), "yyyyMMdd-HHmmssZ", false, begin, end, step);
        checkEquivalenceOnRange(format.withMillis().withTimeZone(), "yyyyMMdd-HHmmss.SSSZ", true, begin, end, step);
        checkEquivalenceOnRange(format.asFullIso(), "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", true, begin, end, step);
    }

    private void checkEquivalenceOnRange(TimeFormat format, String pattern, boolean withMillis,
        long begin, long end, long step) throws ParseException
    {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        TimeZone tz = format.getTimeZone();
        sdf.setTimeZone(tz);

        long expected = begin;
        if (withMillis)
            expected += 100 + ThreadLocalRandom.current().nextInt(900);

        while (expected < end) {
            checkConversionEquivalence(format, pattern, sdf, tz, expected);
            expected += step;
        }
    }

    private void checkConversionEquivalence(TimeFormat format, String pattern, SimpleDateFormat sdf, TimeZone tz,
        long expected) throws ParseException
    {
        String canonical = sdf.format(expected);
        long reversed = sdf.parse(canonical).getTime();
        assertEquals(canonical, format.format(expected));
        assertEquals(canonical, format.format(new Date(expected)));
        assertEquals(reversed, format.parse(canonical).getTime());
        if (expected != reversed &&
            tz.getOffset(expected) % 60000 == 0 &&
            tz.getOffset(expected) == tz.getOffset(reversed))
        {
            fail(String.format(
                "Reversed time differ from original time - " +
                    "\nExpected %d = %s = %s, offset %d" +
                    "\nReversed %d = %s = %s, offset %d" +
                    "\nPattern %s, time zone %s",
                expected, sdf.format(expected), format.format(expected), tz.getOffset(expected),
                reversed, sdf.format(reversed), format.format(reversed), tz.getOffset(reversed),
                pattern, tz));
        }
    }
}
