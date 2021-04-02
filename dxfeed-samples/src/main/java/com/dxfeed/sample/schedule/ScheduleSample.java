/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.dxfeed.sample.schedule;

import com.devexperts.io.URLInputStream;
import com.dxfeed.ipf.InstrumentProfile;
import com.dxfeed.ipf.InstrumentProfileReader;
import com.dxfeed.schedule.Day;
import com.dxfeed.schedule.DayFilter;
import com.dxfeed.schedule.Schedule;
import com.dxfeed.schedule.Session;
import com.dxfeed.schedule.SessionFilter;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * A sample program that demonstrates different use cases of Schedule API.
 */
public class ScheduleSample {
    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            System.out.println("wrong number of arguments");
            System.out.println("usage:  ScheduleSample  <defaults>  <profiles>  <symbol>  [time]");
            System.out.println("where:  <defaults>  is a URL to Schedule API defaults file");
            System.out.println("        <profiles>  is a URL to IPF file");
            System.out.println("        <symbol>    is a ticker symbol used for sample");
            System.out.println("        [time]      is a time used for sample in a format yyyy-MM-dd-HH:mm:ss");
            System.out.println("sample: ScheduleSample  schedule.properties.zip  sample.ipf.zip  IBM  2011-05-26-14:15:00");
            return;
        }
        updateScheduleDefaults(args[0]);
        Map<String, InstrumentProfile> profiles = loadInstrumentProfiles(args[1]);
        checkAllSchedules(profiles.values());
        String symbol = args[2];
        InstrumentProfile profile = profiles.get(symbol);
        if (profile == null) {
            System.out.println("Could not find profile for " + symbol);
            return;
        }
        System.out.println("Found profile for " + symbol + ": " + profile.getDescription());
        long time = args.length < 4 ? System.currentTimeMillis() : new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss").parse(args[3]).getTime();
        System.out.println("Using timestamp " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ").format(time));
        printNext5Holidays(profile, time);
        printCurrentSession(profile, time);
        printNextTradingSession(profile, time);
        printNearestTradingSession(profile, time);
    }

    private static void updateScheduleDefaults(String url) {
        try {
            Schedule.setDefaults(URLInputStream.readURL(url));
            System.out.println("Schedule defaults updated successfully");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Map<String, InstrumentProfile> loadInstrumentProfiles(String url) {
        Map<String, InstrumentProfile> profiles = new HashMap<>();
        try {
            for (InstrumentProfile profile : new InstrumentProfileReader().readFromFile(url))
                profiles.put(profile.getSymbol(), profile);
            System.out.println("Loaded " + profiles.size() + " instrument profiles");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return profiles;
    }

    private static void checkAllSchedules(Collection<InstrumentProfile> profiles) {
        int successes = 0;
        for (InstrumentProfile profile : profiles)
            try {
                Schedule.getInstance(profile);
                for (String venue : Schedule.getTradingVenues(profile))
                    Schedule.getInstance(profile, venue);
                successes++;
            } catch (Exception e) {
                System.out.println("Error getting schedule for " + profile.getSymbol() + " (" + profile.getTradingHours() + "): " + e);
            }
        System.out.println("Checked " + profiles.size() + " instrument profiles: " + successes + " successes, " + (profiles.size() - successes) + " failures");
    }

    private static void printNext5Holidays(InstrumentProfile profile, long time) {
        Schedule schedule = Schedule.getInstance(profile);
        Day day = schedule.getDayByTime(time);
        String output = "5 next holidays for " + profile.getSymbol() + ":";
        for (int i = 0; i < 5; i++) {
            day = day.findNextDay(DayFilter.HOLIDAY);
            if (day != null)
                output = output + " " + day.getYearMonthDay();
            else
                break;
        }
        System.out.println(output);
    }

    private static void printCurrentSession(InstrumentProfile profile, long time) {
        Schedule schedule = Schedule.getInstance(profile);
        Session session = schedule.getSessionByTime(time);
        System.out.println("Current session for " + profile.getSymbol() + ": " + session + " in " + session.getDay());
    }

    private static void printNextTradingSession(InstrumentProfile profile, long time) {
        Schedule schedule = Schedule.getInstance(profile);
        Session session = schedule.getSessionByTime(time);
        if (!session.isTrading())
            session = session.getNextSession(SessionFilter.TRADING);
        System.out.println("Next trading session for " + profile.getSymbol() +  ": " + session + " in " + session.getDay());
    }

    private static void printNearestTradingSession(InstrumentProfile profile, long time) {
        Schedule schedule = Schedule.getInstance(profile);
        Session session = schedule.getNearestSessionByTime(time, SessionFilter.TRADING);
        System.out.println("Nearest trading session for " + profile.getSymbol() + ": " + session + " in " + session.getDay());
    }
}
