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
 * <b>Session</b> represents a continuous period of time during which apply same rules regarding trading activity.
 * For example, <code>regular trading session</code> is a period of time consisting of one day of business activities
 * in a financial market, from the opening bell to the closing bell, when regular trading occurs.
 * <p>
 * Sessions can be either <b>trading</b> or <b>non-trading</b>, with different sets of rules and reasons to exist.
 * Sessions do not overlap with each other - rather they form consecutive chain of adjacent periods of time that
 * cover entire time scale. The point on a border line is considered to belong to following session that starts there.
 * Each session completely fits inside a certain day. Day may contain sessions with zero duration - e.g. indices
 * that post value once a day. Such sessions can be of any appropriate type, trading or non-trading.
 */
public final class Session {

    private final Day day;
    private final SessionType type;
    private final long startTime;
    private final long endTime;

    Session(Day day, SessionType type, long startTime, long endTime) {
        this.day = day;
        this.type = type;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * Returns day to which this session belongs.
     */
    public Day getDay() {
        return day;
    }

    /**
     * Returns type of this session.
     */
    public SessionType getType() {
        return type;
    }

    /**
     * Returns <code>true</code> if trading activity is allowed within this session.
     * This method is equivalent to expression {@link SessionType#isTrading() getType().isTrading()}.
     * <p>
     * Some sessions may have zero duration - e.g. indices that post value once a day.
     * Such sessions can be of any appropriate type, trading or non-trading.
     */
    public boolean isTrading() {
        return type.isTrading();
    }

    /**
     * Returns <code>true</code> if this session has zero duration.
     * Empty sessions can be used for indices that post value once a day or for convenience.
     * Such sessions can be of any appropriate type, trading or non-trading.
     */
    public boolean isEmpty() {
        return startTime >= endTime;
    }

    /**
     * Returns start time of this session (inclusive).
     * For normal sessions the start time is less than the end time, for empty sessions they are equal.
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Returns end time of this session (exclusive).
     * For normal sessions the end time is greater than the start time, for empty sessions they are equal.
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * Returns <code>true</code> if specified time belongs to this session.
     */
    public boolean containsTime(long time) {
        return time >= startTime && time < endTime;
    }

    /**
     * Returns previous session accepted by specified filter.
     * This method may cross the day boundary and return appropriate session from
     * previous days - up to a year back in time. If no such session was found
     * within one year this method will throw {@link NoSuchElementException}.
     * <p>
     * To find previous trading session of any type use this code:
     * <pre>session = session.getPrevSession(SessionFilter.TRADING);</pre>
     * To find previous regular trading session use this code:
     * <pre>session = session.getPrevSession(SessionFilter.REGULAR);</pre>
     *
     * @param filter the filter to test sessions
     * @return nearest previous session that is accepted by the filter
     * @throws NoSuchElementException if no such session was found within one year
     */
    public Session getPrevSession(SessionFilter filter) {
        Session session = findPrevSession(filter);
        if (session != null)
            return session;
        throw new NoSuchElementException("could not find prev session before " + day.getYearMonthDay() + " for " + filter);
    }

    /**
     * Returns following session accepted by specified filter.
     * This method may cross the day boundary and return appropriate session from
     * following days - up to a year in the future. If no such session was found
     * within one year this method will throw {@link NoSuchElementException}.
     * <p>
     * To find following trading session of any type use this code:
     * <pre>session = session.getNextSession(SessionFilter.TRADING);</pre>
     * To find following regular trading session use this code:
     * <pre>session = session.getNextSession(SessionFilter.REGULAR);</pre>
     *
     * @param filter the filter to test sessions
     * @return nearest following session that is accepted by the filter
     * @throws NoSuchElementException if no such session was found within one year
     */
    public Session getNextSession(SessionFilter filter) {
        Session session = findNextSession(filter);
        if (session != null)
            return session;
        throw new NoSuchElementException("could not find next session after " + day.getYearMonthDay() + " for " + filter);
    }

    /**
     * Returns previous session accepted by specified filter.
     * This method may cross the day boundary and return appropriate session from
     * previous days - up to a year back in time. If no such session was found
     * within one year this method will return <b>null</b>.
     * <p>
     * To find previous trading session of any type use this code:
     * <pre>session = session.findPrevSession(SessionFilter.TRADING);</pre>
     * To find previous regular trading session use this code:
     * <pre>session = session.findPrevSession(SessionFilter.REGULAR);</pre>
     *
     * @param filter the filter to test sessions
     * @return nearest previous session that is accepted by the filter
     */
    public Session findPrevSession(SessionFilter filter) {
        List<Session> sessions = day.getSessions();
        for (int i = sessions.size(); --i >= 0;)
            if (sessions.get(i) == this) {
                while (--i >= 0)
                    if (filter.accept(sessions.get(i)))
                        return sessions.get(i);
                break;
            }
        for (int k = 1; k <= 366; k++) {
            sessions = day.getSchedule().getDayById(day.getDayId() - k).getSessions();
            for (int i = sessions.size(); --i >= 0;)
                if (filter.accept(sessions.get(i)))
                    return sessions.get(i);
        }
        return null;
    }

    /**
     * Returns following session accepted by specified filter.
     * This method may cross the day boundary and return appropriate session from
     * following days - up to a year in the future. If no such session was found
     * within one year this method will return <b>null</b>.
     * <p>
     * To find following trading session of any type use this code:
     * <pre>session = session.findNextSession(SessionFilter.TRADING);</pre>
     * To find following regular trading session use this code:
     * <pre>session = session.findNextSession(SessionFilter.REGULAR);</pre>
     *
     * @param filter the filter to test sessions
     * @return nearest following session that is accepted by the filter
     */
    public Session findNextSession(SessionFilter filter) {
        List<Session> sessions = day.getSessions();
        for (int i = 0, n = sessions.size(); i < n; i++)
            if (sessions.get(i) == this) {
                while (++i < n)
                    if (filter.accept(sessions.get(i)))
                        return sessions.get(i);
                break;
            }
        for (int k = 1; k <= 366; k++) {
            sessions = day.getSchedule().getDayById(day.getDayId() + k).getSessions();
            for (int i = 0, n = sessions.size(); i < n; i++)
                if (filter.accept(sessions.get(i)))
                    return sessions.get(i);
        }
        return null;
    }

    public int hashCode() {
        return day.hashCode() + type.hashCode() + (int) startTime + (int) endTime;
    }

    public boolean equals(Object object) {
        if (!(object instanceof Session))
            return false;
        Session session = (Session) object;
        return day.equals(session.day) && type == session.type && startTime == session.startTime && endTime == session.endTime;
    }

    public String toString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return "Session(" + day.getYearMonthDay() + ", " + type + ", " + isTrading() + ", " + sdf.format(startTime) + ", " + sdf.format(endTime) + ")";
    }
}
