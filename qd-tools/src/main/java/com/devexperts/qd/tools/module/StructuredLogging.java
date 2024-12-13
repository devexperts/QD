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
package com.devexperts.qd.tools.module;

import com.devexperts.annotation.Experimental;

import java.util.Objects;
import java.util.logging.Level;
import javax.xml.bind.annotation.XmlType;

/**
 * Basic structured logging support for {@link com.devexperts.qd.tools.launcher.Launcher Launcher} infrastructure.
 * It wraps {@link EventLog} instance and provides a simple type-safer facade of emitting events consisting of
 * <ul>
 *     <li>{@link Action Action} - a classifier denoting a type of change (add/update/remove/...)
 *     <li>{@link Subject Subject} - a classifier of affected object (module/connector/...)
 *     <li>name of the subject
 *     <li>a free-form message describing event
 * </ul>
 *
 * Action and Subject define normalized sets of typed "tags" values and designed to be extended by API users.
 *
 * @apiNote The aim of the facade to provide an easy API for logging module lifecycle related events
 *      in a structured way useful for a table-like reporting using a "normalized" dictionary for main attributes.
 */
// FIXME: better name?
@Experimental
public class StructuredLogging {
    protected final EventLog eventLog;

    public StructuredLogging(EventLog eventLog) { this.eventLog = eventLog; }

    public static void log(EventLog log, Action action, Subject what, String name, Object message) {
        log.log(action == Action.WARNING ? Level.WARNING : Level.INFO,
            new Event(action, what, name, Objects.toString(message, "")));
    }

    public void log(Action action, Subject what, String name, Object message) {
        log(eventLog, action, what, name, message);
    }

    // Primitive tagged string object.
    public static class Tag {
        private final String tag;

        public Tag(String tag) { this.tag = tag; }

        public String tag() { return tag; }

        @Override
        public String toString() { return tag; }
    }

    public static class Action extends Tag {
        protected Action(String tag) { super(tag); }

        public static final Action ADD = new Action("add");
        public static final Action UPDATE = new Action("update");
        public static final Action REMOVE = new Action("remove");
        public static final Action WARNING = new Action("WARNING");
    }

    public static class Subject extends Tag {
        public Subject(String tag) { super(tag); }

        public static final Subject MODULE = new Subject("module");
    }

    @XmlType(propOrder = {"action", "what", "name", "message"})
    public static class Event {
        private final Action action;
        private final Subject what;
        private final String name;
        private final String message;

        public Event(Action action, Subject what, String name, String message) {
            this.action = action;
            this.what = what;
            this.name = name;
            this.message = message;
        }

        @Override
        public String toString() {
            return action + " " + what + " " + name + " " + message;
        }

        public Action getAction() { return action; }

        public Subject getWhat() { return what; }

        public String getName() { return name; }

        public String getMessage() { return message; }
    }
}
