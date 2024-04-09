/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2024 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.qtp.file;

import com.devexperts.util.InvalidFormatException;
import com.devexperts.util.TimeFormat;
import com.devexperts.util.TimeUtil;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

class TimestampedFilenameFilter implements FilenameFilter {
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\d{8}-\\d{6}[+-]\\d{4}");

    private final File directory;
    private final String fileNamePrefix;
    private final String containerExtension;
    private final String fullSuffix;

    private long startTime = Long.MIN_VALUE;
    private long prevTime = Long.MIN_VALUE;
    private long stopTime = Long.MAX_VALUE;
    private long lastTimeForPath = 0; // see getPathForTime
    private boolean requireTimeFile;

    /**
     * Returns new timestamped file name filter or {@code null} if file does not
     * have {@link FileUtils#TIMESTAMP_MARKER} in its name or has multiple timestamp markers there.
     */
    public static TimestampedFilenameFilter create(File file, String containerExtension) {
        String shortName = file.getName();
        if (!shortName.endsWith(containerExtension))
            throw new IllegalArgumentException();
        shortName = shortName.substring(0, shortName.length() - containerExtension.length());
        int timestampPos = shortName.indexOf(FileUtils.TIMESTAMP_MARKER);
        if (timestampPos == -1)
            return null;
        String prefix = shortName.substring(0, timestampPos);
        String suffix = shortName.substring(timestampPos + FileUtils.TIMESTAMP_MARKER.length());
        // Ensure there is only one timestamp label in filename
        int anotherTimestampPos = suffix.indexOf(FileUtils.TIMESTAMP_MARKER);
        if (anotherTimestampPos != -1)
            throw new InvalidFormatException("There can be only one timestamp label in file name");
        // initialize reading of multiple files
        File dir = file.getParentFile();
        return new TimestampedFilenameFilter(dir == null ? new File(".") : dir, prefix, suffix, containerExtension);
    }

    private TimestampedFilenameFilter(File directory, String fileNamePrefix, String fileNameSuffix,
        String containerExtension)
    {
        this.directory = directory;
        this.fileNamePrefix = fileNamePrefix;
        this.containerExtension = containerExtension;
        this.fullSuffix = fileNameSuffix + containerExtension;
    }

    // WARNING: It works only with good files!!!
    private static long getDateFromFile(File file, TimestampedFilenameFilter filter) {
        String fileName = file.getName();
        String dateStr = fileName.substring(filter.fileNamePrefix.length(), fileName.length() - filter.fullSuffix.length());
        return TimeFormat.DEFAULT.parse(dateStr).getTime();
    }

    /**
     * Returns path for tape file with specified time. If time is less or equals than previous,
     * then (previous + 1s) is used instead of specified. It makes reading data in recording order.
     */
    public String getPathForTime(long time) {
        lastTimeForPath = Math.max(lastTimeForPath + TimeUtil.SECOND, time);
        return new File(directory, fileNamePrefix + TimeFormat.DEFAULT.withTimeZone().format(lastTimeForPath) + fullSuffix).getPath();
    }

    /**
     * Returns sorted list of timestamped files.
     *
     * @return sorted list of timestamped files.
     */
    public TimestampedFile[] listTimestampedFiles() {
        File[] files = directory.listFiles(this);
        List<TimestampedFile> timestampedFiles = new ArrayList<>(files == null ? 0 : files.length);
        if (files != null)
            for (File file : files)
                timestampedFiles.add(new TimestampedFile(file, getDateFromFile(file, this)));
        Collections.sort(timestampedFiles);
        // find first file based on start time.
        int first = 0;
        for (TimestampedFile file : timestampedFiles) {
            if (file.time <= startTime)
                first++;
        }
        // previous file with time < startTime may contain part of the data
        if (first > 0)
            first--;
        // return everything beginning with first.
        List<TimestampedFile> timestampedFileList = timestampedFiles.subList(first, timestampedFiles.size());
        return timestampedFileList.toArray(new TimestampedFile[0]);
    }

    @Override
    public boolean accept(File directory, String name) {
        if (!name.startsWith(fileNamePrefix))
            return false;
        String rest = name.substring(fileNamePrefix.length());
        if (!rest.endsWith(fullSuffix))
            return false;
        rest = rest.substring(0, rest.length() - fullSuffix.length());
        // [QD-433] FileConnector shall not allow "~" to be replaced by anything short of full date time
        // Make sure rest is well-formed time specification like "YYYYMMDD-HHmmss[+-]ZZZZ"
        if (!TIMESTAMP_PATTERN.matcher(rest).matches())
            return false;
        try {
            Date time = TimeFormat.DEFAULT.parse(rest);
            if (time.getTime() <= prevTime || time.getTime() >= stopTime)
                return false;
        } catch (InvalidFormatException e) {
            return false;
        }
        if (requireTimeFile) {
            File timeFile = new File(directory, FileUtils.getTimeFilePath(name,
                FileUtils.retrieveExtension(name.substring(0, name.length() - containerExtension.length())), containerExtension));
            return timeFile.exists();
        }
        return true;
    }

    /**
     * Configures filter to find the first file that contains the requested start time and
     * all the files after that. Overrides previous calls to filterXXX method.
     */
    public void filterByStartTime(long startTime) {
        this.startTime = startTime;
        this.prevTime = Long.MIN_VALUE;
    }

    /**
     * Configures filter to find the file that goes after the file with a specified timestamp and also
     * contains the requested start time and all the files after that. Overrides previous calls to filterXXX method.
     */
    public void filterByStartAndPreviousFileTime(long startTime, long prevTime) {
        this.startTime = startTime;
        this.prevTime = prevTime;
    }

    /**
     * Configures filter to find the file that goes after the file with a specified timestamp and
     * all the files after that. Overrides previous calls to filterXXX method.
     */
    public void filterByPreviousFileTime(long prevTime) {
        this.prevTime = prevTime;
        this.startTime = Long.MIN_VALUE;
    }

    public void filterByStopTime(long stopTime) {
        this.stopTime = stopTime;
    }

    public void requireTimeFile() {
        requireTimeFile = true;
    }
}
