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

import java.util.Locale;

/**
 * Configuration key for {@link ApplicationConnection}.
 */
public class ConfigurationKey<T> {
    private final String name;
    private final String lowerCaseName;
    private final Class<T> type;
    private final String description;

    /**
     * Constructs an {@link ConfigurationKey} for given name and type.
     * @param name name of a key.
     * @param type type of a key.
     * @param <T> type of a key.
     * @return {@link ConfigurationKey} with given name and type.
     */
    public static <T> ConfigurationKey<T> create(String name, Class<T> type) {
        return new ConfigurationKey<>(name, type, "");
    }

    /**
     * Constructs an {@link ConfigurationKey} for given name, type, and description.
     * @param name name of a key.
     * @param type type of a key.
     * @param description description of a key.
     * @param <T> type of a key.
     * @return {@link ConfigurationKey} with given name and type.
     */
    public static <T> ConfigurationKey<T> create(String name, Class<T> type, String description) {
        return new ConfigurationKey<>(name, type, description);
    }

    protected ConfigurationKey(String name, Class<T> type, String description) {
        if (name == null || type == null || description == null)
            throw new NullPointerException();
        this.name = name;
        this.lowerCaseName = name.toLowerCase(Locale.US);
        this.type = type;
        this.description = description;
    }

    /**
     * Returns the name of a key.
     * @return the name of a key.
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the type of a key.
     * @return the type of a key.
     */
    public Class<T> getType() {
        return type;
    }

    /**
     * Returns the description of a key.
     * @return the description of a key.
     */
    public String getDescription() {
        return description;
    }

    public boolean equals(Object o) {
        return o instanceof ConfigurationKey && lowerCaseName.equals(((ConfigurationKey<?>) o).lowerCaseName);
    }

    public int hashCode() {
        return lowerCaseName.hashCode();
    }

    public String toString() {
        return name + ":" + type.getName();
    }
}
