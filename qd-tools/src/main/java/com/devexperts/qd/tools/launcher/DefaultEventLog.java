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
package com.devexperts.qd.tools.launcher;

import com.devexperts.logging.Logging;
import com.devexperts.qd.tools.module.EventLog;
import com.devexperts.qd.tools.module.LogEntry;
import com.devexperts.qd.tools.reporting.HtmlReportBuilder;
import com.devexperts.qd.tools.reporting.ReportBuilder;
import com.devexperts.qd.tools.reporting.ReportUtil;
import com.devexperts.util.TimeFormat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.logging.Level;
import javax.annotation.Nonnull;

/**
 * Default {@link EventLog} implementation used by Launcher.
 * An instance handles tagged events associated with some unit of execution (service, module).
 * Registered events are logged-through and stored inside the internal buffer.
 * <p>
 * Collected events may be retrieved later in different ways for the sake of reporting.
 */
@SuppressWarnings("WeakerAccess")
class DefaultEventLog implements EventLog {
    private final Logging log;
    private final String moduleName;
    private final Queue<LogEntry> logEntries = new ArrayDeque<>();
    private final int limit;

    public DefaultEventLog(String moduleName, int limit) {
        if (limit <= 0)
            throw new IllegalArgumentException("limit must be a positive number");
        this.moduleName = moduleName;
        this.limit = limit;
        // FIXME: consider other naming schemes
        log = Logging.getLogging("events." + moduleName);
    }

    public String getModuleName() {
        return moduleName;
    }

    @Override
    public Logging getLogging() {
        return log;
    }

    public String reportEventLog(String regex) {
        try {
            HtmlReportBuilder reportBuilder = new HtmlReportBuilder();
            reportEventLog(reportBuilder);
            return reportBuilder.buildFilteredHtmlReport(getModuleName(), regex);
        } catch (RuntimeException e) {
            log.error("Unexpected error", e);
            throw e;
        }
    }

    @Override
    public void reportEventLog(ReportBuilder reportBuilder) {
        Iterable<LogEntry> entries = getLogEvents();
        List<String> fields = getReportFields(entries);
        ArrayList<String> header = new ArrayList<>(fields);
        header.add(0, "Time");
        header.add("Message");
        reportBuilder.addHeaderRow(header);
        for (LogEntry entry : entries) {
            ArrayList<Object> row = new ArrayList<>(header.size());
            row.add(TimeFormat.DEFAULT.format(entry.getTimestamp()));
            Object event = entry.getEvent();
            ReportUtil.EventIntrospector introspector = ReportUtil.getIntrospector(event);
            for (String field1 : fields) {
                row.add(introspector.getAttr(event, field1));
            }
            row.add(introspector.getMessage(event));
            reportBuilder.addRow(row);
        }
    }

    private static List<String> getReportFields(Iterable<LogEntry> entries) {
        LinkedHashSet<String> fields = new LinkedHashSet<>();
        for (LogEntry entry : entries) {
            Object event = entry.getEvent();
            ReportUtil.EventIntrospector introspector = ReportUtil.getIntrospector(event);
            fields.addAll(introspector.attrNames(event));
        }
        return new ArrayList<>(fields);
    }

    @Override
    public synchronized void log(Level level, @Nonnull Object event) {
        ensureCapacity();
        logEntries.add(new LogEntryImpl(System.currentTimeMillis(), level, event));
        // FIXME: current logging facility doesn't provide a way of propagating time stamp
        logEventInternal(level, event);
    }

    private void ensureCapacity() {
        while (logEntries.size() >= limit) {
            logEntries.remove();
        }
    }

    private void logEventInternal(Level level, Object event) {
        if (Level.SEVERE.intValue() <= level.intValue()) {
            log.error(event.toString());
        } else if (Level.WARNING.intValue() <= level.intValue()) {
            log.warn(event.toString());
        } else {
            log.info(event.toString());
        }
    }

    public synchronized List<LogEntry> getLogEvents() {
        return Collections.unmodifiableList(new ArrayList<>(logEntries));
    }

}
