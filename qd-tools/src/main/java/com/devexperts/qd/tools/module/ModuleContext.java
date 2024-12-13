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

import javax.annotation.Nonnull;

/**
 * Represents an execution environment provided to a {@link ModuleFactory} and passed to a created {@link Module}.
 */
@Experimental
public interface ModuleContext {

    /**
     * Returns the event logging facility should be used by the module.
     *
     * @return the event logging facility should be used by the module.
     */
    @Nonnull
    public EventLog getEventLog();

    /**
     * Returns endpoint configuration to be used by the module as defaults.
     * @return endpoint configuration
     */
    @Nonnull
    public EndpointConfig getEndpointConfig();

    /**
     * Returns the name of the module
     *
     * @return the name of the module
     */
    @Nonnull
    public String getName();
}

