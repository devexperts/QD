/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2026 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.qd.hub;

import com.devexperts.qd.tools.module.EventLog;
import com.devexperts.qd.tools.module.StructuredLogging;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hub {@link StructuredLogging} support extension
 */
public class Log extends StructuredLogging {

    public Log(EventLog eventLog) {
        super(eventLog);
    }

    public static class Action extends StructuredLogging.Action {
        protected Action(String tag) { super(tag); }

        public static final Action PASSIVATE = new Action("passivate");
        public static final Action RESOLVE = new Action("resolve");
        public static final Action START = new Action("start");
        public static final Action STOP = new Action("stop");
    }

    public static class Subject extends StructuredLogging.Subject {
        protected Subject(String tag) { super(tag); }

        public static final Subject SPACE = new Subject("space");
        public static final Subject PRODUCT = new Subject("product");
        public static final Subject CHANNEL = new Subject("channel");
        public static final Subject UPLINK = new Subject("uplink");
        public static final Subject DOWNLINK = new Subject("downlink");
    }

    // ========== Report building ==========

    public static <T> List<List<String>> convert(Subject what, Map<String, T> map) {
        return map.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(e -> Arrays.asList(what.tag(), e.getKey(), String.valueOf(e.getValue())))
            .collect(Collectors.toList());
    }
}
