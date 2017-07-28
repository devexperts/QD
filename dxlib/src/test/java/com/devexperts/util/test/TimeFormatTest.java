/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2017 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.util.test;

import java.text.*;
import java.util.*;

import com.devexperts.util.*;
import junit.framework.TestCase;

public class TimeFormatTest extends TestCase {

    private final TimeFormat GMT = TimeFormat.GMT;
    private final TimeFormat MSK = TimeFormat.getInstance(TimeZone.getTimeZone("GMT+3:00"));

    public void testZoneStability() {
        // try to parse in GMT a time with an explicit time-zone
        assertEquals(1197450000000L, GMT.parse("2007-12-12T12+0300").getTime());
        // make sure it does not break the default formatting of GMT timezone
        assertEquals("20140618-130218+0000", GMT.withTimeZone().format(new Date(1403096538000L)));
    }

    public void testTimePeriods() throws InvalidFormatException {
        assertEquals(0L, TimePeriod.valueOf("0").getTime());
        assertEquals(1000L, TimePeriod.valueOf("1s").getTime());
        assertEquals(123L, TimePeriod.valueOf(".123456789").getTime());
        assertEquals("PT1.235S", TimePeriod.valueOf("1.23456789").toString());
        assertEquals(TimePeriod.valueOf("1.23456789"), TimePeriod.valueOf("0d0h0m1.235s"));
    }

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
            } catch (InvalidFormatException e) {
            }
        }
    }

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
            } catch (InvalidFormatException e) {
            }
        }
    }

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
            "-123456",  // period
            "12",		// hh
            "1234",     // hhmm
            "123456",   // hhmmss
            "20010101", // yyyymmdd
            "123456789",// long
            "12+1200",
            "1234+1234",// 12:34:00 +1234
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

    public void testNPE() {
        try {
            GMT.parse(null);
            fail("NPE expected");
        } catch (NullPointerException npe) {
        }
        try {
            TimePeriod.valueOf(null);
            fail("NPE expected");
        } catch (NullPointerException npe) {
        }
        try {
            ((TimePeriod) null).toString();
            fail("NPE expected");
        } catch (NullPointerException npe) {
        }
        try {
            GMT.format(null);
            fail("NPE expected");
        } catch (NullPointerException npe) {
        }
        try {
            GMT.withTimeZone().format(null);
            fail("NPE expected");
        } catch (NullPointerException npe) {
        }
    }

    public void testNoTimeZoneFormat() {
        Date d1 = new Date();
        String s = TimeFormat.DEFAULT.format(d1);
        s += TimeZone.getDefault().getDisplayName(TimeZone.getDefault().inDaylightTime(d1), TimeZone.SHORT);
        Date d2 = GMT.parse(s);
        assertEquals(GMT.withTimeZone().format(d1), GMT.withTimeZone().format(d2));
    }

    public void testIsoFormat() {
        // Samples from http://www.w3schools.com/schema/schema_dtypes_date.asp
        // Test parsing
        assertEqual(TimeFormat.GMT.parse("2002-05-30T09:30:10-06:00"), "20020530-153010+0000", TimeFormat.GMT);
        assertEqual(TimeFormat.GMT.parse("2002-05-30T09:30:10+06:00"), "20020530-033010+0000", TimeFormat.GMT);
        assertEqual(TimeFormat.GMT.parse("2002-05-30T09:30:10Z"), "20020530-093010+0000", TimeFormat.GMT);
        // test format
        assertEquals("2002-05-30T09:30:10.000-06:00", TimeFormat.getInstance(TimeZone.getTimeZone("GMT-06:00")).asFullIso().format(
            TimeFormat.GMT.parse("20020530-153010")));
        assertEquals("2002-05-30T09:30:10.000+06:00", TimeFormat.getInstance(TimeZone.getTimeZone("GMT+06:00")).asFullIso().format(
            TimeFormat.GMT.parse("20020530-033010")));
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

    public void testTimeWithoutTimeZone() {
        TimeFormat MSD = TimeFormat.getInstance(TimeZone.getTimeZone("Europe/Moscow"));
        Date withoutTZ = MSD.parse("20120406-182355");
        assertEquals(MSD.parse("20120406-182355+0400"), withoutTZ);
    }

    public void testTimeWithMillis() {
        TimeFormat MSD = TimeFormat.getInstance(TimeZone.getTimeZone("Europe/Moscow"));
        assertEquals(1333737666231L, MSD.parse("20120406-224106.231").getTime());
        assertEquals(1333737666231L, MSD.parse("20120406-224106.231+0400").getTime());
    }

    public void testZero() {
        assertEquals(0, GMT.parse("0").getTime());
        assertEquals("0", GMT.withTimeZone().format(new Date(0)));
        assertEquals("0", GMT.withMillis().format(new Date(0)));
        assertEquals("0", GMT.withTimeZone().withMillis().format(new Date(0)));
        assertEquals("0", GMT.format(new Date(0)));
    }

    public void testEquivalence() throws ParseException {
        for (String tzName : "GMT,GMT+01:30,GMT-01:30,Europe/Moscow,America/Chicago".split(",")) {
            TimeZone tz = TimeZone.getTimeZone(tzName);
            TimeFormat format = TimeFormat.getInstance(tz);

            assertTrue(format.withMillis().withTimeZone() == format.withTimeZone().withMillis());
            assertTrue(format.asFullIso() == format.withMillis().asFullIso());
            assertTrue(format.asFullIso() == format.withTimeZone().asFullIso());
            assertTrue(format.asFullIso() == format.withMillis().withTimeZone().asFullIso());

            doTestEquivalence(format, "yyyyMMdd-HHmmss", false, false);
            doTestEquivalence(format.withMillis(), "yyyyMMdd-HHmmss.SSS", true, false);
            doTestEquivalence(format.withTimeZone(), "yyyyMMdd-HHmmssZ", false, true);
            doTestEquivalence(format.withMillis().withTimeZone(), "yyyyMMdd-HHmmss.SSSZ", true, true);
            doTestEquivalence(format.asFullIso(), "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", true, true);
        }
    }

    private void doTestEquivalence(TimeFormat format, String pattern, boolean withMillis, boolean withZone) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setTimeZone(format.getTimeZone());
        Random random = new Random(20170608);
        for (int i = 0; i < 1000; i++) {
            long expected = random.nextLong() >> 20; // 1970 +/- 278 years
            if (!withMillis)
                expected = (expected >> 10) * 1000;
            String canonical = sdf.format(expected);
            long reversed = sdf.parse(canonical).getTime();
            assertEquals(canonical, format.format(expected));
            assertEquals(canonical, format.format(new Date(expected)));
            assertEquals(reversed, format.parse(canonical).getTime());
            if (expected != reversed && format.getTimeZone().getOffset(expected) % 60000 == 0) {
                fail("Reversed time differ from original time - " +
                    "\nExpected " + expected + " = " + sdf.format(expected) + " = " + format.format(expected) + ", offset " + format.getTimeZone().getOffset(expected) +
                    "\nReversed " + reversed + " = " + sdf.format(reversed) + " = " + format.format(reversed) + ", offset " + format.getTimeZone().getOffset(reversed) +
                    "\nPattern " + pattern + ", time zone " + format.getTimeZone());
            }
        }
    }
}
