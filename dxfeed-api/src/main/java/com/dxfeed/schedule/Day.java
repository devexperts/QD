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
package com.dxfeed.schedule;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * <b>Day</b> represents a continuous period of time approximately 24 hours long. The day is aligned
 * to the start and the end of business activities of a certain business entity or business process.
 * For example, the day may be aligned to a trading schedule of a particular instrument on an exchange.
 * Thus, different days may start and end at various local times depending on the related trading schedules.
 * <p>
 * The length of the day depends on the trading schedule and other circumstances. For example, it is possible
 * that day for Monday is longer than 24 hours because it includes part of Sunday; consequently, the day for
 * Sunday will be shorter than 24 hours to avoid overlapping with Monday.
 * <p>
 * Days do not overlap with each other - rather they form consecutive chain of adjacent periods of time that
 * cover entire time scale. The point on a border line is considered to belong to following day that starts there.
 * <p>
 * Each day consists of sessions that cover entire duration of the day. If day contains at least one trading
 * session (i.e. session within which trading activity is allowed), then the day is considered trading day.
 * Otherwise the day is considered non-trading day (e.g. weekend or holiday).
 * <p>
 * Day may contain sessions with zero duration - e.g. indices that post value once a day.
 * Such sessions can be of any appropriate type, trading or non-trading.
 * Day may have zero duration as well - e.g. when all time within it is transferred to other days.
 */
public final class Day {

    private final Schedule schedule;
    private final int dayId;
    private final int yearMonthDay;
    private final boolean holiday;
    private final boolean shortDay;
    private final long resetTime;

    private boolean trading;
    private long startTime;
    private long endTime;
    private List<Session> sessions;

    long usageCounter;

    Day(Schedule schedule, int dayId, int yearMonthDay, boolean holiday, boolean shortDay, long resetTime) {
        this.schedule = schedule;
        this.dayId = dayId;
        this.yearMonthDay = yearMonthDay;
        this.holiday = holiday;
        this.shortDay = shortDay;
        this.resetTime = resetTime;
    }

    void setSessions(List<Session> sessions) {
        trading = false;
        for (int i = 0, n = sessions.size(); i < n; i++)
            if (sessions.get(i).isTrading())
                trading = true;
        startTime = sessions.get(0).getStartTime();
        endTime = sessions.get(sessions.size() - 1).getEndTime();
        this.sessions = sessions;
    }

    /**
     * Returns schedule to which this day belongs.
     */
    public Schedule getSchedule() {
        return schedule;
    }

    /**
     * Number of this day since January 1, 1970 (that day has identifier of 0 and previous days have negative identifiers).
     */
    public int getDayId() {
        return dayId;
    }

    /**
     * Returns year, month and day numbers decimally packed in the following way:
     * <pre>YearMonthDay = year * 10000 + month * 100 + day</pre>
     * For example, September 28, 1977 has value 19770928.
     */
    public int getYearMonthDay() {
        return yearMonthDay;
    }

    /**
     * Returns calendar year - i.e. it returns <code>1977</code> for the year <code>1977</code>.
     */
    public int getYear() {
        return yearMonthDay / 10000;
    }

    /**
     * Returns calendar month number in the year starting with <b>1=January</b> and ending with <b>12=December</b>.
     */
    public int getMonthOfYear() {
        return yearMonthDay / 100 % 100;
    }

    /**
     * Returns ordinal day number in the month starting with <b>1</b> for the first day of month.
     */
    public int getDayOfMonth() {
        return yearMonthDay % 100;
    }

    /**
     * Returns ordinal day number in the week starting with <b>1=Monday</b> and ending with <b>7=Sunday</b>.
     */
    public int getDayOfWeek() {
        return Schedule.dayOfWeek(dayId);
    }

    /**
     * Returns <code>true</code> if this day is an exchange holiday.
     * Usually there are no trading takes place on an exchange holiday.
     */
    public boolean isHoliday() {
        return holiday;
    }

    /**
     * Returns <code>true</code> if this day is a short day.
     * Usually trading stops earlier on a short day.
     */
    public boolean isShortDay() {
        return shortDay;
    }

    /**
     * Returns <code>true</code> if trading activity is allowed within this day.
     * Positive result assumes that day has at least one trading session.
     */
    public boolean isTrading() {
        return trading;
    }

    /**
     * Returns start time of this day (inclusive).
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Returns end time of this day (exclusive).
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * Returns <code>true</code> if specified time belongs to this day.
     */
    public boolean containsTime(long time) {
        return time >= startTime && time < endTime;
    }

    /**
     * Returns reset time for this day.
     * Reset of daily data is performed on trading days only, the result has no meaning for non-trading days.
     */
    public long getResetTime() {
        return resetTime;
    }

    /**
     * Returns list of sessions that constitute this day.
     * The list is ordered according to natural order of sessions - how they occur one after another.
     */
    public List<Session> getSessions() {
        return sessions;
    }

    /**
     * Returns session belonging to this day that contains specified time.
     * If no such session was found within this day this method will throw {@link NoSuchElementException}.
     *
     * @param time the time to search for
     * @return session that contains specified time
     * @throws NoSuchElementException if no such session was found within this day
     */
    public Session getSessionByTime(long time) {
        for (int i = 0, n = sessions.size(); i < n; i++)
            if (sessions.get(i).containsTime(time))
                return sessions.get(i);
        throw new NoSuchElementException("could not find session in " + yearMonthDay + " for " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time));
    }

    /**
     * Returns first session belonging to this day accepted by specified filter.
     * This method does not cross the day boundary. If no such session was found
     * within this day this method will throw {@link NoSuchElementException}.
     * <p>
     * To find first trading session of any type use this code:
     * <pre>Session session = day.getFirstSession(SessionFilter.TRADING);</pre>
     * To find first regular trading session use this code:
     * <pre>Session session = day.getFirstSession(SessionFilter.REGULAR);</pre>
     *
     * @param filter the filter to test sessions
     * @return first session that is accepted by the filter
     * @throws NoSuchElementException if no such session was found within this day
     */
    public Session getFirstSession(SessionFilter filter) {
        Session session = findFirstSession(filter);
        if (session != null)
            return session;
        throw new NoSuchElementException("could not find session in " + yearMonthDay + " for " + filter);
    }

    /**
     * Returns last session belonging to this day accepted by specified filter.
     * This method does not cross the day boundary. If no such session was found
     * within this day this method will throw {@link NoSuchElementException}.
     * <p>
     * To find last trading session of any type use this code:
     * <pre>Session session = day.getLastSession(SessionFilter.TRADING);</pre>
     * To find last regular trading session use this code:
     * <pre>Session session = day.getLastSession(SessionFilter.REGULAR);</pre>
     *
     * @param filter the filter to test sessions
     * @return last session that is accepted by the filter
     * @throws NoSuchElementException if no such session was found within this day
     */
    public Session getLastSession(SessionFilter filter) {
        Session session = findLastSession(filter);
        if (session != null)
            return session;
        throw new NoSuchElementException("could not find session in " + yearMonthDay + " for " + filter);
    }

    /**
     * Returns first session belonging to this day accepted by specified filter.
     * This method does not cross the day boundary. If no such session was found
     * within this day this method will return <b>null</b>.
     * <p>
     * To find first trading session of any type use this code:
     * <pre>Session session = day.findFirstSession(SessionFilter.TRADING);</pre>
     * To find first regular trading session use this code:
     * <pre>Session session = day.findFirstSession(SessionFilter.REGULAR);</pre>
     *
     * @param filter the filter to test sessions
     * @return first session that is accepted by the filter
     */
    public Session findFirstSession(SessionFilter filter) {
        for (int i = 0, n = sessions.size(); i < n; i++)
            if (filter.accept(sessions.get(i)))
                return sessions.get(i);
        return null;
    }

    /**
     * Returns last session belonging to this day accepted by specified filter.
     * This method does not cross the day boundary. If no such session was found
     * within this day this method will return <b>null</b>.
     * <p>
     * To find last trading session of any type use this code:
     * <pre>Session session = day.findLastSession(SessionFilter.TRADING);</pre>
     * To find last regular trading session use this code:
     * <pre>Session session = day.findLastSession(SessionFilter.REGULAR);</pre>
     *
     * @param filter the filter to test sessions
     * @return last session that is accepted by the filter
     */
    public Session findLastSession(SessionFilter filter) {
        for (int i = sessions.size(); --i >= 0;)
            if (filter.accept(sessions.get(i)))
                return sessions.get(i);
        return null;
    }

    /**
     * Returns previous day accepted by specified filter.
     * This method looks for appropriate day up to a year back in time. If no such day was found
     * within one year this method will throw {@link NoSuchElementException}.
     *
     * @param filter the filter to test days
     * @return nearest previous day that is accepted by the filter
     * @throws NoSuchElementException if no such day was found within one year
     */
    public Day getPrevDay(DayFilter filter) {
        Day day = findPrevDay(filter);
        if (day != null)
            return day;
        throw new NoSuchElementException("could not find prev day before " + yearMonthDay + " for " + filter);
    }

    /**
     * Returns following day accepted by specified filter.
     * This method looks for appropriate day up to a year in the future. If no such day was found
     * within one year this method will throw {@link NoSuchElementException}.
     *
     * @param filter the filter to test days
     * @return nearest following day that is accepted by the filter
     * @throws NoSuchElementException if no such day was found within one year
     */
    public Day getNextDay(DayFilter filter) {
        Day day = findNextDay(filter);
        if (day != null)
            return day;
        throw new NoSuchElementException("could not find next day after " + yearMonthDay + " for " + filter);
    }

    /**
     * Returns previous day accepted by specified filter.
     * This method looks for appropriate day up to a year back in time. If no such day was found
     * within one year this method will return <b>null</b>.
     *
     * @param filter the filter to test days
     * @return nearest previous day that is accepted by the filter
     */
    public Day findPrevDay(DayFilter filter) {
        for (int k = 1; k <= 366; k++) {
            Day day = schedule.getDayById(dayId - k);
            if (filter.accept(day))
                return day;
        }
        return null;
    }

    /**
     * Returns following day accepted by specified filter.
     * This method looks for appropriate day up to a year in the future. If no such day was found
     * within one year this method will return <b>null</b>.
     *
     * @param filter the filter to test days
     * @return nearest following day that is accepted by the filter
     */
    public Day findNextDay(DayFilter filter) {
        for (int k = 1; k <= 366; k++) {
            Day day = schedule.getDayById(dayId + k);
            if (filter.accept(day))
                return day;
        }
        return null;
    }

    public int hashCode() {
        return schedule.hashCode() + dayId;
    }

    public boolean equals(Object object) {
        if (!(object instanceof Day))
            return false;
        Day day = (Day) object;
        return schedule.equals(day.schedule) && dayId == day.dayId;
    }

    public String toString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return "Day(" + yearMonthDay + ", " + trading + ", " + sdf.format(startTime) + ", " + sdf.format(endTime) + ", " + sdf.format(resetTime) + ")";
    }
}
