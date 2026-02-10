/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.schedule.test;

import com.devexperts.io.ByteArrayOutput;
import com.devexperts.test.isolated.Isolated;
import com.devexperts.test.isolated.IsolatedRunner;
import com.devexperts.util.DayUtil;
import com.dxfeed.schedule.Day;
import com.dxfeed.schedule.Schedule;
import com.dxfeed.schedule.Session;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Verifies that the current schedule.properties file remains compatible
 * with the legacy Schedule class from previous API version.
 * <p>
 * This ensures forward compatibility for clients that use older parsers.
 */
@RunWith(IsolatedRunner.class)
@Isolated({"com.dxfeed.schedule", "com.dxfeed.schedule.test.old"})
public class ScheduleVersionCompatibilityTest {

    private static final List<Integer> SAMPLE_DATES = Arrays.asList(
        // days selected avoiding known holidays/short days/special days
        20240722, 20240723, 20240724, 20240725, 20240726, 20240727, 20240728
    );

    @Test
    public void testDefaultsValidByOldParser() throws Exception {
        try (InputStream in = com.dxfeed.schedule.Schedule.class.getResourceAsStream("schedule.properties")) {
            assertNotNull(in);
            com.dxfeed.schedule.test.old.Schedule.setDefaults(in);
        }
    }

    @Test
    public void testOldParserVsNewParserOnOldProperties() throws Exception {
        // if this test fails check date in the file - it should be the same as in schedule.properties
        byte[] oldProps = readResourceBytes("schedule_old.properties");
        String oldPropsText = new String(oldProps, StandardCharsets.UTF_8);

        Set<String> venues = extractVenues(oldPropsText);

        Set<Integer> dates = extractInterestingDates(oldPropsText);
        dates.addAll(SAMPLE_DATES);

        Map<String, Map<Integer, List<NormalizedSession>>> snapshotNew = buildSnapshotNew(oldProps, venues, dates);
        Map<String, Map<Integer, List<NormalizedSession>>> snapshotOld = buildSnapshotOld(oldProps, venues, dates);

        compareSnapshots(snapshotOld, snapshotNew);
    }

    @Test
    public void testNewParserOnOldPropertiesVsNewProperties() throws Exception {
        // if this test fails check date in both files - it should be the same as in schedule.properties
        byte[] oldProps = readResourceBytes("schedule_old.properties");
        byte[] refProps = readResourceBytes("schedule_refactored.properties");

        String oldText = new String(oldProps, StandardCharsets.UTF_8);
        String refText = new String(refProps, StandardCharsets.UTF_8);

        Set<String> venues = new HashSet<>(extractVenues(oldText));
        venues.retainAll(extractVenues(refText));

        Set<Integer> oldInterestingDates = extractInterestingDates(oldText);
        Set<Integer> refInterestingDates = extractInterestingDates(refText);
        assertEquals("The lists of holidays and short days are not changed", oldInterestingDates, refInterestingDates);

        Set<Integer> dates = new HashSet<>(oldInterestingDates);
        dates.addAll(refInterestingDates);
        dates.addAll(SAMPLE_DATES);

        Map<String, Map<Integer, List<NormalizedSession>>> snapshotOld = buildSnapshotNew(oldProps, venues, dates);
        Map<String, Map<Integer, List<NormalizedSession>>> snapshotRef = buildSnapshotNew(refProps, venues, dates);

        compareSnapshots(snapshotOld, snapshotRef);
    }

    private static byte[] readResourceBytes(String path) throws IOException {
        try (InputStream in = ScheduleVersionCompatibilityTest.class.getResourceAsStream(path);
             ByteArrayOutput out = new ByteArrayOutput())
        {
            assertNotNull("Resource not found: " + path, in);
            for (int n; (n = in.read(out.getBuffer(), out.getPosition(), out.getLimit() - out.getPosition())) >= 0; ) {
                out.setPosition(out.getPosition() + n);
                out.ensureCapacity(out.getPosition() + 1024);
            }
            return out.toByteArray();
        }
    }

    private static Set<String> extractVenues(String propsText) throws IOException {
        Set<String> venues = new HashSet<>();
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(new java.io.ByteArrayInputStream(propsText.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8)))
        {
            for (String line; (line = br.readLine()) != null; ) {
                if (line.startsWith("tv.") && line.contains("=")) {
                    String key = line.substring(0, line.indexOf('='));
                    String venue = key.substring("tv.".length());
                    venues.add(venue);
                }
            }
        }
        return venues;
    }

    private static Set<Integer> extractInterestingDates(String propsText) throws IOException {
        Set<Integer> interestingDates = new HashSet<>();

        Pattern number = Pattern.compile("\\b(\\d{8})\\b");
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(new java.io.ByteArrayInputStream(propsText.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8)))
        {
            for (String line; (line = br.readLine()) != null; ) {
                String trimmed = line.trim();
                while (trimmed.endsWith("\\")) {
                    trimmed = trimmed.substring(0, trimmed.length() - 1);
                    String nextLine = br.readLine();
                    if (nextLine == null)
                        break;
                    trimmed = trimmed + nextLine;
                }

                if (trimmed.isEmpty() || trimmed.startsWith("#"))
                    continue;
                boolean listLine = trimmed.startsWith("hd.") || trimmed.startsWith("sd.") || trimmed.startsWith("xd.");
                if (listLine) {
                    Matcher m = number.matcher(trimmed);
                    while (m.find()) {
                        interestingDates.add(Integer.parseInt(m.group(1)));
                    }
                }

                // Add explicit date overrides like 20241224=...
                if (trimmed.startsWith("tv.") && trimmed.contains("=")) {
                    Matcher m = Pattern.compile("(\\d{8})=").matcher(trimmed);
                    while (m.find()) {
                        interestingDates.add(Integer.parseInt(m.group(1)));
                    }
                }
            }
        }

        Set<Integer> result = new HashSet<>();
        
        // For all interesting dates add +/- 2 days
        for (int ymd : interestingDates) {
            int dayId = DayUtil.getDayIdByYearMonthDay(ymd);
            for (int d = -2; d <= 2; d++) {
                int ymdAround = DayUtil.getYearMonthDayByDayId(dayId + d);
                result.add(ymdAround);
            }
        }

        return result;
    }

    private static Map<String, Map<Integer, List<NormalizedSession>>> buildSnapshotNew(byte[] defaults,
        Set<String> venues, Set<Integer> dates) throws IOException
    {
        Schedule.setDefaults(defaults);
        Map<String, Map<Integer, List<NormalizedSession>>> snapshot = new HashMap<>();
        for (String venue : venues) {
            Schedule sch;
            try {
                sch = Schedule.getInstance(venue + "()");
            } catch (Exception e) {
                sch = Schedule.getInstance(venue + "(0=)");
            }
            snapshot.put(venue, getSessionsByDateNew(sch, dates));
        }
        return snapshot;
    }

    private static Map<Integer, List<NormalizedSession>> getSessionsByDateNew(Schedule sch, Collection<Integer> dates) {
        Map<Integer, List<NormalizedSession>> sessionsByDate = new HashMap<>();
        for (int ymd : dates) {
            Day day = sch.getDayByYearMonthDay(ymd);
            sessionsByDate.put(ymd, normalize(day.getSessions()));
        }
        return sessionsByDate;
    }

    private static Map<String, Map<Integer, List<NormalizedSession>>> buildSnapshotOld(byte[] defaults,
        Set<String> venues, Set<Integer> dates) throws IOException
    {
        com.dxfeed.schedule.test.old.Schedule.setDefaults(defaults);
        Map<String, Map<Integer, List<NormalizedSession>>> snapshot = new HashMap<>();
        for (String venue : venues) {
            com.dxfeed.schedule.test.old.Schedule sch;
            try {
                sch = com.dxfeed.schedule.test.old.Schedule.getInstance(venue + "()");
            } catch (Exception e) {
                sch = com.dxfeed.schedule.test.old.Schedule.getInstance(venue + "(0=)");
            }
            snapshot.put(venue, getSessionsByDateOld(sch, dates));
        }
        return snapshot;
    }

    private static Map<Integer, List<NormalizedSession>> getSessionsByDateOld(com.dxfeed.schedule.test.old.Schedule sch,
        Collection<Integer> dates)
    {
        Map<Integer, List<NormalizedSession>> sessionsByDate = new HashMap<>();
        for (int ymd : dates) {
            com.dxfeed.schedule.test.old.Day day = sch.getDayByYearMonthDay(ymd);
            sessionsByDate.put(ymd, normalizeOld(day.getSessions()));
        }
        return sessionsByDate;
    }

    private static List<NormalizedSession> normalize(List<Session> sessions) {
        List<NormalizedSession> list = new ArrayList<>(sessions.size());
        for (Session s : sessions) {
            list.add(new NormalizedSession(s.getType().name(), s.getStartTime(), s.getEndTime()));
        }
        list.sort(Comparator.comparingLong(a -> a.start));
        return list;
    }

    private static List<NormalizedSession> normalizeOld(List<com.dxfeed.schedule.test.old.Session> sessions) {
        List<NormalizedSession> list = new ArrayList<>(sessions.size());
        for (com.dxfeed.schedule.test.old.Session s : sessions) {
            list.add(new NormalizedSession(s.getType().name(), s.getStartTime(), s.getEndTime()));
        }
        list.sort(Comparator.comparingLong(a -> a.start));
        return list;
    }

    private static void compareSnapshots(Map<String, Map<Integer, List<NormalizedSession>>> a,
        Map<String, Map<Integer, List<NormalizedSession>>> b)
    {
        assertEquals("Venue sets differ", a.keySet(), b.keySet());
        for (String venue : a.keySet()) {
            Map<Integer, List<NormalizedSession>> sessionByDayA = a.get(venue);
            Map<Integer, List<NormalizedSession>> sessionByDayB = b.get(venue);
            assertEquals("Date sets differ for venue " + venue, sessionByDayA.keySet(), sessionByDayB.keySet());
            for (int ymd : sessionByDayA.keySet()) {
                List<NormalizedSession> sa = sessionByDayA.get(ymd);
                List<NormalizedSession> sb = sessionByDayB.get(ymd);
                assertEquals("Different number of sessions for venue=" + venue + " date=" + ymd, sa.size(), sb.size());
                for (int i = 0; i < sa.size(); i++) {
                    NormalizedSession na = sa.get(i);
                    NormalizedSession nb = sb.get(i);
                    assertEquals("Session type mismatch for venue=" + venue + " date=" + ymd + " idx=" + i,
                        na.type, nb.type);
                    assertEquals("Session start mismatch for venue=" + venue + " date=" + ymd + " idx=" + i,
                        na.start, nb.start);
                    assertEquals("Session end mismatch for venue=" + venue + " date=" + ymd + " idx=" + i,
                        na.end, nb.end);
                }
            }
        }
    }

    private static final class NormalizedSession {
        final String type;
        final long start;
        final long end;

        NormalizedSession(String type, long start, long end) {
            this.type = type;
            this.start = start;
            this.end = end;
        }
    }
}
