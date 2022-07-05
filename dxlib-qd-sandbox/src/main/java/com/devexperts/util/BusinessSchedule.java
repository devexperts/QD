/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2022 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.util;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responsobilities: determine operating mode by day, location specified (you should create instance for every country
 * or even exchange)
 */
public abstract class BusinessSchedule {
    /**
     * @param id - currently supports "US", "RU", "CA"; otherwise EMPTY
     * @return instance of BusinessSchedule for required country, exchange etc
     */
    public static BusinessSchedule getInstance(String id) {
        return
            "US".equalsIgnoreCase(id) ? US :
            "RU".equalsIgnoreCase(id) ? RU :
            "CA".equalsIgnoreCase(id) ? CA :
            EMPTY;
    }

    /**
     * create operating mode instance by day id in special format and week day number
     */
    public abstract OperatingMode getOperatingMode(int year_month_day_number, int week_day_number);

    /**
     * Retrieves list of holidays
     */
    public abstract Set<Long> getHolidays();


    // ========== public static stuff ==========
    public static final BusinessSchedule EMPTY = new BusinessSchedule() {
        private final OperatingMode business = new OperatingMode(false, false, false, false, "");
        private final OperatingMode weekend = new OperatingMode(false, true, false, false, "");

        public OperatingMode getOperatingMode(int year_month_day_number, int week_day_number) {
            return week_day_number < 6 ? business : weekend;
        }

        public Set<Long> getHolidays() {
            return Collections.emptySet();
        }
    };

    public static final BusinessSchedule US = new BusinessScheduleImpl(
        "0:20020101,1:20020121,2:20020218,3:20020329,4:20020527,5:20020704,6:20020902,7:20021128,8:20021225," +
        "0:20030101,1:20030120,2:20030217,3:20030418,4:20030526,5:20030704,6:20030901,7:20031127,8:20031225," +
        "0:20040101,1:20040119,2:20040216,3:20040409,4:20040531,5:20040705,6:20040906,7:20041125,8:20041224," +
        "0:20050101,1:20050117,2:20050221,3:20050325,4:20050530,5:20050704,6:20050905,7:20051124,8:20051226," +
        "0:20060102,1:20060116,2:20060220,3:20060414,4:20060529,5:20060704,6:20060904,7:20061123,8:20061225," +
        "0:20070101,1:20070115,2:20070219,3:20070406,4:20070528,5:20070704,6:20070903,7:20071122,8:20071225," +
        "0:20080101,1:20080121,2:20080218,3:20080321,4:20080526,5:20080704,6:20080901,7:20081127,8:20081225," +
        "0:20090101,1:20090119,2:20090216,3:20090410,4:20090525,5:20090703,6:20090907,7:20091126,8:20091225," +
        "0:20100101,1:20100118,2:20100215,3:20100402,4:20100531,5:20100705,6:20100906,7:20101125,8:20101224," +
        "0:20110101,1:20110117,2:20110221,3:20110422,4:20110530,5:20110704,6:20110905,7:20111124,8:20111226," +
        "0:20120102,1:20120116,2:20120220,3:20120406,4:20120528,5:20120704,6:20120903,7:20121122,8:20121225," +
        "0:20130101,1:20130121,2:20130218,3:20130329,4:20130527,5:20130704,6:20130902,7:20131128,8:20131225," +
        "0:20140101,1:20140120,2:20140217,3:20140418,4:20140526,5:20140704,6:20140901,7:20141127,8:20141225," +
        "0:20150101,1:20150119,2:20150216,3:20150403,4:20150525,5:20150703,6:20150907,7:20151126,8:20151225," +
        "0:20160101,1:20160118,2:20160215,3:20160325,4:20160530,5:20160704,6:20160905,7:20161124,8:20161226," +
        "0:20170102,1:20170116,2:20170220,3:20170414,4:20170529,5:20170704,6:20170904,7:20171123,8:20171225," +
        "0:20180101,1:20180115,2:20180219,3:20180330,4:20180528,5:20180704,6:20180903,7:20181122,8:20181225," +
        "0:20190101,1:20190121,2:20190218,3:20190419,4:20190527,5:20190704,6:20190902,7:20191128,8:20191225," +
        "0:20200101,1:20200120,2:20200217,3:20200410,4:20200525,5:20200703,6:20200907,7:20201126,8:20201225," +
        "0:20210101,1:20210118,2:20210215,3:20210402,4:20210531,5:20210705,6:20210906,7:20211125,8:20211224," +
        "0:20220101,1:20220117,2:20220221,3:20220415,4:20220530,9:20220620,5:20220704,6:20220905,7:20221124,8:20221226," +
        "0:20230102,1:20230116,2:20230220,3:20230407,4:20230529,9:20230619,5:20230704,6:20230904,7:20231123,8:20231225," +
        "0:20240101,1:20240115,2:20240219,3:20240329,4:20240527,9:20240619,5:20240704,6:20240902,7:20241128,8:20241225," +
        "0:20250101,1:20250120,2:20250217,3:20250418,4:20250526,9:20250619,5:20250704,6:20250901,7:20251127,8:20251225," +
        "",
        "H:New Year's Day,Martin Luther King Jr. Day,President's Day,Good Friday,Memorial Day,Independence Day,Labor Day,Thanksgiving Day,Christmas,Juneteenth;"
    );

    public static final BusinessSchedule RU = new BusinessScheduleImpl(
        "H0:20090101,H1:20090102,H1:20090105,H1:20090106,H1:20090107,H1:20090108,H1:20090109,H1:20090110,E:20090111,H2:20090223,H3:20090309,20090501,20090612,20091104," +
        "",
        ""
    );

    public static final BusinessSchedule CA = new BusinessScheduleImpl(
        "0:20090101,1:20090410,2:20090413,3:20090518,4:20090601,5:20090907,6:20091012,7:20091111,8:20091225,9:20091226," +
        "0:20100101,1:20100402,2:20100405,3:20100524,4:20100601,5:20100906,6:20101011,7:20101111,8:20101225,9:20101226," +
        "0:20110101,1:20110422,2:20110425,3:20110523,4:20110601,5:20110905,6:20111010,7:20111111,8:20111225,9:20111226," +
        "",
        "H:New Year's Day,Good Friday,Easter Monday,Victoria Day,Canada Day,Labour Day,Thanksgiving Day,Remembrance Day,Christmas,Boxing Day;"
    );

    /**
     * information about operating mode (is it Holiday, etc) for Day instance from Timing
     */
    public static class OperatingMode {
        private final boolean short_business;
        private final boolean weekend;
        private final boolean extra_business;
        private final boolean holiday;
        private final String description;

        public OperatingMode(boolean short_business, boolean weekend, boolean extra_business, boolean holiday, String description) {
            this.short_business = short_business;
            this.weekend = weekend;
            this.extra_business = extra_business;
            this.holiday = holiday;
            this.description = description;
        }

        /**
         * Determines if this day is a trading day - not a weekend and not a holiday. It also may return true if it's extra business day.
         */
        public boolean isBusiness() {
            return (!weekend && !holiday) || extra_business;
        }

        /*
        * Determines if this day when the Market will close early
        */
        public boolean isShortBusiness() {
            return short_business;
        }

        /**
         * Determines if this day is a weekend (Saturday or Sunday).
         */
        public boolean isWeekend() {
            return weekend;
        }

        /*
        *  Weekend, but business day.
        */
        public boolean isExtraBusiness() {
            return extra_business;
        }

        /**
         * Determines if this day is a holiday from the list of exchange holidays.
         */
        public boolean isHoliday() {
            return holiday;
        }

        /**
         * Returns additional description of day. If information doesn't exist, it returns empty string.
         */
        public String getDescription() {
            return description;
        }

        public boolean equals(Object object) {
            if (!(object instanceof OperatingMode))
                return false;
            OperatingMode other = (OperatingMode) object;
            return short_business == other.short_business && weekend == other.weekend &&
                extra_business == other.extra_business && holiday == other.holiday &&
                (description == null ? other.description == null : description.equals(other.description));
        }

        public int hashCode() {
            return (short_business ? 1 : 0) + (weekend ? 2 : 0) + (extra_business ? 4 : 0) + (holiday ? 8 : 0) +
                (description != null ? description.hashCode() * 31 : 0);
        }
    }

    /**
     * Base implementation for BusinessSchedule abstract class
     */
    private static class BusinessScheduleImpl extends BusinessSchedule {
        private static final Pattern SPECIAL_DAYS = Pattern.compile("(?:([HSE])?+(\\d*):)?+(\\d{8}),");
        private static final Pattern DAY_DESCRIPTION_HEADER = Pattern.compile("(?:H:([^:;]*);)?(?:S:([^:;]*);)?(?:E:([^:;]*);)?");
        private static final Pattern CSV = Pattern.compile(",");

        private final LongHashMap<String> holidays = new LongHashMap<String>();
        private final LongHashMap<String> extra_business = new LongHashMap<String>();
        private final LongHashMap<String> short_business = new LongHashMap<String>();

        private final IndexedSet<OperatingMode, OperatingMode> modes = new IndexedSet<OperatingMode, OperatingMode>();

        BusinessScheduleImpl(String special_days, String description) {
            Matcher matcher = DAY_DESCRIPTION_HEADER.matcher(description);

            String[] holidays_descr = {};
            String[] short_business_descr = {};
            String[] extra_business_descr = {};

            if (matcher.find()) {
                if (matcher.group(1) != null)
                    holidays_descr = CSV.split(matcher.group(1));
                if (matcher.group(2) != null)
                    short_business_descr = CSV.split(matcher.group(2));
                if (matcher.group(3) != null)
                    extra_business_descr = CSV.split(matcher.group(3));
            }

            matcher = SPECIAL_DAYS.matcher(special_days);

            while (matcher.find()) {
                String descripition = matcher.group(2);
                int descr_id = (descripition != null && descripition.length() != 0) ? Integer.parseInt(descripition) : -1;

                String day_type = matcher.group(1);
                if (day_type == null || day_type.isEmpty() || day_type.equals("H")) {
                    put(holidays, Long.parseLong(matcher.group(3)), holidays_descr, descr_id);
                } else if (day_type.equals("S")) {
                    put(short_business, Long.parseLong(matcher.group(3)), short_business_descr, descr_id);
                } else if (day_type.equals("E")) {
                    put(extra_business, Long.parseLong(matcher.group(3)), extra_business_descr, descr_id);
                }

            }
        }

        private static void put(LongHashMap<String> dest_map, long date, String[] descriptions, int descr_id) {
            dest_map.put(date, (descr_id == -1 || descr_id >= descriptions.length) ? "" : descriptions[descr_id]);
        }

        /*
        *  Returns information about operating mode by day
         */
        public OperatingMode getOperatingMode(int year_month_day_number, int week_day_number) {
            String holiday = holidays.get(year_month_day_number);
            String short_day = short_business.get(year_month_day_number);
            String extra_day = extra_business.get(year_month_day_number);
            String description = concat(concat(concat("", holiday), short_day), extra_day);

            OperatingMode mode = new OperatingMode(short_day != null, week_day_number >= 6, extra_day != null, holiday != null, description);
            OperatingMode canonical = modes.getByKey(mode);
            if (canonical == null)
                synchronized (modes) {
                    modes.put(canonical = mode);
                }
            return canonical;
        }

        private static String concat(String s1, String s2) {
            return s2 == null || s2.isEmpty() ? s1 : s1.isEmpty() ? s2 : s1 + ", " + s2;
        }

        public Set<Long> getHolidays() {
            return Collections.unmodifiableSet(holidays.keySet());
        }
    }
}
