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

import com.devexperts.qd.tools.module.EndpointConfig;

import java.util.Objects;
import java.util.Properties;
import javax.annotation.Nonnull;

/**
 * Default {@link EndpointConfig} implementation used by Launcher
 */
class EndpointConfigImpl implements EndpointConfig {

    private final Properties endpointProps;

    public EndpointConfigImpl(@Nonnull Properties endpointProps) {
        this.endpointProps = Objects.requireNonNull(endpointProps);
    }

    @Override
    @Nonnull
    public Properties getProperties() {
        return endpointProps;
    }
}
