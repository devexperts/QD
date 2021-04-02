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

import java.io.File;

/**
 * Used in {@link FileReader} and {@link FileWriterImpl} to get a list of files ordered not by their name
 * but by their actual date.
 */
class TimestampedFile implements Comparable<TimestampedFile> {
    final File file;
    final String address;
    final long time;

    TimestampedFile(File file, long time) {
        this.file = file;
        this.address = file.toURI().toString();
        this.time = time;
    }

    @Override
    public int compareTo(TimestampedFile that) {
        return Long.valueOf(time).compareTo(that.time);
    }

    @Override
    public String toString() {
        return address;
    }
}
