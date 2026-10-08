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

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings({"unused", "WeakerAccess"})
public class SpaceConfig {

    private static final Set<QDContract> ALL_CONTRACTS = Collections.unmodifiableSet(EnumSet.allOf(QDContract.class));

    private String name;
    private String filter = QDFilter.ANYTHING.toString();
    private Set<QDContract> contracts = ALL_CONTRACTS;
    private boolean active = true;
    private boolean enableWildcards = true;
    private boolean eventTimeSequence = false;
    private boolean storeEverything = false;
    private String storeEverythingFilter = QDFilter.ANYTHING.toString();

    public SpaceConfig() {
    }

    public SpaceConfig(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isEnableWildcards() {
        return enableWildcards;
    }

    public void setEnableWildcards(boolean enableWildcards) {
        this.enableWildcards = enableWildcards;
    }

    public boolean isEventTimeSequence() {
        return eventTimeSequence;
    }

    public void setEventTimeSequence(boolean eventTimeSequence) {
        this.eventTimeSequence = eventTimeSequence;
    }

    public boolean isStoreEverything() {
        return storeEverything;
    }

    public void setStoreEverything(boolean storeEverything) {
        this.storeEverything = storeEverything;
    }

    public String getStoreEverythingFilter() {
        return storeEverythingFilter;
    }

    public void setStoreEverythingFilter(String storeEverythingFilter) {
        this.storeEverythingFilter = storeEverythingFilter;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof SpaceConfig))
            return false;
        SpaceConfig that = (SpaceConfig) o;
        return active == that.active &&
            enableWildcards == that.enableWildcards &&
            eventTimeSequence == that.eventTimeSequence &&
            storeEverything == that.storeEverything &&
            Objects.equals(name, that.name) &&
            Objects.equals(filter, that.filter) &&
            Objects.equals(contracts, that.contracts) &&
            Objects.equals(storeEverythingFilter, that.storeEverythingFilter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, filter, contracts, active, enableWildcards, eventTimeSequence, storeEverything, storeEverythingFilter);
    }

    @Override
    public String toString() {
        return "Space{name=" + name +
            ", filter=" + filter +
            ", contracts=" + (contracts.equals(ALL_CONTRACTS) ? "[*]" : contracts) +
            ", active=" + active +
            ", ewc=" + enableWildcards +
            ", ets=" + eventTimeSequence +
            ", se=" + storeEverything +
            ", sef=" + storeEverythingFilter +
            "}";
    }

    // builder methods

    public SpaceConfig withName(String name) {
        setName(name);
        return this;
    }

    public SpaceConfig withFilter(String filter) {
        setFilter(filter);
        return this;
    }

    public SpaceConfig withContracts(Set<QDContract> contracts) {
        setContracts(contracts);
        return this;
    }

    public SpaceConfig withActive(boolean active) {
        setActive(active);
        return this;
    }

    public SpaceConfig withEnableWildcards(boolean enableWildcards) {
        setEnableWildcards(enableWildcards);
        return this;
    }

    public SpaceConfig withEventTimeSequence(boolean eventTimeSequence) {
        setEventTimeSequence(eventTimeSequence);
        return this;
    }

    public SpaceConfig withStoreEverything(boolean storeEverything) {
        setStoreEverything(storeEverything);
        return this;
    }

    public SpaceConfig withStoreEverythingFilter(String storeEverythingFilter) {
        setStoreEverythingFilter(storeEverythingFilter);
        return this;
    }
}
