/*
 * !++
 * QDS - Quick Data Signalling Library
 * !-
 * Copyright (C) 2002 - 2021 Devexperts LLC
 * !-
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at
 * http://mozilla.org/MPL/2.0/.
 * !__
 */
package com.devexperts.connector.proto;

import com.devexperts.util.InvalidFormatException;

/**
 * Thrown to indicate that configuration value for
 * {@link ConfigurationKey key} is invalid.
 */
public class ConfigurationException extends InvalidFormatException {
    private static final long serialVersionUID = 0;

    private final ConfigurationKey<?> key;

    public ConfigurationException(ConfigurationKey<?> key, String message) {
        super(message);
        if (key == null)
            throw new NullPointerException();
        this.key = key;
    }

    public ConfigurationException(ConfigurationKey<?> key, String message, Throwable cause) {
        super(message, cause);
        if (key == null)
            throw new NullPointerException();
        this.key = key;
    }

    public ConfigurationException(ConfigurationKey<?> key, Throwable cause) {
        this(key, cause.getMessage(), cause);
    }

    public ConfigurationKey<?> getKey() {
        return key;
    }
}
