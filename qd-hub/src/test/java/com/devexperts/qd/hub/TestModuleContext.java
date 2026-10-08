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

import com.devexperts.qd.tools.launcher.DefaultEventLog;
import com.devexperts.qd.tools.module.EndpointConfig;
import com.devexperts.qd.tools.module.EventLog;
import com.devexperts.qd.tools.module.ModuleContext;

import java.util.Objects;
import java.util.Properties;
import javax.annotation.Nonnull;

/**
 * {@link ModuleContext} implementation for tests.
 */
class TestModuleContext implements ModuleContext, EndpointConfig {

    private final String moduleName;
    private final EventLog eventLog;
    private final Properties endpointProperties;

    public TestModuleContext(String name, Properties endpointProperties) {
        moduleName = Objects.requireNonNull(name);
        eventLog = new DefaultEventLog(name, 100_000);
        this.endpointProperties = endpointProperties;
    }

    @Override
    @Nonnull
    public EventLog getEventLog() {
        return eventLog;
    }

    @Override
    @Nonnull
    public EndpointConfig getEndpointConfig() {
        return this;
    }

    @Nonnull
    @Override
    public String getName() {
        return moduleName;
    }

    // ======== EndpointConfig contract ========

    @Nonnull
    @Override
    public Properties getProperties() {
        return endpointProperties;
    }
}
