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

import com.devexperts.qd.QDContract;
import com.devexperts.qd.QDFilter;
import com.devexperts.qd.config.Required;
import com.devexperts.util.TimePeriod;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings({"unused", "WeakerAccess"})
public class ChannelConfig {

    private static final Set<QDContract> ALL_CONTRACTS = Collections.unmodifiableSet(EnumSet.allOf(QDContract.class));

    public static ChannelConfig valueOf(String base) {
        ChannelConfig product = new ChannelConfig();
        product.setBase(base);
        return product;
    }

    @Required
    private String base;
    private String filter = QDFilter.ANYTHING.toString();
    private Set<QDContract> contracts = ALL_CONTRACTS;
    private TimePeriod aggregationPeriod = TimePeriod.ZERO;
    private boolean allowWildcards = false;

    public ChannelConfig() {
    }

    public ChannelConfig(String base) {
        this.base = base;
    }

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

    public Set<QDContract> getContracts() {
        return contracts;
    }

    public void setContracts(Set<QDContract> contracts) {
        this.contracts = EnumSet.noneOf(QDContract.class);
        this.contracts.addAll(contracts);
    }

    public TimePeriod getAggregationPeriod() {
        return aggregationPeriod;
    }

    public void setAggregationPeriod(TimePeriod aggregationPeriod) {
        this.aggregationPeriod = aggregationPeriod;
    }

    public boolean isAllowWildcards() {
        return allowWildcards;
    }

    public void setAllowWildcards(boolean allowWildcards) {
        this.allowWildcards = allowWildcards;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof ChannelConfig))
            return false;
        ChannelConfig that = (ChannelConfig) o;
        return allowWildcards == that.allowWildcards &&
            Objects.equals(base, that.base) &&
            Objects.equals(filter, that.filter) &&
            Objects.equals(contracts, that.contracts) &&
            Objects.equals(aggregationPeriod, that.aggregationPeriod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(base, filter, contracts, aggregationPeriod, allowWildcards);
    }

    @Override
    public String toString() {
        return "Channel{base=" + base +
            ", filter=" + filter +
            ", contracts=" + (contracts.equals(ALL_CONTRACTS) ? "[*]" : contracts) +
            ", ap=" + aggregationPeriod.getTime() +
            ", awc=" + allowWildcards +
            "}";
    }

    // builder methods

    public ChannelConfig withBase(String base) {
        setBase(base);
        return this;
    }

    public ChannelConfig withFilter(String filter) {
        setFilter(filter);
        return this;
    }

    public ChannelConfig withContracts(Set<QDContract> contracts) {
        setContracts(contracts);
        return this;
    }

    public ChannelConfig withAggregationPeriod(TimePeriod aggregationPeriod) {
        setAggregationPeriod(aggregationPeriod);
        return this;
    }

    public ChannelConfig withAllowWildcards(boolean allowWildcards) {
        setAllowWildcards(allowWildcards);
        return this;
    }
}
