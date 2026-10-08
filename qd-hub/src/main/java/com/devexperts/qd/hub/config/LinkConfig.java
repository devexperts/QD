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

import com.devexperts.qd.QDFilter;
import com.devexperts.qd.config.Required;

import java.util.Objects;

@SuppressWarnings("unused")
// FIXME: @Named - means resolving get/setName with key in maps
public abstract class LinkConfig {

    private String name;

    @Required
    private String address;
    private String filter = QDFilter.ANYTHING.toString();

    public LinkConfig() {
    }

    public LinkConfig(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof LinkConfig))
            return false;
        LinkConfig that = (LinkConfig) o;
        return Objects.equals(name, that.name) &&
            Objects.equals(address, that.address) &&
            Objects.equals(filter, that.filter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, address, filter);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append('{');
        fieldsToString(sb);
        sb.append('}');
        return sb.toString();
    }

    protected StringBuilder fieldsToString(StringBuilder sb) {
        sb.append("name=").append(name);
        sb.append(", address=").append(address);
        sb.append(", filter=").append(filter);
        return sb;
    }

    // builder methods

    public LinkConfig withName(String name) {
        setName(name);
        return this;
    }

    public LinkConfig withAddress(String address) {
        setAddress(address);
        return this;
    }

    public LinkConfig withFilter(String filter) {
        setFilter(filter);
        return this;
    }
}
