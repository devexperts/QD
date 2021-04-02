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
package com.devexperts.qd.qtp;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.TimeFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class HeartbeatPayload {
    private static final String TIME_MILLIS_KEY = "time=";
    private static final String TIME_MARK_KEY = "mark=";
    private static final String DELTA_MARK_KEY = "delta=";
    private static final String LAG_MARK_KEY = "lag=";

    private static final int CONTENT_EMPTY = 0; // no heartbeat payload
    private static final int CONTENT_TIME_MILLIS = 1; // currentTimeMillis only
    private static final int CONTENT_TIME_MARK = 2;
    private static final int CONTENT_DELTA_MARK = 4;
    private static final int CONTENT_LAG_MARK = 8;

    private int contentBits;
    private long timeMillis;
    private int timeMark;
    private int deltaMark;
    private int lagMark;

    public boolean isEmpty() {
        return contentBits == CONTENT_EMPTY;
    }

    public void clear() {
        contentBits = CONTENT_EMPTY;
        timeMillis = 0;
        timeMark = 0;
        deltaMark = 0;
        lagMark = 0;
    }

    public void updateFrom(HeartbeatPayload other) {
        if (other.hasTimeMillis())
            setTimeMillis(other.timeMillis);
        if (other.hasTimeMark())
            setTimeMark(other.timeMark);
        if (other.hasDeltaMark())
            setDeltaMark(other.deltaMark);
        if (other.hasLagMark())
            setLagMark(other.lagMark);
    }

    public boolean hasTimeMillis() {
        return (contentBits & CONTENT_TIME_MILLIS) != 0;
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public void setTimeMillis(long timeMillis) {
        this.contentBits |= CONTENT_TIME_MILLIS;
        this.timeMillis = timeMillis;
    }

    public boolean hasTimeMark() {
        return (contentBits & CONTENT_TIME_MARK) != 0;
    }

    public int getTimeMark() {
        return timeMark;
    }

    public void setTimeMark(int timeMark) {
        this.contentBits |= CONTENT_TIME_MARK;
        this.timeMark = timeMark;
    }

    public boolean hasDeltaMark() {
        return (contentBits & CONTENT_DELTA_MARK) != 0;
    }

    public int getDeltaMark() {
        return deltaMark;
    }

    public void setDeltaMark(int deltaMark) {
        this.contentBits |= CONTENT_DELTA_MARK;
        this.deltaMark = deltaMark;
    }

    public boolean hasLagMark() {
        return (contentBits & CONTENT_LAG_MARK) != 0;
    }

    public int getLagMark() {
        return lagMark;
    }

    public void setLagMark(int lagMark) {
        this.contentBits |= CONTENT_LAG_MARK;
        this.lagMark = lagMark;
    }

    /**
     * Composes this heartbeat payload in compact binary format.
     */
    public void composeTo(BufferedOutput out) throws IOException {
        out.writeCompactInt(contentBits);
        if (hasTimeMillis())
            out.writeCompactLong(timeMillis);
        if (hasTimeMark())
            out.writeCompactInt(timeMark);
        if (hasDeltaMark())
            out.writeCompactInt(deltaMark);
        if (hasLagMark())
            out.writeCompactInt(lagMark);
    }

    /**
     * Parses heartbeat payload from compact binary format.
     */
    public void parseFrom(BufferedInput in) throws IOException {
        contentBits = in.readCompactInt();
        if (hasTimeMillis())
            timeMillis = in.readCompactLong();
        if (hasTimeMark())
            timeMark = in.readCompactInt();
        if (hasDeltaMark())
            deltaMark = in.readCompactInt();
        if (hasLagMark())
            lagMark = in.readCompactInt();
    }

    /**
     * Converts heartbeat payload to a list of strings for representation in tab or comma separated text format.
     */
    public List<String> convertToTextTokens() {
        ArrayList<String> tokens = new ArrayList<String>();
        if (hasTimeMillis())
            tokens.add(TIME_MILLIS_KEY + TimeFormat.DEFAULT.withMillis().withTimeZone().format(timeMillis));
        if (hasTimeMark())
            tokens.add(TIME_MARK_KEY + timeMark);
        if (hasDeltaMark())
            tokens.add(DELTA_MARK_KEY + deltaMark);
        if (hasLagMark())
            tokens.add(LAG_MARK_KEY + lagMark);
        return tokens;
    }

    /**
     * Adds information to this heartbeat payload from a list of strings that are parsed
     * from tab or comma separated text format. Unrecognized tokens are ignored.
     *
     * @throws InvalidFormatException if recognized tokens have bad format.
     */
    public void appendFromTextTokens(List<String> tokens, int i) {
        while (i < tokens.size()) {
            String token = tokens.get(i++);
            if (token.startsWith(TIME_MILLIS_KEY))
                setTimeMillis(TimeFormat.DEFAULT.parse(token.substring(TIME_MILLIS_KEY.length())).getTime());
            else if (token.startsWith(TIME_MARK_KEY))
                setTimeMark(parseInt(TIME_MARK_KEY, token));
            else if (token.startsWith(DELTA_MARK_KEY))
                setDeltaMark(parseInt(DELTA_MARK_KEY, token));
            else if (token.startsWith(LAG_MARK_KEY))
                setLagMark(parseInt(LAG_MARK_KEY, token));
        }
    }

    private static int parseInt(String key, String token) {
        try {
            return Integer.parseInt(token.substring(key.length()));
        } catch (NumberFormatException e) {
            throw new InvalidFormatException(e.getMessage(), e);
        }
    }


    @Override
    public String toString() {
        return convertToTextTokens().toString();
    }
}
