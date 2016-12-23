/*
 * QDS - Quick Data Signalling Library
 * Copyright (C) 2002-2016 Devexperts LLC
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 */
package com.devexperts.logging;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.logging.Formatter;
import java.util.logging.*;

import com.devexperts.util.ArrayUtil;
import com.devexperts.util.TimeUtil;

/**
 * Formatter for log messages.
 * It is used for formatting both log4j and {@link java.util.logging} log messages.
 * Performs conversion of thread names according to patterns specified in configuration file.
 * <p>
 * If the system property <code>logformatter.properties</code> is specified, then it should contain
 * an URL to the configuration file. Otherwise, configuration is loaded from classpath, using
 * <i>/META-INF/logformatter.properties</i> file.
 * <p>
 * The format of the file is:
 * <ul>
 * <li>pattern=replacement
 * <li>"Pattern" uses regular expression syntax.
 * You can escape "=" in pattern with "\=" syntax.
 * <li>"Replacement" string can refer to capturing groups defined in pattern using usual
 * regular expression syntax "$n", where "n" stands for the number of the group.
 * <li>ISO 8859-1 encoding is used.
 * <li>Empty lines and lines starting with # or ! are ignored.
 * Lines containing wrong patterns are ignored.
 * </ul>
 * Configuration file is loaded during class loading.
 * Any errors which occur in this class are printed in <code>System.err</code>.
 * <p>
 * Sample configuration file can be found in <i>etc/logformatter.properties</i>.
 * <p>
 * This class is not intended to be used standalone.
 * It is a part of implementation of {@link com.devexperts.logging} package.
 *
 * @see DetailedLogLayout
 */
public class LogFormatter extends Formatter {
    public static final String CONFIG_FILE_PROPERTY = "logformatter.properties";
    public static final String DEFAULT_CONFIG_FILE = "/META-INF/logformatter.properties";

    private static final String LINE_SEP = DefaultLogging.getProperty("line.separator", "\n");

    // ============== Instance ================

    private final Calendar calendar;
    private final char[] originalBuffer = new char[1000]; // Original fixed-size buffer for reuse.

    private long translatedMinute;
    private char[] buffer; // "P yyMMdd HHmmss.SSS <remaining message>"
    private int position;

    public LogFormatter() {
        this(TimeZone.getDefault());
    }

    public LogFormatter(TimeZone zone) {
        calendar = Calendar.getInstance(zone);
        Arrays.fill(originalBuffer, 0, 20, ' ');
        originalBuffer[15] = '.';
    }

    /**
     * Used by {@link java.util.logging} logging.
     * Formats messages with the same format as for log4j.
     */
    @Override
    public String format(LogRecord record) {
        String s = format(getLevelChar(record.getLevel()),
            record.getMillis(), Thread.currentThread().getName(),
            record.getLoggerName(), formatMessage(record));
        if (record.getThrown() != null) {
            StringWriter sw = new StringWriter();
            sw.write(s);
            record.getThrown().printStackTrace(new PrintWriter(sw));
            s = sw.toString();
        }
        return s;
    }

    /**
     * Formats log message.
     *
     * @return Formatted message.
     * @throws NullPointerException when threadName, loggerName, or msg are <code>null</code>.
     */
    public synchronized String format(char levelChar, long time, String threadName, String loggerName, String msg) {
        buffer = originalBuffer; // Re-init buffer every time in case of unexpected errors during previous executions.
        buffer[0] = levelChar;

        if (time < translatedMinute || time >= translatedMinute + TimeUtil.MINUTE) {
            // set year, month, day, hour and minute
            calendar.setTimeInMillis(time);
            translatedMinute = calendar.getTime().getTime() - calendar.get(Calendar.SECOND) * 1000 - calendar.get(Calendar.MILLISECOND);
            print2(2, calendar.get(Calendar.YEAR));
            print2(4, calendar.get(Calendar.MONTH) + 1);
            print2(6, calendar.get(Calendar.DAY_OF_MONTH));
            print2(9, calendar.get(Calendar.HOUR_OF_DAY));
            print2(11, calendar.get(Calendar.MINUTE));
        }

        // set seconds and milliseconds
        int millis = (int) (time - translatedMinute);
        print2(13, millis / 1000);
        print2(16, millis / 10);
        buffer[18] = (char) ('0' + millis % 10);
        position = 20;

        if (msg == null)
            msg = "null";
        if (msg.length() >= 1 && msg.charAt(0) == '\b') {
            // First backspace is a signal to skip thread name and logger name output
            append(msg, 1);
        } else {
            append("[");
            append(ThreadNameFormatter.formatThreadName(time, threadName));
            append("] ");
            append(loggerName, loggerName.lastIndexOf('.') + 1);
            append(" - ");
            append(msg);
        }
        append(LINE_SEP);

        String s = new String(buffer, 0, position);
        buffer = originalBuffer; // Forget temporary buffer - it might have grown too large to keep.
        return s;
    }

    private void print2(int offset, int value) {
        buffer[offset] = (char) ('0' + (value / 10) % 10);
        buffer[offset + 1] = (char) ('0' + value % 10);
    }

    private void append(String s) {
        append(s, 0);
    }

    private void append(String s, int begin_index) {
        int end_index = s.length();
        int new_position = position + end_index - begin_index;
        if (buffer.length < new_position)
            buffer = ArrayUtil.grow(buffer, new_position);
        s.getChars(begin_index, end_index, buffer, position);
        position = new_position;
    }

    static char getLevelChar(Level level) {
        int levelInt = level.intValue();
        if (levelInt <= Level.FINEST.intValue())
            return 'T';
        if (levelInt <= Level.FINE.intValue())
            return 'D';
        if (levelInt <= Level.INFO.intValue())
            return 'I';
        if (levelInt <= Level.WARNING.intValue())
            return 'W';
        return 'E';
    }
}
