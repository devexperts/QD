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
import com.devexperts.qd.tools.module.Module;
import com.devexperts.qd.tools.module.ModuleContext;
import com.devexperts.qd.tools.module.StateReportingSupport;
import com.devexperts.qd.tools.reporting.ReportBuilder;

public class TestModule implements Module<TestModuleConfig>, StateReportingSupport {

    public final Logging log;

    public enum State { NEW, ACTIVE, FINISHED, CLOSED }

    public enum Event { VALIDATE, INIT, START, RECONFIGURE, CLOSE }

    public final String name;
    public final ModuleContext context;

    volatile State state = State.NEW;
    volatile TestModuleConfig config;

    public TestModule(ModuleContext context) {
        this.context = context;
        name = context.getName();
        log = Logging.getLogging("TestModule" + "-" + name);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object validate(TestModuleConfig config) {
        trace(Event.VALIDATE, config);
        return null;
    }

    @Override
    public void start(TestModuleConfig config) {
        trace(Event.START, config);
        if (state != State.NEW)
            throw new IllegalStateException("Initialized state expected");
        state = State.ACTIVE;
    }

    @Override
    public void reconfigure(TestModuleConfig config) {
        trace(Event.RECONFIGURE, config);
        if (state != State.ACTIVE)
            throw new IllegalStateException("State " + state + " is not active");
    }

    @Override
    public boolean isActive() {
        return state == State.ACTIVE;
    }

    @Override
    public void close() {
        trace(Event.CLOSE, null);
        state = State.CLOSED;
    }

    public void trace(Event event, Object data) {
        log.debug("trace:" + event + (data == null ? "" : "[" + data + "]"));
    }

    @Override
    public void reportCurrentState(ReportBuilder report) {
        report.addHeaderRow("Status");
        report.addRow("Current state: " + state + ", config = " + config);
    }
}
