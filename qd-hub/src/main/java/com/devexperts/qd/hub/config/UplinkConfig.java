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
package com.devexperts.qd.hub.config;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;

public class UplinkConfig extends LinkConfig {

    private List<String> spaces = Collections.emptyList();

    public UplinkConfig() {
    }

    public UplinkConfig(String name, String address) {
        super(name, address);
    }

    @Nonnull
    public List<String> getSpaces() {
        return spaces;
    }

    public void setSpaces(@Nonnull List<String> spaces) {
        this.spaces = Objects.requireNonNull(spaces);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof UplinkConfig))
            return false;
        if (!super.equals(o))
            return false;
        UplinkConfig that = (UplinkConfig) o;
        return Objects.equals(spaces, that.spaces);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), spaces);
    }

    @Override
    protected StringBuilder fieldsToString(StringBuilder sb) {
        return super.fieldsToString(sb)
            .append(", spaces=").append(spaces);
    }

    // builder methods

    @Override
    public UplinkConfig withName(String name) {
        return (UplinkConfig) super.withName(name);
    }

    @Override
    public UplinkConfig withAddress(String address) {
        return (UplinkConfig) super.withAddress(address);
    }

    @Override
    public UplinkConfig withFilter(String filter) {
        return (UplinkConfig) super.withFilter(filter);
    }

    public UplinkConfig withSpaces(@Nonnull List<String> spaces) {
        setSpaces(spaces);
        return this;
    }

    public UplinkConfig withSpace(String space) {
        spaces = ConfigHelper.ensureArrayList(spaces);
        spaces.add(space);
        return this;
    }
}
