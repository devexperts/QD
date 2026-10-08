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

@SuppressWarnings({"unused"})
public class DownlinkConfig extends LinkConfig {

    private List<String> spaces = Collections.emptyList();
    private List<ChannelConfig> products = Collections.emptyList();

    public DownlinkConfig() {
    }

    public DownlinkConfig(String name, String address) {
        super(name, address);
    }

    @Nonnull
    public List<String> getSpaces() {
        return spaces;
    }

    public void setSpaces(@Nonnull List<String> spaces) {
        this.spaces = Objects.requireNonNull(spaces);
    }

    @Nonnull
    public List<ChannelConfig> getProducts() {
        return products;
    }

    public void setProducts(@Nonnull List<ChannelConfig> products) {
        this.products = Objects.requireNonNull(products);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof DownlinkConfig))
            return false;
        if (!super.equals(o))
            return false;
        DownlinkConfig that = (DownlinkConfig) o;
        return Objects.equals(spaces, that.spaces) &&
            Objects.equals(products, that.products);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), spaces, products);
    }

    @Override
    protected StringBuilder fieldsToString(StringBuilder sb) {
        return super.fieldsToString(sb)
            .append(", spaces=").append(spaces)
            .append(", products=").append(products);
    }

    // builder methods

    @Override
    public DownlinkConfig withName(String name) {
        return (DownlinkConfig) super.withName(name);
    }

    @Override
    public DownlinkConfig withAddress(String address) {
        return (DownlinkConfig) super.withAddress(address);
    }

    @Override
    public DownlinkConfig withFilter(String filter) {
        return (DownlinkConfig) super.withFilter(filter);
    }

    public DownlinkConfig withSpaces(@Nonnull List<String> spaces) {
        setSpaces(spaces);
        return this;
    }

    public DownlinkConfig withSpace(String space) {
        spaces = ConfigHelper.ensureArrayList(spaces);
        spaces.add(space);
        return this;
    }

    public DownlinkConfig withProducts(@Nonnull List<ChannelConfig> products) {
        setProducts(products);
        return this;
    }

    public DownlinkConfig withProduct(ChannelConfig product) {
        products = ConfigHelper.ensureArrayList(products);
        products.add(product);
        return this;
    }
}
