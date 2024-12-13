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
package com.devexperts.qd.config;

import com.devexperts.qd.config.hocon.HoconConfigProvider;
import com.typesafe.config.Config;

import java.util.Objects;
import javax.annotation.Nonnull;

/**
 * Main entry point for configuration support.
 */
public class ConfigProvider {

    static final HoconConfigProvider IMPL = new HoconConfigProvider();

    /**
     * Maps provided raw configuration node to the specified beanType using default mapping infrastructure.
     *
     * @param config configuration node
     * @param beanType expected configuration bean type
     *
     * @return configuration bean constructed from provided configuration
     */
    @Nonnull
    public static <T> T getConfigBean(Config config, Class<T> beanType) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(beanType, "beanType");
        return IMPL.getConfigBean(config, beanType);
    }

    private ConfigProvider() {}
}
