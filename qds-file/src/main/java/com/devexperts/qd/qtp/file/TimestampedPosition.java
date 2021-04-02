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
package com.devexperts.qd.qtp.file;

import com.devexperts.util.TimeFormat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * A pair of time and position with methods to read and written them from/to timestamps file.
 */
class TimestampedPosition {
    private final long time;
    private final long position;

    TimestampedPosition(long time, long position) {
        this.time = time;
        this.position = position;
    }

    public long getTime() {
        return time;
    }

    public long getPosition() {
        return position;
    }

    void writeTo(PrintWriter out, TimestampsType type) {
        if (out == null || type == TimestampsType.NONE)
            return;
        if (type == TimestampsType.LONG) {
            out.print(time);
        } else {
            out.print(TimeFormat.DEFAULT.withTimeZone().withMillis().format(time));
        }
        out.print(':');
        out.println(position);
    }

    static TimestampedPosition readFrom(BufferedReader in) throws IOException {
        if (in == null)
            return null;
        String line;
        int semicolon;
        do {
            line = in.readLine();
            if (line == null)
                return null;
            semicolon = line.lastIndexOf(':');
        } while (semicolon < 0);
        long time = TimeFormat.DEFAULT.parse(line.substring(0, semicolon)).getTime();
        long position = Long.parseLong(line.substring(semicolon + 1));
        return new TimestampedPosition(time, position);
    }

    public String toString() {
        return TimeFormat.DEFAULT.withTimeZone().withMillis().format(time) + ":" + position;
    }
}
