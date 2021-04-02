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
package com.devexperts.qd.kit;

import com.devexperts.io.BufferedInput;
import com.devexperts.io.BufferedOutput;
import com.devexperts.qd.SerialFieldType;
import com.devexperts.qd.ng.RecordCursor;
import com.devexperts.util.TimeFormat;

import java.io.IOException;

public class TimeMillisField extends CompactIntField {
    public TimeMillisField(int index, String name) {
        this(index, name, SerialFieldType.TIME_MILLIS.forNamedField(name));
    }

    public TimeMillisField(int index, String name, SerialFieldType serialType) {
        super(index, name, serialType);
        if (!serialType.hasSameRepresentationAs(SerialFieldType.TIME_MILLIS))
            throw new IllegalArgumentException("Invalid serialType: " + serialType);
    }

    @Override
    public String getString(RecordCursor cursor) {
        return toStringLong(cursor.getLong(getIndex()));
    }

    @Override
    public void setString(RecordCursor cursor, String value) {
        cursor.setLong(getIndex(), parseStringLong(value));
    }

    @Override
    public void write(BufferedOutput out, RecordCursor cursor) throws IOException {
        out.writeCompactLong(cursor.getLong(getIndex()));
    }

    @Override
    public void read(BufferedInput in, RecordCursor cursor) throws IOException {
        cursor.setLong(getIndex(), in.readCompactLong());
    }

    @Override
    public String toString(int value) {
        return toStringLong(value);
    }

    @Override
    public int parseString(String value) {
        return (int) parseStringLong(value);
    }

    @Override
    public double toDouble(int value) {
        return value;
    }

    @Override
    public int toInt(double value) {
        return (int) value;
    }

    protected String toStringLong(long value) {
        return TimeFormat.DEFAULT.withTimeZone().withMillis().format(value);
    }

    protected long parseStringLong(String value) {
        return TimeFormat.DEFAULT.parse(value).getTime();
    }
}
