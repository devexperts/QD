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
import com.devexperts.qd.tools.module.EndpointConfig;
import com.devexperts.qd.tools.module.Module;
import com.devexperts.qd.tools.module.ModuleContext;
import com.devexperts.qd.tools.module.ModuleFactory;
import com.devexperts.qd.tools.module.StateReportingSupport;
import com.devexperts.qd.tools.module.StructuredLogging;
import com.devexperts.qd.tools.reporting.HtmlReportBuilder;

import java.util.Objects;
import java.util.Properties;
import javax.annotation.Nonnull;

import static com.devexperts.qd.tools.module.StructuredLogging.Action.ADD;
import static com.devexperts.qd.tools.module.StructuredLogging.Action.REMOVE;
import static com.devexperts.qd.tools.module.StructuredLogging.Action.UPDATE;
import static com.devexperts.qd.tools.module.StructuredLogging.Subject.MODULE;

/**
 * ModuleContext implementation used by the Launcher
 *
 * @apiNote a container almost completely encapsulates the managed module inside. It's expected that launcher avoids
 *     direct interaction with the managed module.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
final class ModuleContainer implements ModuleContext {

    // TODO: it's unclear for the moment if explicit state is useful
    // enum State { NEW, ACTIVE, CLOSED }

    private static final Logging log = Logging.getLogging(ModuleContainer.class);

    private final DefaultEventLog eventLog;
    private final ModuleFactory factory;
    private final EndpointConfig endpointConfig;
    private final String name;
    private final String type;
    private Object config; // module configuration bean
    private Module module;
    // private State state = State.NEW;

    public ModuleContainer(String name, Properties endpointProps, ModuleFactory factory) {
        this.name = Objects.requireNonNull(name);
        endpointConfig = new EndpointConfigImpl(endpointProps);
        this.factory = Objects.requireNonNull(factory);
        this.type = factory.getType();
        this.eventLog = new DefaultEventLog(name, Launcher.MODULE_EVENT_LOG_LIMIT);
    }

    @Override
    @Nonnull
    public DefaultEventLog getEventLog() {
        return eventLog;
    }

    @Override
    @Nonnull
    public EndpointConfig getEndpointConfig() {
        return endpointConfig;
    }

    // public State getState() {
    //     return state;
    // }

    public Object getConfig() {
        return config;
    }

    public Module getModule() {
        return module;
    }

    public boolean isActive() {
        return module.isActive();
    }

    public String getType() {
        return type;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    // ======== module management ops ========

    public void createModule() {
        module = factory.createModule(this);
    }

    public void start(Object config) {
        boolean started = false;
        try {
            module.start(config);
            started = true;
        } finally {
            if (!started)
                module.close();
        }
        // FIXME: shall we report an event before initialization?
        logEvent(ADD, "Initialized module " + type);
    }

    public void reconfigure(Object config) {
        logEvent(UPDATE, "Updating configuration for module " + type);
        module.reconfigure(config);
        this.config = config;
    }

    public Object validate(Object config) {
        return module.validate(config);
    }

    public void closeModule() {
        if (module == null)
            return;
        log.info("Closing module " + name + " ...");
        module.close();
        logEvent(REMOVE, "Closed module " + type);
    }

    public String reportCurrentState(String regex) {
        try {
            HtmlReportBuilder reportBuilder = new HtmlReportBuilder();
            if (module instanceof StateReportingSupport) {
                ((StateReportingSupport) module).reportCurrentState(reportBuilder);
                return reportBuilder.buildFilteredHtmlReport(getName(), regex);
            } else {
                return "<h1>" + getName() + "</h1><p>[UNKNOWN]</p>";
            }
        } catch (RuntimeException e) {
            log.error("Unexpected error", e);
            throw e;
        }
    }

    private void logEvent(StructuredLogging.Action action, String message) {
        StructuredLogging.log(eventLog, action, MODULE, name, message);
    }
}
